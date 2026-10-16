package in.eko.eloka.onboarding.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.onboarding.OnboardingProperties;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.onboarding.StepStatus;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Masking;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.persistence.PublicIds;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applicant use cases on an application: start or resume, read, draft, submit a step, skip,
 * submit the application, withdraw, expire. Every write goes through {@link #write}, which takes
 * the row lock, checks the {@code If-Match} version and that the application is editable.
 */
@Service
class ApplicationService {

	private static final Set<ApplicationStatus> NOT_RESUMABLE = EnumSet.of(ApplicationStatus.REJECTED,
			ApplicationStatus.WITHDRAWN, ApplicationStatus.EXPIRED, ApplicationStatus.ACTIVE);

	/** Outcome of an applicant login: the application to continue and whether it already existed. */
	record Start(UUID applicantId, ApplicationSnapshot snapshot, boolean resumed) {
	}

	private final ApplicantRepository applicants;
	private final OnboardingApplicationRepository applications;
	private final OnboardingStepRepository steps;
	private final BusinessProfileRepository profiles;
	private final TimelineEntryRepository timeline;
	private final CorrectionRequestRepository corrections;
	private final ApplicationStateMachine stateMachine;
	private final Map<StepKey, StepHandler> handlers = new EnumMap<>(StepKey.class);
	private final OnboardingProperties properties;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final AuditLog audit;
	private final JsonMapper json;
	private final Clock clock;

	ApplicationService(ApplicantRepository applicants, OnboardingApplicationRepository applications,
			OnboardingStepRepository steps, BusinessProfileRepository profiles, TimelineEntryRepository timeline,
			CorrectionRequestRepository corrections, ApplicationStateMachine stateMachine, List<StepHandler> stepHandlers, OnboardingProperties properties,
			FieldCipher cipher, BlindIndex index, AuditLog audit, JsonMapper json, Clock clock) {
		this.applicants = applicants;
		this.applications = applications;
		this.steps = steps;
		this.profiles = profiles;
		this.timeline = timeline;
		this.corrections = corrections;
		this.stateMachine = stateMachine;
		stepHandlers.forEach(handler -> handlers.put(handler.key(), handler));
		this.properties = properties;
		this.cipher = cipher;
		this.index = index;
		this.audit = audit;
		this.json = json;
		this.clock = clock;
	}

	/**
	 * T1: after a verified OTP, continue the applicant's live application or start one. A mobile
	 * whose application already became an active tenant gets {@code 409 DUPLICATE_APPLICATION}.
	 */
	@Transactional
	Start startOrResume(String verifiedMobile) {
		String mobile = Mobiles.normalize(verifiedMobile);
		byte[] mobileHash = index.hash("mobile", mobile);
		Applicant applicant = applicants.findByMobileHash(mobileHash)
			.orElseGet(() -> applicants.save(new Applicant(cipher.encrypt(mobile), mobileHash, Masking.mobile(mobile))));
		if (applications.existsByApplicantIdAndStatus(applicant.getId(), ApplicationStatus.ACTIVE)) {
			audit.record(new AuditEntry("DUPLICATE_LOCAL_ADMIN_ATTEMPT", "APPLICANT", null, IdentityApi.PLATFORM_ORG_ID,
					"APPLICANT", applicant.getId().toString(), Map.of()));
			throw new ApiProblem(HttpStatus.CONFLICT, "DUPLICATE_APPLICATION", "This mobile number is already registered");
		}
		Optional<OnboardingApplication> live = applications.findByApplicantIdAndStatusNotIn(applicant.getId(), NOT_RESUMABLE)
			.stream()
			.findFirst();
		if (live.isPresent()) {
			return new Start(applicant.getId(), snapshot(live.get()), true);
		}
		Instant now = Instant.now(clock);
		OnboardingApplication application = applications
			.saveAndFlush(new OnboardingApplication(PublicIds.next("onb", now), applicant.getId(), now, properties.idleExpiry()));
		for (StepKey key : StepKey.values()) {
			OnboardingStep step = new OnboardingStep(application.getId(), key, properties.isRequired(key),
					properties.maxAttempts());
			if (key == StepKey.MOBILE_VERIFICATION) {
				step.complete(json.writeValueAsString(Map.of("mobile_masked", applicant.getMobileMasked())), now);
			}
			steps.save(step);
		}
		timeline.save(new TimelineEntry(application.getId(), "ONBOARDING_STARTED", null, null,
				ApplicationStatus.IN_PROGRESS, "APPLICANT", now));
		audit.record(new AuditEntry("ONBOARDING_STARTED", "APPLICANT", null, IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", application.getPublicId(), Map.of()));
		return new Start(applicant.getId(), snapshot(application), false);
	}

	@Transactional(readOnly = true)
	Optional<ApplicationSnapshot> current(UUID applicantId) {
		return applications.findByApplicantIdAndStatusNotIn(applicantId, NOT_RESUMABLE)
			.stream()
			.findFirst()
			.map(this::snapshot);
	}

	@Transactional(readOnly = true)
	ApplicationSnapshot get(UUID applicantId, String publicId) {
		return snapshot(find(applicantId, publicId));
	}

	@Transactional(readOnly = true)
	List<TimelineEntry> timeline(UUID applicantId, String publicId) {
		return timeline.findByApplicationIdOrderByOccurredAtAsc(find(applicantId, publicId).getId());
	}

	/**
	 * Runs one applicant write under the application row lock: version check ({@code 412} with
	 * the current snapshot), editable check, then {@code action}, then activity tracking.
	 *
	 * @param expectedVersion from {@code If-Match}; null only for calls the spec runs without it
	 *        (document uploads, which may go in parallel)
	 */
	@Transactional
	<T> T write(UUID applicantId, String publicId, Long expectedVersion, UUID sessionId, Function<StepContext, T> action) {
		OnboardingApplication application = applications.lockByPublicIdAndApplicantId(publicId, applicantId)
			.orElseThrow(ApplicationService::notFound);
		if (expectedVersion != null && application.getVersion() != expectedVersion) {
			throw new ApiProblem(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED",
					"This application changed in another tab or device. Here is the latest version.",
					Map.of("current", snapshot(application)));
		}
		if (!application.getStatus().isEditableByApplicant()) {
			throw new ApiProblem(HttpStatus.CONFLICT, "APPLICATION_NOT_EDITABLE",
					"The application can no longer be changed", Map.of("current_status", application.getStatus().name()));
		}
		Instant now = Instant.now(clock);
		StepContext context = new StepContext(application, steps.findByApplicationId(application.getId()), profiles,
				timeline, now, sessionId);
		T result = action.apply(context);
		application.recordActivity(now, properties.idleExpiry());
		applications.saveAndFlush(application);
		return result;
	}

	/** A7: saves unverified data for a step. Only the step's allowed draft fields are accepted. */
	@Transactional
	ApplicationSnapshot saveDraft(UUID applicantId, String publicId, long expectedVersion, StepKey key, JsonNode draft) {
		write(applicantId, publicId, expectedVersion, null, context -> {
			if (!(draft instanceof ObjectNode object)) {
				throw ValidationException.field("draft", "draft must be an object");
			}
			Set<String> allowed = draftFields(key);
			List<String> rejected = new ArrayList<>();
			for (Iterator<String> names = object.propertyNames().iterator(); names.hasNext();) {
				String name = names.next();
				if (!allowed.contains(name)) {
					rejected.add(name);
				}
			}
			if (!rejected.isEmpty()) {
				Map<String, String> invalid = new java.util.LinkedHashMap<>();
				rejected.forEach(name -> invalid.put(name, "Cannot be saved as a draft for this step"));
				throw new ValidationException("Some fields cannot be saved as a draft", invalid);
			}
			context.requirePrerequisites(key);
			context.step(key).saveDraft(json.writeValueAsString(object));
			return null;
		});
		return get(applicantId, publicId);
	}

	/** A8: validates and completes a data step through its {@link StepHandler}. */
	@Transactional
	ApplicationSnapshot submitStep(UUID applicantId, String publicId, long expectedVersion, StepKey key, JsonNode data) {
		StepHandler handler = handlers.get(key);
		if (handler == null) {
			throw new ApiProblem(HttpStatus.CONFLICT, "STEP_HAS_OWN_RESOURCE",
					"This step is completed through its own verification call");
		}
		write(applicantId, publicId, expectedVersion, null, context -> {
			context.requirePrerequisites(key);
			handler.submit(context, data == null ? json.createObjectNode() : data);
			return null;
		});
		return get(applicantId, publicId);
	}

	/** A9: skips an optional step. */
	@Transactional
	ApplicationSnapshot skip(UUID applicantId, String publicId, long expectedVersion, StepKey key) {
		write(applicantId, publicId, expectedVersion, null, context -> {
			context.requirePrerequisites(key);
			context.step(key).skip();
			context.record("STEP_SKIPPED", key);
			return null;
		});
		return get(applicantId, publicId);
	}

	/** A10 / T3: submits when every required step is done (the agreement signature completes the last one). */
	@Transactional
	ApplicationSnapshot submit(UUID applicantId, String publicId, long expectedVersion) {
		write(applicantId, publicId, expectedVersion, null, context -> {
			submitIfComplete(context, true);
			return null;
		});
		return get(applicantId, publicId);
	}

	/**
	 * Moves the application to {@code SUBMITTED} when nothing required is missing.
	 *
	 * @param strict when true, missing steps are a {@code 409 PREREQUISITE_INCOMPLETE}; when false
	 *        (auto-submit after signing) nothing happens
	 */
	void submitIfComplete(StepContext context, boolean strict) {
		List<String> missing = context.steps()
			.values()
			.stream()
			.filter(step -> step.isRequired() && !step.getStatus().isSatisfied())
			.map(step -> step.getStepKey().name())
			.toList();
		if (!missing.isEmpty()) {
			if (strict) {
				throw new ApiProblem(HttpStatus.CONFLICT, "PREREQUISITE_INCOMPLETE",
						"Some steps are not complete yet", Map.of("missing_steps", missing));
			}
			return;
		}
		context.steps().values().forEach(OnboardingStep::lock);
		stateMachine.apply(context.application(), LifecycleEvent.SUBMIT, "APPLICANT");
		// T6: a resubmission closes the open correction request
		corrections.findByApplicationIdOrderByCreatedAtDesc(context.applicationId())
			.forEach(request -> request.resolve(context.now()));
		context.application().correctionDue(null);
	}

	/** A11 / T16: withdraws (the web layer checks the step-up token first). */
	@Transactional
	ApplicationSnapshot withdraw(UUID applicantId, String publicId, long expectedVersion, String reasonCode) {
		OnboardingApplication application = applications.lockByPublicIdAndApplicantId(publicId, applicantId)
			.orElseThrow(ApplicationService::notFound);
		if (application.getVersion() != expectedVersion) {
			throw new ApiProblem(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED",
					"This application changed in another tab or device. Here is the latest version.",
					Map.of("current", snapshot(application)));
		}
		stateMachine.apply(application, LifecycleEvent.WITHDRAW, "APPLICANT");
		audit.record(new AuditEntry("ONBOARDING_WITHDRAW_REASON", "APPLICANT", null, IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", publicId, Map.of("reason_code", reasonCode == null ? "NONE" : reasonCode)));
		applications.saveAndFlush(application);
		return snapshot(application);
	}

	/** T15: expires one idle application (called by {@link ExpiryJob}, one transaction each). */
	@Transactional
	boolean expireIfIdle(UUID applicationId) {
		OnboardingApplication application = applications.lockById(applicationId).orElse(null);
		Instant now = Instant.now(clock);
		Instant cutoff = now.minus(properties.idleExpiry());
		boolean correctionOverdue = application != null && application.getStatus() == ApplicationStatus.CORRECTION_REQUIRED
				&& application.getCorrectionDueAt() != null && application.getCorrectionDueAt().isBefore(now);
		if (application == null || !(application.isIdleSince(cutoff) || correctionOverdue)
				|| !stateMachine.allows(application, LifecycleEvent.EXPIRE)) {
			return false;
		}
		stateMachine.apply(application, LifecycleEvent.EXPIRE, "SYSTEM");
		return true;
	}

	List<UUID> idleApplicationIds() {
		Instant now = Instant.now(clock);
		List<UUID> ids = new ArrayList<>(applications.findIdleIds(
				EnumSet.of(ApplicationStatus.IN_PROGRESS, ApplicationStatus.CORRECTION_REQUIRED), now.minus(properties.idleExpiry())));
		applications.findOverdueCorrections(now).stream().filter(id -> !ids.contains(id)).forEach(ids::add);
		return ids;
	}

	Set<String> draftFields(StepKey key) {
		StepHandler handler = handlers.get(key);
		if (handler != null) {
			return handler.draftFields();
		}
		return switch (key) {
			// Never drafts: PAN, account numbers, OTPs, Aadhaar
			case BANK_ACCOUNT -> Set.of("bank_code", "bank_id", "ifsc");
			default -> Set.of();
		};
	}

	OnboardingApplication find(UUID applicantId, String publicId) {
		return applications.findByPublicIdAndApplicantId(publicId, applicantId).orElseThrow(ApplicationService::notFound);
	}

	ApplicationSnapshot snapshot(OnboardingApplication application) {
		List<OnboardingStep> rows = new ArrayList<>(steps.findByApplicationId(application.getId()));
		rows.sort(java.util.Comparator.comparing(OnboardingStep::getStepKey));
		Map<StepKey, StepStatus> statusByKey = new EnumMap<>(StepKey.class);
		rows.forEach(step -> statusByKey.put(step.getStepKey(), step.getStatus()));

		String next = rows.stream()
			.filter(step -> step.isRequired() && !step.getStatus().isSatisfied())
			.filter(step -> step.getStepKey().prerequisites().stream().allMatch(p -> statusByKey.get(p).isSatisfied()))
			.map(step -> step.getStepKey().name())
			.findFirst()
			.orElse(null);
		boolean complete = rows.stream().allMatch(step -> !step.isRequired() || step.getStatus().isSatisfied());

		List<String> actions = new ArrayList<>();
		if (application.getStatus().isEditableByApplicant()) {
			actions.add("SAVE_STEP");
			actions.add("SUBMIT_STEP");
			if (complete) {
				actions.add("SUBMIT");
			}
		}
		if (stateMachine.allows(application, LifecycleEvent.WITHDRAW)) {
			actions.add("WITHDRAW");
		}
		var entityType = application.getEntityType();
		ApplicationSnapshot.Correction correction = null;
		if (application.getStatus() == ApplicationStatus.CORRECTION_REQUIRED) {
			correction = corrections.findByApplicationIdOrderByCreatedAtDesc(application.getId())
				.stream()
				.filter(CorrectionRequest::isOpen)
				.findFirst()
				.map(request -> new ApplicationSnapshot.Correction(request.getReasonCode(), request.getMessage(),
						request.getDueAt(), readJson(request.getStepKeys())))
				.orElse(null);
		}
		return new ApplicationSnapshot(application.getPublicId(), application.getStatus().name(), application.getVersion(),
				new ApplicationSnapshot.Program(application.getApplicantCategory(), application.getProductType(),
						entityType == null ? null : entityType.code()),
				next, application.getExpiresAt(), application.getSubmittedAt(),
				rows.stream().map(this::stepView).toList(), actions, correction);
	}

	private ApplicationSnapshot.Step stepView(OnboardingStep step) {
		return new ApplicationSnapshot.Step(step.getStepKey().name(), step.getStatus().name(), step.isRequired(),
				step.getAttemptCount(), step.getMaxAttempts(), step.getLastErrorCode(), readJson(step.getSummary()),
				readJson(step.getDraftData()), step.getReopenReasonCode(), step.getReviewerMessage());
	}

	private JsonNode readJson(String value) {
		return value == null ? null : json.readTree(value);
	}

	private static NotFoundException notFound() {
		return new NotFoundException("ONBOARDING_NOT_FOUND", "Application not found");
	}
}
