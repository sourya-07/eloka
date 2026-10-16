package in.eko.eloka.onboarding.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.StaffPermission;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.verification.VerificationApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Staff review use cases (P8, S3–S13). Every action takes the application row lock, checks the
 * caller's permission and the case state, records a {@link ReviewAction} and an audit entry.
 * Maker-checker: the approver may be neither the reviewer who holds the case nor the staff
 * member who recommended approval.
 */
@Service
class ReviewService {

	static final Duration DEFAULT_CORRECTION_WINDOW = Duration.ofDays(15);

	/** correction_request.message and onboarding_step.reviewer_message (V27). */
	static final int MAX_MESSAGE = 1000;

	private final OnboardingApplicationRepository applications;
	private final OnboardingStepRepository steps;
	private final ApplicationDocumentRepository documents;
	private final BusinessProfileRepository profiles;
	private final ApplicantRepository applicants;
	private final ReviewActionRepository reviewActions;
	private final CorrectionRequestRepository corrections;
	private final ApplicationStateMachine stateMachine;
	private final VerificationApi verification;
	private final FieldCipher cipher;
	private final AuditLog audit;
	private final JsonMapper json;
	private final Clock clock;

	ReviewService(OnboardingApplicationRepository applications, OnboardingStepRepository steps,
			ApplicationDocumentRepository documents, BusinessProfileRepository profiles, ApplicantRepository applicants,
			ReviewActionRepository reviewActions, CorrectionRequestRepository corrections,
			ApplicationStateMachine stateMachine, VerificationApi verification, FieldCipher cipher, AuditLog audit,
			JsonMapper json, Clock clock) {
		this.applications = applications;
		this.steps = steps;
		this.documents = documents;
		this.profiles = profiles;
		this.applicants = applicants;
		this.reviewActions = reviewActions;
		this.corrections = corrections;
		this.stateMachine = stateMachine;
		this.verification = verification;
		this.cipher = cipher;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/** S3 / T4: take a submitted case. */
	@Transactional
	void claim(Caller staff, String publicId) {
		require(staff, StaffPermission.ONBOARDING_REVIEW);
		OnboardingApplication application = lock(publicId);
		stateMachine.apply(application, LifecycleEvent.CLAIM, "STAFF", staff.userId().toString());
		application.assignTo(staff.userId());
		record(application, staff, ReviewAction.CLAIM, null, null, null, null);
	}

	/**
	 * S4: give the case back to the queue. The reviewer releases their own case; someone with
	 * {@code onboarding.reassign} may release anyone's (a reviewer who left), with a reason.
	 */
	@Transactional
	void release(Caller staff, String publicId, String reason) {
		require(staff, StaffPermission.ONBOARDING_READ);
		OnboardingApplication application = lock(publicId);
		requireUnderReview(application);
		UUID reviewer = application.getAssignedStaffId();
		boolean own = application.isAssignedTo(staff.userId());
		if (own) {
			require(staff, StaffPermission.ONBOARDING_REVIEW);
		}
		else {
			require(staff, StaffPermission.ONBOARDING_REASSIGN);
			if (reason == null || reason.isBlank()) {
				throw ValidationException.field("reason", "Say why you take the case from its reviewer");
			}
		}
		stateMachine.apply(application, LifecycleEvent.RELEASE, "STAFF", staff.userId().toString());
		application.assignTo(null);
		record(application, staff, ReviewAction.RELEASE, own ? null : "FORCED", null, reason,
				own || reviewer == null ? null : reviewer.toString());
	}

	/** S5 / S6 */
	@Transactional
	void decideDocument(Caller staff, String publicId, String documentId, boolean verified, String reasonCode,
			String message) {
		OnboardingApplication application = assignedCase(staff, publicId);
		ApplicationDocument document = documents.findByPublicIdAndApplicationId(documentId, application.getId())
			.orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND", "Document not found"));
		Instant now = Instant.now(clock);
		if (verified) {
			document.verify(staff.userId(), now);
		}
		else {
			if (reasonCode == null || reasonCode.isBlank()) {
				throw ValidationException.field("reason_code", "Say why the document is rejected");
			}
			document.reject(reasonCode, staff.userId(), now);
		}
		record(application, staff, ReviewAction.DOCUMENT, verified ? "VERIFIED" : "REJECTED", reasonCode, message, documentId);
	}

	/** S7: accept or reject a verification that needs a human (name mismatch). */
	@Transactional
	VerificationApi.AttemptView decideVerification(Caller staff, String publicId, UUID verificationId, boolean accept,
			String reasonCode) {
		OnboardingApplication application = assignedCase(staff, publicId);
		VerificationApi.AttemptView decided = verification.decide(application.getId(), verificationId, accept, staff.userId());
		record(application, staff, ReviewAction.VERIFICATION, accept ? "ACCEPT" : "REJECT", reasonCode, null,
				verificationId.toString());
		return decided;
	}

	/** S8 / T5: reopen steps for the applicant with a reason and a due date. */
	@Transactional
	void requestCorrection(Caller staff, String publicId, List<String> stepKeys, String reasonCode, String message,
			Integer dueInDays) {
		OnboardingApplication application = assignedCase(staff, publicId);
		if (stepKeys == null || stepKeys.isEmpty()) {
			throw ValidationException.field("step_keys", "Choose at least one step to correct");
		}
		if (reasonCode == null || reasonCode.isBlank() || message == null || message.isBlank()) {
			throw ValidationException.field("message", "Tell the applicant what to fix");
		}
		if (message.length() > MAX_MESSAGE) {
			throw ValidationException.field("message", "Keep the message under " + MAX_MESSAGE + " characters");
		}
		List<StepKey> keys = new ArrayList<>();
		for (String value : stepKeys) {
			StepKey key;
			try {
				key = StepKey.valueOf(value);
			}
			catch (IllegalArgumentException ex) {
				throw ValidationException.field("step_keys", "Unknown step " + value);
			}
			if (key == StepKey.MOBILE_VERIFICATION) {
				throw ValidationException.field("step_keys", "The verified mobile cannot be reopened");
			}
			if (!keys.contains(key)) {
				keys.add(key);
			}
		}
		Instant now = Instant.now(clock);
		Instant due = now.plus(dueInDays == null ? DEFAULT_CORRECTION_WINDOW : Duration.ofDays(Math.max(1, dueInDays)));
		for (OnboardingStep step : steps.findByApplicationId(application.getId())) {
			if (keys.contains(step.getStepKey())) {
				step.reopen(reasonCode, message);
			}
		}
		stateMachine.apply(application, LifecycleEvent.REQUEST_CORRECTION, "STAFF", staff.userId().toString());
		application.correctionDue(due);
		List<String> keyNames = keys.stream().map(Enum::name).toList();
		corrections.save(new CorrectionRequest(application.getId(), staff.userId(), json.writeValueAsString(keyNames),
				reasonCode, message, due));
		record(application, staff, ReviewAction.CORRECTION, null, reasonCode, message, String.join(",", keyNames));
	}

	/** S9: the maker's recommendation. */
	@Transactional
	void recommend(Caller staff, String publicId, String recommendation, String note) {
		OnboardingApplication application = assignedCase(staff, publicId);
		if (!"APPROVE".equals(recommendation) && !"REJECT".equals(recommendation)) {
			throw ValidationException.field("recommendation", "APPROVE or REJECT");
		}
		record(application, staff, ReviewAction.RECOMMENDATION, recommendation, null, note, null);
	}

	/** S10 / T7: the checker approves. */
	@Transactional
	void approve(Caller staff, String publicId, String note) {
		require(staff, StaffPermission.ONBOARDING_APPROVE);
		OnboardingApplication application = lock(publicId);
		List<ReviewAction> history = reviewActions.findByApplicationIdOrderByOccurredAtAsc(application.getId());
		Optional<ReviewAction> recommendation = latestRecommendation(history);
		// Nobody approves a case they claimed or recommended at any point, even after handing it over
		boolean reviewedIt = history.stream()
			.anyMatch(a -> a.getStaffId().equals(staff.userId()) && (ReviewAction.CLAIM.equals(a.getAction())
					|| ReviewAction.RECOMMENDATION.equals(a.getAction())));
		if (application.isAssignedTo(staff.userId()) || reviewedIt) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "MAKER_CHECKER_VIOLATION",
					"A different staff member must approve a case you reviewed or recommended");
		}
		List<String> blocked = approvalBlockers(application, recommendation);
		if (!blocked.isEmpty()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "APPROVAL_BLOCKED", "This case cannot be approved yet",
					Map.of("reasons", blocked));
		}
		stateMachine.apply(application, LifecycleEvent.APPROVE, "STAFF", staff.userId().toString());
		record(application, staff, ReviewAction.APPROVE, "APPROVE", null, note, null);
	}

	/** S11 / T8 */
	@Transactional
	void reject(Caller staff, String publicId, String reasonCode, String applicantMessage, String internalNote) {
		require(staff, StaffPermission.ONBOARDING_REJECT);
		if (reasonCode == null || reasonCode.isBlank()) {
			throw ValidationException.field("reason_code", "A reason code is required");
		}
		OnboardingApplication application = lock(publicId);
		stateMachine.apply(application, LifecycleEvent.REJECT, "STAFF", staff.userId().toString());
		record(application, staff, ReviewAction.REJECT, "REJECT", reasonCode,
				applicantMessage == null ? internalNote : applicantMessage, null);
	}

	/** S12 / T9 */
	@Transactional
	void hold(Caller staff, String publicId, String reasonCode, boolean on) {
		require(staff, StaffPermission.ONBOARDING_HOLD);
		OnboardingApplication application = lock(publicId);
		stateMachine.apply(application, on ? LifecycleEvent.HOLD : LifecycleEvent.RELEASE_HOLD, "STAFF",
				staff.userId().toString());
		record(application, staff, on ? ReviewAction.HOLD : ReviewAction.RELEASE_HOLD, null, reasonCode, null, null);
	}

	/** S13: one value in full, for one response. Audited with the purpose, never the value. */
	@Transactional
	String revealPii(Caller staff, String publicId, String field, String purpose) {
		require(staff, StaffPermission.PII_REVEAL);
		if (purpose == null || purpose.isBlank()) {
			throw ValidationException.field("purpose", "Say why you need the full value");
		}
		OnboardingApplication application = applications.findByPublicId(publicId)
			.orElseThrow(ReviewService::notFound);
		BusinessProfile profile = profiles.findByApplicationId(application.getId()).orElse(null);
		String value = switch (field == null ? "" : field) {
			case "PAN" -> profile == null || profile.getPanEnc() == null ? null : cipher.decrypt(profile.getPanEnc());
			case "EMAIL" -> profile == null || profile.getEmailEnc() == null ? null : cipher.decrypt(profile.getEmailEnc());
			case "MOBILE" -> applicants.findById(application.getApplicantId())
				.map(applicant -> cipher.decrypt(applicant.getMobileEnc()))
				.orElse(null);
			case "ACCOUNT" -> currentBankAccountId(application.getId())
				.map(id -> verification.revealAccountNumber(application.getId(), id))
				.orElse(null);
			default -> throw ValidationException.field("field", "PAN, ACCOUNT, MOBILE or EMAIL");
		};
		if (value == null) {
			throw new NotFoundException("VALUE_NOT_AVAILABLE", "The applicant has not provided this yet");
		}
		// The purpose is free text (it may hold personal data), so it stays in the review history;
		// the audit log keeps ids and codes only and points at that history entry
		ReviewAction reveal = reviewActions.save(new ReviewAction(application.getId(), staff.userId(),
				ReviewAction.PII_REVEAL, field, null, truncate(purpose, 1000), null, Instant.now(clock)));
		audit.record(new AuditEntry("PII_REVEALED", "STAFF", staff.userId().toString(), IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", publicId, Map.of("field", field, "review_action", reveal.getId().toString())));
		return value;
	}

	/** Why the checker cannot approve yet (empty = approvable). */
	List<String> approvalBlockers(OnboardingApplication application, Optional<ReviewAction> recommendation) {
		List<String> reasons = new ArrayList<>();
		if (recommendation.isEmpty() || !"APPROVE".equals(recommendation.get().getDecision())) {
			reasons.add("NO_APPROVE_RECOMMENDATION");
		}
		List<ApplicationDocument> current = documents.findByApplicationIdOrderByCreatedAtAsc(application.getId())
			.stream()
			.filter(ApplicationDocument::isCurrent)
			.toList();
		if (current.stream().anyMatch(d -> !"VERIFIED".equals(d.getStatus()))) {
			reasons.add("DOCUMENTS_NOT_VERIFIED");
		}
		Map<String, VerificationApi.AttemptView> latestByKind = new LinkedHashMap<>();
		verification.attempts(application.getId())
			.stream()
			.filter(a -> !"FAILED".equals(a.status()) || "REJECTED_BY_REVIEWER".equals(a.failureCode()))
			.forEach(a -> latestByKind.putIfAbsent(a.kind(), a));
		latestByKind.values().forEach(a -> {
			if ("MANUAL_REVIEW".equals(a.status())) {
				reasons.add(a.kind() + "_NEEDS_DECISION");
			}
			if ("REJECTED_BY_REVIEWER".equals(a.failureCode())) {
				reasons.add(a.kind() + "_REJECTED");
			}
		});
		return reasons;
	}

	/** The latest recommendation since the application was last (re)submitted. */
	Optional<ReviewAction> latestRecommendation(List<ReviewAction> history) {
		ReviewAction latest = null;
		for (ReviewAction action : history) {
			if (ReviewAction.RECOMMENDATION.equals(action.getAction())) {
				latest = action;
			}
			if (ReviewAction.CORRECTION.equals(action.getAction())) {
				latest = null; // a correction invalidates earlier recommendations
			}
		}
		return Optional.ofNullable(latest);
	}

	Optional<String> currentBankAccountId(UUID applicationId) {
		return steps.findByApplicationIdAndStepKey(applicationId, StepKey.BANK_ACCOUNT)
			.map(OnboardingStep::getSummary)
			.filter(summary -> summary != null)
			.map(summary -> json.readTree(summary).path("bank_account_id").asString(null));
	}

	private OnboardingApplication assignedCase(Caller staff, String publicId) {
		require(staff, StaffPermission.ONBOARDING_REVIEW);
		OnboardingApplication application = lock(publicId);
		requireUnderReview(application);
		if (!application.isAssignedTo(staff.userId())) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "NOT_ASSIGNED", "This case is assigned to another reviewer");
		}
		return application;
	}

	private static void requireUnderReview(OnboardingApplication application) {
		if (application.getStatus() != ApplicationStatus.UNDER_REVIEW) {
			throw new ApiProblem(HttpStatus.CONFLICT, "INVALID_STATE_TRANSITION",
					"Claim the case first; it is " + application.getStatus().name().toLowerCase().replace('_', ' '),
					Map.of("current_status", application.getStatus().name()));
		}
	}

	private OnboardingApplication lock(String publicId) {
		OnboardingApplication application = applications.findByPublicId(publicId).orElseThrow(ReviewService::notFound);
		return applications.lockById(application.getId()).orElseThrow(ReviewService::notFound);
	}

	private void record(OnboardingApplication application, Caller staff, String action, String decision, String reasonCode,
			String note, String targetRef) {
		reviewActions.save(new ReviewAction(application.getId(), staff.userId(), action, decision, reasonCode,
				truncate(note, 1000), targetRef, Instant.now(clock)));
		Map<String, String> details = new LinkedHashMap<>();
		if (decision != null) {
			details.put("decision", decision);
		}
		if (reasonCode != null) {
			details.put("reason_code", reasonCode);
		}
		if (targetRef != null) {
			details.put("target", truncate(targetRef, 120));
		}
		audit.record(new AuditEntry("REVIEW_" + action, "STAFF", staff.userId().toString(), IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", application.getPublicId(), details));
	}

	static void require(Caller staff, String permission) {
		if (!staff.hasPermission(permission)) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do this",
					Map.of("missing_permission", permission));
		}
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	private static NotFoundException notFound() {
		return new NotFoundException("ONBOARDING_NOT_FOUND", "Application not found");
	}

	/** Statuses staff list by default (the review queue and cases in flight). */
	static final EnumSet<ApplicationStatus> QUEUE = EnumSet.of(ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW,
			ApplicationStatus.ON_HOLD, ApplicationStatus.CORRECTION_REQUIRED);
}
