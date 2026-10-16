package in.eko.eloka.onboarding.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.identity.ApplicantOtpResult;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.OtpDispatch;
import in.eko.eloka.identity.TokenPair;
import in.eko.eloka.onboarding.OnboardingProperties;
import in.eko.eloka.shared.crypto.BlindIndex;
import in.eko.eloka.shared.crypto.FieldCipher;
import in.eko.eloka.shared.crypto.Mobiles;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * Applicant identity (A1–A4) and step-up re-auth. Answers are the same for known and unknown
 * mobiles, so the API does not reveal who has applied.
 */
@RestController
@RequestMapping("/v1/onboarding")
class ApplicantSessionController {

	private static final String OTP_REF_PREFIX = "otpref_";

	private final IdentityApi identity;
	private final ApplicationService applications;
	private final Idempotency idempotency;
	private final FieldCipher cipher;
	private final BlindIndex index;
	private final OnboardingProperties properties;
	private final in.eko.eloka.identity.IdentityProperties identityProperties;
	private final Clock clock;

	ApplicantSessionController(IdentityApi identity, ApplicationService applications, Idempotency idempotency,
			FieldCipher cipher, BlindIndex index, OnboardingProperties properties,
			in.eko.eloka.identity.IdentityProperties identityProperties, Clock clock) {
		this.identity = identity;
		this.applications = applications;
		this.idempotency = idempotency;
		this.cipher = cipher;
		this.index = index;
		this.properties = properties;
		this.identityProperties = identityProperties;
		this.clock = clock;
	}

	/** A1: send the login OTP. {@code invite_token} is accepted and ignored until leads exist (P8). */
	@PostMapping("/otp")
	ResponseEntity<?> sendOtp(@RequestBody JsonNode body, @RequestHeader(value = Idempotency.HEADER, required = false) String key,
			HttpServletRequest request) {
		String mobile = Mobiles.normalize(text(body, "mobile"));
		UUID scope = UUID.nameUUIDFromBytes(index.hash("mobile", mobile));
		return idempotency.run(scope, key, WebSupport.fingerprint(request, body), () -> {
			OtpDispatch sent = identity.sendApplicantOtp(mobile, request.getRemoteAddr());
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("otp_ref", otpRef(mobile));
			result.put("expires_in", identityProperties.otpTtl().toSeconds());
			result.put("resend_after", identityProperties.otpMinGap().toSeconds());
			result.put("otp_length", sent.length());
			if (sent.revealedCode() != null) {
				result.put("otp", sent.revealedCode()); // local profile only ("Demo OTP")
			}
			return ResponseEntity.accepted().body(result);
		});
	}

	/** A2: verify the OTP, then continue the live application or start one. */
	@PostMapping("/otp/verify")
	ResponseEntity<Map<String, Object>> verifyOtp(@RequestBody JsonNode body) {
		String mobile = mobileFromRef(text(body, "otp_ref"));
		ApplicantOtpResult result = identity.verifyApplicantOtp(mobile, text(body, "otp"));
		if (result instanceof ApplicantOtpResult.Expired) {
			throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED", "This OTP has expired. Please request a new one.");
		}
		if (result instanceof ApplicantOtpResult.WrongCode wrong) {
			if (wrong.attemptsLeft() == 0) {
				throw new ApiProblem(HttpStatus.TOO_MANY_REQUESTS, "OTP_LOCKED",
						"Too many wrong attempts. Please request a new OTP.");
			}
			throw new ApiProblem(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "The OTP is not correct",
					Map.of("attempts_left", wrong.attemptsLeft()));
		}
		ApplicationService.Start start = applications.startOrResume(mobile);
		TokenPair tokens = identity.openApplicantSession(start.applicantId(), mobile);
		Map<String, Object> response = tokenBody(tokens);
		response.put("resumed", start.resumed());
		response.put("application", start.snapshot());
		return ResponseEntity.ok().eTag(WebSupport.etag(start.snapshot())).body(response);
	}

