package in.eko.eloka.onboarding.internal;

import java.util.List;
import java.util.Map;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.onboarding.StepKey;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.security.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** The application and its steps (A5–A11) and the applicant timeline (A30). */
@RestController
@RequestMapping("/v1/onboarding/applications")
class ApplicationController {

	private final ApplicationService applications;
	private final Idempotency idempotency;
	private final IdentityApi identity;

	ApplicationController(ApplicationService applications, Idempotency idempotency, IdentityApi identity) {
		this.applications = applications;
		this.idempotency = idempotency;
		this.identity = identity;
	}

	/** A5 */
	@GetMapping("/current")
	ResponseEntity<ApplicationSnapshot> current() {
		return applications.current(WebSupport.applicantId())
			.map(WebSupport::ok)
			.orElseThrow(() -> new NotFoundException("ONBOARDING_NOT_FOUND", "No application in progress"));
	}

	/** A6 */
	@GetMapping("/{id}")
	ResponseEntity<ApplicationSnapshot> get(@PathVariable String id) {
		return WebSupport.ok(applications.get(WebSupport.applicantId(), id));
	}

	/** A7: save a draft (no verification). */
	@PutMapping("/{id}/steps/{stepKey}")
	ResponseEntity<ApplicationSnapshot> saveDraft(@PathVariable String id, @PathVariable String stepKey,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch, @RequestBody JsonNode body) {
		return WebSupport.ok(applications.saveDraft(WebSupport.applicantId(), id, WebSupport.expectedVersion(ifMatch),
				step(stepKey), body == null ? null : body.get("draft")));
	}

	/** A8: validate and complete a data step. */
	@PostMapping("/{id}/steps/{stepKey}/submit")
	ResponseEntity<?> submitStep(@PathVariable String id, @PathVariable String stepKey,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody(required = false) JsonNode body,
			HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		StepKey step = step(stepKey);
		return idempotency.run(WebSupport.applicantId(), key, WebSupport.fingerprint(request, body),
				() -> WebSupport.ok(applications.submitStep(WebSupport.applicantId(), id, version, step,
						body == null ? null : body.get("data"))));
	}

	/** A9: skip an optional step. */
	@PostMapping("/{id}/steps/{stepKey}/skip")
	ResponseEntity<?> skip(@PathVariable String id, @PathVariable String stepKey,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody(required = false) JsonNode body,
			HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		StepKey step = step(stepKey);
		return idempotency.run(WebSupport.applicantId(), key, WebSupport.fingerprint(request, body),
				() -> WebSupport.ok(applications.skip(WebSupport.applicantId(), id, version, step)));
	}

	/** A10: submit (normally automatic once the agreement is signed). */
	@PostMapping("/{id}/submit")
	ResponseEntity<?> submit(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody(required = false) JsonNode body,
			HttpServletRequest request) {
		long version = WebSupport.expectedVersion(ifMatch);
		return idempotency.run(WebSupport.applicantId(), key, WebSupport.fingerprint(request, body),
				() -> WebSupport.ok(applications.submit(WebSupport.applicantId(), id, version)));
	}

	/** A11: withdraw; needs a fresh step-up token ({@code X-Reauth-Token}). */
	@PostMapping("/{id}/withdraw")
	ResponseEntity<?> withdraw(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestHeader(value = WebSupport.REAUTH_HEADER, required = false) String reauthToken,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		Caller caller = WebSupport.applicant();
		long version = WebSupport.expectedVersion(ifMatch);
		return idempotency.run(caller.userId(), key, WebSupport.fingerprint(request, body), () -> {
			requireReauth(caller, reauthToken);
			String reason = body != null && body.hasNonNull("reason_code") ? body.get("reason_code").asString() : null;
			return WebSupport.ok(applications.withdraw(caller.userId(), id, version, reason));
		});
	}

	/** A30: applicant-safe history. */
	@GetMapping("/{id}/timeline")
	Map<String, Object> timeline(@PathVariable String id) {
		List<Map<String, Object>> items = applications.timeline(WebSupport.applicantId(), id)
			.stream()
			.map(entry -> {
				Map<String, Object> item = new java.util.LinkedHashMap<>();
				item.put("event", entry.getEventType());
				if (entry.getStepKey() != null) {
					item.put("step_key", entry.getStepKey().name());
				}
				if (entry.getToStatus() != null) {
					item.put("status", entry.getToStatus().name());
				}
				item.put("occurred_at", entry.getOccurredAt());
				return item;
			})
			.toList();
		return Map.of("items", items);
	}

	void requireReauth(Caller caller, String reauthToken) {
		if (!identity.consumeReauthToken(caller.sessionId(), reauthToken)) {
			throw new ApiProblem(HttpStatus.UNAUTHORIZED, "REAUTH_REQUIRED",
					"Confirm with a new OTP first (POST /v1/onboarding/reauth)");
		}
	}

	static StepKey step(String value) {
		try {
			return StepKey.valueOf(value.toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			throw new NotFoundException("STEP_NOT_FOUND", "Unknown step " + value);
		}
	}
}
