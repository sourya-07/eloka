package in.eko.eloka.onboarding.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.verification.VerificationApi;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * Verification resources (A12–A18). A failed attempt is committed (it counts towards blocking the
 * step) and answered with {@code 422 VERIFICATION_FAILED} plus the current snapshot.
 */
@RestController
@RequestMapping("/v1/onboarding/applications/{id}")
class VerificationController {

	private final VerificationSteps steps;
	private final ApplicationService applications;
	private final ApplicationController applicationController;
	private final VerificationApi verification;
	private final Idempotency idempotency;

	VerificationController(VerificationSteps steps, ApplicationService applications,
			ApplicationController applicationController, VerificationApi verification, Idempotency idempotency) {
		this.steps = steps;
		this.applications = applications;
		this.applicationController = applicationController;
		this.verification = verification;
		this.idempotency = idempotency;
	}

	/** A12 */
	@PostMapping("/pan-verifications")
	ResponseEntity<?> pan(@PathVariable String id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody JsonNode body,
			HttpServletRequest request) {
		UUID applicantId = WebSupport.applicantId();
		long version = WebSupport.expectedVersion(ifMatch);
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, body), () -> respond(applicantId, id,
				steps.verifyPan(applicantId, id, version, ApplicantSessionController.text(body, "pan"))));
	}

	/** A13 */
	@PostMapping("/email-verifications")
	ResponseEntity<?> email(@PathVariable String id, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody JsonNode body, HttpServletRequest request) {
		UUID applicantId = WebSupport.applicantId();
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, body), () -> ResponseEntity.accepted()
			.body(steps.startEmail(applicantId, id, ApplicantSessionController.text(body, "email"), request.getRemoteAddr())));
	}

	/** A14 */
	@PostMapping("/email-verifications/{verificationId}/confirm")
	ResponseEntity<ApplicationSnapshot> confirmEmail(@PathVariable String id, @PathVariable String verificationId,
			@RequestBody JsonNode body) {
		UUID applicantId = WebSupport.applicantId();
		steps.confirmEmail(applicantId, id, verificationId, ApplicantSessionController.text(body, "otp"));
		return WebSupport.ok(applications.get(applicantId, id));
	}

	/** A15 */
	@PostMapping("/kyc-sessions")
	ResponseEntity<?> startKyc(@PathVariable String id,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key, @RequestBody JsonNode body,
			HttpServletRequest request) {
		UUID applicantId = WebSupport.applicantId();
		long version = WebSupport.expectedVersion(ifMatch);
		String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, body), () -> {
			Map<String, Object> result = new LinkedHashMap<>(
					steps.startKyc(applicantId, id, version, ApplicantSessionController.text(body, "method"), baseUrl));
			ApplicationSnapshot snapshot = applications.get(applicantId, id);
			result.put("application", snapshot);
			return ResponseEntity.status(HttpStatus.CREATED).eTag(WebSupport.etag(snapshot)).body(result);
		});
	}

	/** A16 */
	@GetMapping("/kyc-sessions/{sessionId}")
	ResponseEntity<Map<String, Object>> kycStatus(@PathVariable String id, @PathVariable String sessionId) {
		UUID applicantId = WebSupport.applicantId();
		Map<String, Object> result = new LinkedHashMap<>(steps.kycStatus(applicantId, id, sessionId));
		ApplicationSnapshot snapshot = applications.get(applicantId, id);
		result.put("application", snapshot);
		return ResponseEntity.ok().eTag(WebSupport.etag(snapshot)).body(result);
	}

	/** A17: needs a fresh step-up token ({@code X-Reauth-Token}). */
	@PostMapping("/bank-accounts")
	ResponseEntity<?> bank(@PathVariable String id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestHeader(value = WebSupport.REAUTH_HEADER, required = false) String reauthToken, @RequestBody JsonNode body,
			HttpServletRequest request) {
		Caller caller = WebSupport.applicant();
		long version = WebSupport.expectedVersion(ifMatch);
		return idempotency.run(caller.userId(), key, WebSupport.fingerprint(request, body), () -> {
			applicationController.requireReauth(caller, reauthToken);
			return respond(caller.userId(), id, steps.addBankAccount(caller.userId(), id, version, optional(body, "bank_code"),
					optional(body, "bank_id"), optional(body, "ifsc"), optional(body, "account"),
					optional(body, "proof_document_id")));
		});
	}

	/** A18 */
	@GetMapping("/bank-accounts/{bankAccountId}")
	Map<String, Object> bankStatus(@PathVariable String id, @PathVariable String bankAccountId) {
		UUID applicationId = applications.find(WebSupport.applicantId(), id).getId();
		VerificationApi.BankResult result = verification.bankAccount(applicationId, bankAccountId)
			.orElseThrow(() -> new NotFoundException("BANK_ACCOUNT_NOT_FOUND", "Bank account not found"));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("bank_account_id", result.bankAccountId());
		body.put("status", result.status().name());
		body.put("account_masked", result.accountMasked());
		body.put("beneficiary_name_returned", result.beneficiaryName());
		body.put("name_match_score", result.nameMatchScore());
		return body;
	}

	/** 200 done, 202 waiting for the provider, 422 failed (with the committed snapshot). */
	private ResponseEntity<?> respond(UUID applicantId, String id, VerificationSteps.Outcome outcome) {
		ApplicationSnapshot snapshot = applications.get(applicantId, id);
		if (outcome.status() == VerificationApi.Status.FAILED) {
			ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
					"The verification failed. Please check the details and try again.");
			problem.setProperty("code", "VERIFICATION_FAILED");
			problem.setProperty("reason_code", outcome.failureCode());
			problem.setProperty("current", snapshot);
			return ResponseEntity.unprocessableContent().eTag(WebSupport.etag(snapshot)).body(problem);
		}
		Map<String, Object> body = new LinkedHashMap<>(outcome.body());
		body.put("application", snapshot);
		HttpStatus status = outcome.status() == VerificationApi.Status.PENDING ? HttpStatus.ACCEPTED : HttpStatus.OK;
		return ResponseEntity.status(status).eTag(WebSupport.etag(snapshot)).body(body);
	}

	private static String optional(JsonNode body, String field) {
		JsonNode value = body == null ? null : body.get(field);
		return value == null || value.isNull() ? null : value.asString();
	}
}