	/** A3: rotate the tokens with the refresh token (works after the access token expired). */
	@PostMapping("/session/refresh")
	Map<String, Object> refresh(@RequestBody JsonNode body) {
		return identity.refresh(text(body, "refresh_token"), java.util.EnumSet.of(SessionKind.LA_APPLICANT))
			.map(ApplicantSessionController::tokenBody)
			.orElseThrow(() -> new ApiProblem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
					"Your session has expired. Please log in again."));
	}

	/** A4: log out. */
	@DeleteMapping("/session")
	ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
		WebSupport.applicant();
		identity.revoke(authorization.replaceFirst("(?i)^Bearer\\s+", ""), "LOGOUT");
		return ResponseEntity.noContent().build();
	}

	/** Step-up: sends an OTP to the verified mobile before bank, e-sign or withdraw. */
	@PostMapping("/reauth")
	ResponseEntity<Map<String, Object>> sendReauth(HttpServletRequest request) {
		Caller caller = WebSupport.applicant();
		OtpDispatch sent = identity.sendReauthOtp(caller.orgId(), caller.sessionId(), caller.mobile(), request.getRemoteAddr());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("expires_in", identityProperties.otpTtl().toSeconds());
		result.put("otp_length", sent.length());
		if (sent.revealedCode() != null) {
			result.put("otp", sent.revealedCode());
		}
		return ResponseEntity.accepted().body(result);
	}

	/** Step-up: verifies the OTP and returns a single-use {@code X-Reauth-Token} (5 minutes). */
	@PostMapping("/reauth/verify")
	Map<String, Object> verifyReauth(@RequestBody JsonNode body) {
		Caller caller = WebSupport.applicant();
		String token = identity.verifyReauthOtp(caller.orgId(), caller.sessionId(), text(body, "otp"))
			.orElseThrow(() -> new ApiProblem(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "The OTP is not correct"));
		return Map.of("reauth_token", token, "expires_in", 300);
	}

	private static Map<String, Object> tokenBody(TokenPair tokens) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("access_token", tokens.accessToken());
		body.put("refresh_token", tokens.refreshToken());
		body.put("token_type", "Bearer");
		body.put("expires_in", tokens.expiresIn().toSeconds());
		return body;
	}

	/** Opaque reference to the mobile an OTP was sent to: encrypted, time-stamped, never a lookup key. */
	private String otpRef(String mobile) {
		byte[] sealed = cipher.encryptBytes((mobile + "|" + Instant.now(clock).getEpochSecond()).getBytes(StandardCharsets.UTF_8));
		return OTP_REF_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(sealed);
	}

	private String mobileFromRef(String ref) {
		if (!ref.startsWith(OTP_REF_PREFIX)) {
			throw invalidRef();
		}
		String plain;
		try {
			plain = new String(cipher.decryptBytes(Base64.getUrlDecoder().decode(ref.substring(OTP_REF_PREFIX.length()))),
					StandardCharsets.UTF_8);
		}
		catch (RuntimeException ex) {
			throw invalidRef();
		}
		String[] parts = plain.split("\\|");
		if (parts.length != 2) {
			throw invalidRef();
		}
		Instant issued = Instant.ofEpochSecond(Long.parseLong(parts[1]));
		if (issued.plus(properties.otpRefTtl()).isBefore(Instant.now(clock))) {
			throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED", "This OTP has expired. Please request a new one.");
		}
		return parts[0];
	}

	private static ValidationException invalidRef() {
		return ValidationException.field("otp_ref", "Request a new OTP");
	}

	static String text(JsonNode body, String field) {
		JsonNode node = body == null ? null : body.get(field);
		if (node == null || node.isNull() || node.asString().isBlank()) {
			throw ValidationException.field(field, field + " is required");
		}
		return node.asString().trim();
	}
}
