package in.eko.eloka.onboarding.internal;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.audit.AuditTrail;
import in.eko.eloka.onboarding.ApplicationStatus;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.verification.VerificationApi;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Staff read side: the queue (S1), one case (S2) and its audit trail (S21). Masked values only. */
@Service
class ReviewQueries {

	private final OnboardingApplicationRepository applications;
	private final ApplicationService applicationService;
	private final ApplicationDocumentRepository documents;
	private final BusinessProfileRepository profiles;
	private final ReviewActionRepository reviewActions;
	private final CorrectionRequestRepository corrections;
	private final ReviewService review;
	private final VerificationApi verification;
	private final AuditTrail auditTrail;
	private final NamedParameterJdbcTemplate jdbc;
	private final BlindIndex index;
	private final JsonMapper json;

	ReviewQueries(OnboardingApplicationRepository applications, ApplicationService applicationService,
			ApplicationDocumentRepository documents, BusinessProfileRepository profiles, ReviewActionRepository reviewActions,
			CorrectionRequestRepository corrections, ReviewService review, VerificationApi verification,
			AuditTrail auditTrail, NamedParameterJdbcTemplate jdbc, BlindIndex index, JsonMapper json) {
		this.applications = applications;
		this.applicationService = applicationService;
		this.documents = documents;
		this.profiles = profiles;
		this.reviewActions = reviewActions;
		this.corrections = corrections;
		this.review = review;
		this.verification = verification;
		this.auditTrail = auditTrail;
		this.jdbc = jdbc;
		this.index = index;
		this.json = json;
	}

