package in.eko.eloka.onboarding.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.agreement.AgreementApi;
import in.eko.eloka.shared.security.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Agreement and e-sign (A27–A29). Only the server marks an agreement signed. */
@RestController
@RequestMapping("/v1/onboarding/applications/{id}/agreements")
class AgreementController {

	private final VerificationSteps steps;
	private final ApplicationService applications;
	private final ApplicationController applicationController;
	private final AgreementApi agreements;
	private final Idempotency idempotency;

	AgreementController(VerificationSteps steps, ApplicationService applications, ApplicationController applicationController,
			AgreementApi agreements, Idempotency idempotency) {
		this.steps = steps;
		this.applications = applications;
		this.applicationController = applicationController;
		this.agreements = agreements;
		this.idempotency = idempotency;
	}

	/** A27 */
	@PostMapping
	ResponseEntity<?> prepare(@PathVariable String id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		UUID applicantId = WebSupport.applicantId();
		long version = WebSupport.expectedVersion(ifMatch);
		return idempotency.run(applicantId, key, WebSupport.fingerprint(request, body), () -> {
			AgreementApi.AgreementView view = steps.prepareAgreement(applicantId, id, version);
			Map<String, Object> result = agreementBody(id, view);
			ApplicationSnapshot snapshot = applications.get(applicantId, id);
			result.put("application", snapshot);
			return ResponseEntity.status(HttpStatus.CREATED).eTag(WebSupport.etag(snapshot)).body(result);
		});
	}

	/** A28: needs a fresh step-up token ({@code X-Reauth-Token}). */
	@PostMapping("/{agreementId}/esign-sessions")
	ResponseEntity<?> esign(@PathVariable String id, @PathVariable String agreementId,
			@RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
			@RequestHeader(value = Idempotency.HEADER, required = false) String key,
			@RequestHeader(value = WebSupport.REAUTH_HEADER, required = false) String reauthToken,
			@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
		Caller caller = WebSupport.applicant();
		long version = WebSupport.expectedVersion(ifMatch);
		return idempotency.run(caller.userId(), key, WebSupport.fingerprint(request, body), () -> {
			applicationController.requireReauth(caller, reauthToken);
			AgreementApi.EsignStart start = steps.startEsign(caller.userId(), id, version, agreementId);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("esign_request_id", start.esignRequestId());
			result.put("provider_pipe", start.providerPipe());
			result.put("signing_url", start.signingUrl());
			result.put("document_id", start.documentId());
			result.put("expires_at", start.expiresAt());
			result.put("already_signed", start.alreadySigned());
			ApplicationSnapshot snapshot = applications.get(caller.userId(), id);
			result.put("application", snapshot);
			return ResponseEntity.status(HttpStatus.CREATED).eTag(WebSupport.etag(snapshot)).body(result);
		});
	}

	/** A29: call when the signing window closes; completes the step and auto-submits once signed. */
	@GetMapping("/{agreementId}")
	ResponseEntity<Map<String, Object>> status(@PathVariable String id, @PathVariable String agreementId) {
		UUID applicantId = WebSupport.applicantId();
		AgreementApi.AgreementView view = steps.agreementStatus(applicantId, id, agreementId);
		Map<String, Object> result = agreementBody(id, view);
		result.put("signed_at", view.signedAt());
		result.put("signed_document_available", view.isSigned());
		ApplicationSnapshot snapshot = applications.get(applicantId, id);
		result.put("application", snapshot);
		return ResponseEntity.ok().eTag(WebSupport.etag(snapshot)).body(result);
	}

	/** The rendered PDF (the {@code preview_url} of A27). */
	@GetMapping("/{agreementId}/pdf")
	ResponseEntity<byte[]> pdf(@PathVariable String id, @PathVariable String agreementId) {
		UUID applicationId = applications.find(WebSupport.applicantId(), id).getId();
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.body(agreements.pdf(applicationId, agreementId));
	}

	private static Map<String, Object> agreementBody(String applicationId, AgreementApi.AgreementView view) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("agreement_id", view.agreementId());
		result.put("version", view.versionNo());
		result.put("template_version", view.templateVersion());
		result.put("status", view.status());
		result.put("content_sha256", view.contentSha256());
		result.put("preview_url", "/v1/onboarding/applications/" + applicationId + "/agreements/" + view.agreementId() + "/pdf");
		return result;
	}
}
