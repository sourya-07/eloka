package in.eko.eloka.onboarding.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import in.eko.eloka.documents.DocumentContent;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.StaffPermission;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Staff review API (S1–S13, S21; docs/onboarding/P8_STAFF_REVIEW.md). Actions answer with the
 * updated case. Side-effect POSTs take an {@code Idempotency-Key}.
 */
@RestController
@RequestMapping("/v1/admin/applications")
class StaffReviewController {

	private final ReviewService review;
	private final ReviewQueries queries;
	private final ApplicationDocuments documents;
	private final OnboardingApplicationRepository applications;
	private final Idempotency idempotency;
	private final AuditLog audit;

	StaffReviewController(ReviewService review, ReviewQueries queries, ApplicationDocuments documents,
			OnboardingApplicationRepository applications, Idempotency idempotency, AuditLog audit) {
		this.review = review;
		this.queries = queries;
		this.documents = documents;
		this.applications = applications;
		this.idempotency = idempotency;
		this.audit = audit;
	}

	/** S1 */
	@GetMapping
	Map<String, Object> list(@RequestParam(name = "status", required = false) String status,
			@RequestParam(name = "assigned_to", required = false) String assignedTo,
			@RequestParam(name = "q", required = false) String q, @RequestParam(name = "limit", defaultValue = "50") int limit,
			@RequestParam(name = "cursor", required = false) String cursor) {
		Caller staff = WebSupport.staff();
		ReviewService.require(staff, StaffPermission.ONBOARDING_READ);
		List<String> statuses = status == null || status.isBlank() ? List.of() : Arrays.asList(status.split(","));
		return queries.list(staff, statuses, "me".equalsIgnoreCase(assignedTo), q, limit, cursor);
	}

	/** S2 */
	@GetMapping("/{id}")
	Map<String, Object> get(@PathVariable String id) {
		ReviewService.require(WebSupport.staff(), StaffPermission.ONBOARDING_READ);
		return queries.caseView(id);
	}

	/** S3 */
	@PostMapping("/{id}/claim")
	ResponseEntity<?> claim(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request, () -> review.claim(staff, id));
	}

	/** S4 */
	@PostMapping("/{id}/release")
	ResponseEntity<?> release(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request, () -> review.release(staff, id, text(body, "reason", false)));
	}

	/** S5 */
	@PostMapping("/{id}/documents/{documentId}/verify")
	ResponseEntity<?> verifyDocument(@PathVariable String id, @PathVariable String documentId,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody(required = false) JsonNode body,
			HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request,
				() -> review.decideDocument(staff, id, documentId, true, null, text(body, "note", false)));
	}

	/** S6 */
	@PostMapping("/{id}/documents/{documentId}/reject")
	ResponseEntity<?> rejectDocument(@PathVariable String id, @PathVariable String documentId,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody(required = false) JsonNode body,
			HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request, () -> review.decideDocument(staff, id, documentId, false,
				text(body, "reason_code", true), text(body, "message", false)));
	}

	/** Reviewers open the file (audited). */
	@GetMapping("/{id}/documents/{documentId}/download")
	ResponseEntity<byte[]> download(@PathVariable String id, @PathVariable String documentId) {
		Caller staff = WebSupport.staff();
		ReviewService.require(staff, StaffPermission.ONBOARDING_READ);
		UUID applicationId = applications.findByPublicId(id)
			.orElseThrow(() -> new NotFoundException("ONBOARDING_NOT_FOUND", "Application not found"))
			.getId();
		DocumentContent content = documents.download(applicationId, documentId);
		audit.record(new AuditEntry("DOCUMENT_VIEWED", "STAFF", staff.userId().toString(), IdentityApi.PLATFORM_ORG_ID,
				"ONBOARDING_APPLICATION", id, Map.of("document", documentId)));
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(content.ref().contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(content.ref().fileName()).build().toString())
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(content.bytes());
	}

	/** S7 */
	@PostMapping("/{id}/verifications/{verificationId}/decide")
	ResponseEntity<?> decide(@PathVariable String id, @PathVariable UUID verificationId,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody JsonNode body,
			HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		String decision = text(body, "decision", true);
		if (!"ACCEPT".equals(decision) && !"REJECT".equals(decision)) {
			throw ValidationException.field("decision", "ACCEPT or REJECT");
		}
		return act(staff, id, key, body, request, () -> review.decideVerification(staff, id, verificationId,
				"ACCEPT".equals(decision), text(body, "reason_code", false)));
	}

	/** S8 */
	@PostMapping("/{id}/corrections")
	ResponseEntity<?> corrections(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		List<String> steps = new ArrayList<>();
		if (body != null && body.has("step_keys") && body.get("step_keys").isArray()) {
			body.get("step_keys").forEach(node -> steps.add(node.asString()));
		}
		Integer days = body != null && body.hasNonNull("due_in_days") ? body.get("due_in_days").asInt() : null;
		return act(staff, id, key, body, request, () -> review.requestCorrection(staff, id, steps,
				text(body, "reason_code", true), text(body, "message", true), days));
	}

	/** S9 */
	@PostMapping("/{id}/recommendations")
	ResponseEntity<?> recommend(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request,
				() -> review.recommend(staff, id, text(body, "recommendation", true), text(body, "note", false)));
	}

	/** S10 */
	@PostMapping("/{id}/approve")
	ResponseEntity<?> approve(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request, () -> review.approve(staff, id, text(body, "note", false)));
	}

	/** S11 */
	@PostMapping("/{id}/reject")
	ResponseEntity<?> reject(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request, () -> review.reject(staff, id, text(body, "reason_code", true),
				text(body, "applicant_message", false), text(body, "internal_note", false)));
	}

	/** S12: hold */
	@PostMapping("/{id}/holds")
	ResponseEntity<?> hold(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		Caller staff = WebSupport.staff();
		return act(staff, id, key, body, request, () -> review.hold(staff, id, text(body, "reason_code", true), true));
	}

	/** S12: release the hold */
	@DeleteMapping("/{id}/holds")
	Map<String, Object> releaseHold(@PathVariable String id) {
		Caller staff = WebSupport.staff();
		review.hold(staff, id, null, false);
		return queries.caseView(id);
	}

	/** S13: valid for this one response only; nothing is cached. */
	@PostMapping("/{id}/pii-reveal")
	ResponseEntity<Map<String, Object>> reveal(@PathVariable String id, @RequestBody JsonNode body) {
		Caller staff = WebSupport.staff();
		String field = text(body, "field", true);
		String value = review.revealPii(staff, id, field, text(body, "purpose", true));
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(Map.of("field", field, "value", value));
	}

	/** S21 */
	@GetMapping("/{id}/audit")
	Map<String, Object> audit(@PathVariable String id) {
		ReviewService.require(WebSupport.staff(), StaffPermission.AUDIT_READ);
		return Map.of("items", queries.auditTrail(id));
	}

	private ResponseEntity<?> act(Caller staff, String id, String key, JsonNode body, HttpServletRequest request,
			Runnable action) {
		return idempotency.run(staff.userId(), key, WebSupport.fingerprint(request, body), () -> {
			action.run();
			return ResponseEntity.ok(queries.caseView(id));
		});
	}

	private static String text(JsonNode body, String field, boolean required) {
		JsonNode node = body == null ? null : body.get(field);
		String value = node == null || node.isNull() ? null : node.asString().trim();
		if (required && (value == null || value.isEmpty())) {
			throw ValidationException.field(field, field + " is required");
		}
		return value == null || value.isEmpty() ? null : value;
	}
}