	/**
	 * S1: the review queue, oldest submission first.
	 *
	 * @param q company name (contains), an {@code onb_…} id, or an exact PAN (matched by blind index)
	 */
	@Transactional(readOnly = true)
	Map<String, Object> list(Caller staff, List<String> statuses, boolean mine, String q, int limit, String cursor) {
		int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
		int offset = decodeCursor(cursor);
		List<String> wanted = statuses == null || statuses.isEmpty()
				? ReviewService.QUEUE.stream().map(Enum::name).toList()
				: statuses.stream().map(String::toUpperCase).toList();
		for (String status : wanted) {
			try {
				ApplicationStatus.valueOf(status);
			}
			catch (IllegalArgumentException ex) {
				throw ValidationException.field("status", "Unknown status " + status);
			}
		}
		StringBuilder sql = new StringBuilder("""
				SELECT a.public_id, a.status, a.submitted_at, a.created_at, a.assigned_staff_id, a.entity_type,
				       p.legal_name, p.pan_masked
				FROM onboarding_application a LEFT JOIN business_profile p ON p.application_id = a.id
				WHERE a.status IN (:statuses)""");
		MapSqlParameterSource params = new MapSqlParameterSource("statuses", wanted);
		if (mine) {
			sql.append(" AND a.assigned_staff_id = :me");
			params.addValue("me", uuidBytes(staff.userId()));
		}
		if (q != null && !q.isBlank()) {
			String term = q.trim();
			if (term.startsWith("onb_")) {
				sql.append(" AND a.public_id = :q");
				params.addValue("q", term);
			}
			else if (term.toUpperCase().matches("[A-Z]{5}\\d{4}[A-Z]")) {
				sql.append(" AND p.pan_hash = :pan");
				params.addValue("pan", index.hash("pan", term.toUpperCase()));
			}
			else {
				sql.append(" AND p.legal_name LIKE :like");
				params.addValue("like", "%" + term.replace("%", "").replace("_", "") + "%");
			}
		}
		sql.append(" ORDER BY COALESCE(a.submitted_at, a.created_at), a.id LIMIT :limit OFFSET :offset");
		params.addValue("limit", size + 1).addValue("offset", offset);
		List<Map<String, Object>> rows = jdbc.query(sql.toString(), params, (rs, row) -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("onboarding_id", rs.getString("public_id"));
			item.put("status", rs.getString("status"));
			item.put("company_name", rs.getString("legal_name"));
			item.put("pan_masked", rs.getString("pan_masked"));
			item.put("entity_type", rs.getString("entity_type"));
			// DATETIME holds UTC; getTimestamp would read it in the JVM's zone
			LocalDateTime submitted = rs.getObject("submitted_at", LocalDateTime.class);
			item.put("submitted_at", submitted == null ? null : submitted.toInstant(ZoneOffset.UTC));
			byte[] assigned = rs.getBytes("assigned_staff_id");
			item.put("assigned_staff_id", assigned == null ? null : uuid(assigned));
			return item;
		});
		boolean more = rows.size() > size;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("items", more ? rows.subList(0, size) : rows);
		result.put("next_cursor", more ? encodeCursor(offset + size) : null);
		return result;
	}

	/** S2: everything a reviewer needs on one screen. */
	@Transactional(readOnly = true)
	Map<String, Object> caseView(String publicId) {
		OnboardingApplication application = applications.findByPublicId(publicId)
			.orElseThrow(() -> new NotFoundException("ONBOARDING_NOT_FOUND", "Application not found"));
		UUID id = application.getId();
		Map<String, Object> view = new LinkedHashMap<>();
		ApplicationSnapshot snapshot = applicationService.snapshot(application);
		view.put("application", snapshot);
		view.put("assigned_staff_id", application.getAssignedStaffId());
		profiles.findByApplicationId(id).ifPresent(p -> {
			Map<String, Object> profile = new LinkedHashMap<>();
			profile.put("legal_name", p.getLegalName());
			profile.put("company_type", p.getCompanyType());
			profile.put("pan_masked", p.getPanMasked());
			profile.put("pan_holder_type", p.getPanHolderType());
			profile.put("name_as_per_pan", p.getNameAsPerPan());
			profile.put("authorized_signatory_name", p.getSignatoryName());
			profile.put("contact_mobile_masked", p.getContactMobileMasked());
			profile.put("email_masked", p.getEmailMasked());
			profile.put("kyc_name", p.getKycName());
			profile.put("aadhaar_masked", p.getAadhaarMasked());
			profile.put("gstin", p.getGstin());
			profile.put("cin", p.getCin());
			profile.put("address", String.join(", ", java.util.stream.Stream
				.of(p.getAddressLine1(), p.getAddressLine2(), p.getDistrict(), p.getState(), p.getPincode())
				.filter(part -> part != null && !part.isBlank())
				.toList()));
			view.put("business_profile", profile);
		});
		List<ApplicationDocument> documentRows = documents.findByApplicationIdOrderByCreatedAtAsc(id);
		List<VerificationApi.AttemptView> attempts = verification.attempts(id);
		view.put("documents", documentRows.stream().map(d -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("document_id", d.getPublicId());
			item.put("doc_type", d.getDocType());
			item.put("status", d.getStatus());
			item.put("reject_reason_code", d.getRejectReasonCode());
			item.put("uploaded_at", d.getUploadedAt());
			return item;
		}).toList());
		view.put("verifications", attempts.stream().map(a -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("verification_id", a.verificationId());
			item.put("kind", a.kind());
			item.put("status", a.status());
			item.put("name_match_score", a.nameMatchScore());
			item.put("failure_code", a.failureCode());
			item.put("result", a.resultJson() == null ? null : json.readTree(a.resultJson()));
			item.put("created_at", a.createdAt());
			return item;
		}).toList());
		List<ReviewAction> history = reviewActions.findByApplicationIdOrderByOccurredAtAsc(id);
		view.put("review_history", history.stream().map(r -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("action", r.getAction());
			item.put("staff_id", r.getStaffId());
			item.put("decision", r.getDecision());
			item.put("reason_code", r.getReasonCode());
			item.put("note", r.getNote());
			item.put("applicant_message", r.getApplicantMessage());
			item.put("target", r.getTargetRef());
			item.put("occurred_at", r.getOccurredAt());
			return item;
		}).toList());
		view.put("corrections", corrections.findByApplicationIdOrderByCreatedAtDesc(id).stream().map(c -> {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("step_keys", json.readTree(c.getStepKeys()));
			item.put("reason_code", c.getReasonCode());
			item.put("message", c.getMessage());
			item.put("due_at", c.getDueAt());
			item.put("open", c.isOpen());
			return item;
		}).toList());
		view.put("flags", flags(snapshot, id));
		view.put("approval_blockers", review.approvalBlockers(review.latestRecommendation(history), documentRows, attempts));
		return view;
	}

	/** S21 */
	@Transactional(readOnly = true)
	List<AuditTrail.AuditRecord> auditTrail(String publicId) {
		OnboardingApplication application = applications.findByPublicId(publicId)
			.orElseThrow(() -> new NotFoundException("ONBOARDING_NOT_FOUND", "Application not found"));
		return auditTrail.forTarget("ONBOARDING_APPLICATION", List.of(publicId, application.getId().toString()), 500);
	}

	/** Risk and mismatch signals the reviewer should look at (E3, E9, OQ-B6). */
	private List<String> flags(ApplicationSnapshot snapshot, UUID applicationId) {
		List<String> flags = new ArrayList<>();
		for (ApplicationSnapshot.Step step : snapshot.steps()) {
			JsonNode summary = step.summary();
			if (summary == null) {
				continue;
			}
			if (step.stepKey().equals(StepKey.BUSINESS_DETAILS.name()) && !summary.path("name_matches_pan").asBoolean(true)) {
				flags.add("BUSINESS_NAME_DIFFERS_FROM_PAN");
			}
			if (step.stepKey().equals(StepKey.SIGNATORY_KYC.name())
					&& !summary.path("name_matches_signatory").asBoolean(true)) {
				flags.add("KYC_NAME_DIFFERS_FROM_SIGNATORY");
			}
			if (step.stepKey().equals(StepKey.BANK_ACCOUNT.name()) && !summary.path("name_matches").asBoolean(true)) {
				flags.add("BANK_HOLDER_DIFFERS");
			}
		}
		if (verification.bankAccountReuseCount(applicationId) > 0) {
			flags.add("BANK_ACCOUNT_USED_BY_ANOTHER_APPLICATION");
		}
		return flags;
	}

	private static int decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return 0;
		}
		try {
			String plain = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			return Math.max(0, Integer.parseInt(plain.substring(2)));
		}
		catch (RuntimeException ex) {
			throw ValidationException.field("cursor", "Invalid cursor");
		}
	}

	private static String encodeCursor(int offset) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(("o:" + offset).getBytes(StandardCharsets.UTF_8));
	}

	private static byte[] uuidBytes(UUID id) {
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
		buffer.putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
		return buffer.array();
	}

	private static UUID uuid(byte[] bytes) {
		java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
		return new UUID(buffer.getLong(), buffer.getLong());
	}
}
