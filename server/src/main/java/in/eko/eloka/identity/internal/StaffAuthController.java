package in.eko.eloka.identity.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import in.eko.eloka.identity.IdentityApi;
import in.eko.eloka.identity.OtpDispatch;
import in.eko.eloka.identity.StaffLoginResult;
import in.eko.eloka.identity.TokenPair;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.CallerContext;
import in.eko.eloka.shared.security.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Staff sign-in for the review console (P8). Unknown and disabled accounts get the same answer. */
@RestController
@RequestMapping("/v1/admin")
class StaffAuthController {

	private final IdentityApi identity;

	StaffAuthController(IdentityApi identity) {
		this.identity = identity;
	}

	/** Production path: a Google ID token from the staff console's Google sign-in button. */
	@PostMapping("/auth/google")
	Map<String, Object> google(@RequestBody JsonNode body) {
		return respond(identity.staffGoogleLogin(text(body, "id_token")));
	}

	/** Local/test only (404 elsewhere). */
	@PostMapping("/auth/otp")
	ResponseEntity<Map<String, Object>> sendOtp(@RequestBody JsonNode body, HttpServletRequest request) {
		OtpDispatch sent = identity.sendStaffOtp(text(body, "mobile"), request.getRemoteAddr());
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("otp_length", sent.length());
		if (sent.revealedCode() != null) {
			result.put("otp", sent.revealedCode());
		}
		return ResponseEntity.accepted().body(result);
	}

	@PostMapping("/auth/otp/verify")
	Map<String, Object> verifyOtp(@RequestBody JsonNode body) {
		return respond(identity.verifyStaffOtp(text(body, "mobile"), text(body, "otp")));
	}

	@PostMapping("/auth/refresh")
	Map<String, Object> refresh(@RequestBody JsonNode body) {
		return identity.refresh(text(body, "refresh_token"), java.util.EnumSet.of(SessionKind.STAFF))
			.map(StaffAuthController::tokens)
			.orElseThrow(() -> new ApiProblem(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
					"Your session has expired. Please sign in again."));
	}

	@DeleteMapping("/auth/session")
	ResponseEntity<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
		staff();
		identity.revoke(authorization.replaceFirst("(?i)^Bearer\\s+", ""), "LOGOUT");
		return ResponseEntity.noContent().build();
	}

	/** Who am I, and what may I do (the console hides buttons the staff member cannot use). */
	@GetMapping("/me")
	Map<String, Object> me() {
		Caller caller = staff();
		var view = identity.staff(caller.userId()).orElseThrow();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("staff_id", view.id());
		result.put("email", view.email());
		result.put("display_name", view.displayName());
		result.put("roles", view.roles());
		result.put("permissions", caller.permissions());
		return result;
	}

	private static Caller staff() {
		Caller caller = CallerContext.require();
		if (caller.kind() != SessionKind.STAFF) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this.");
		}
		return caller;
	}

	private static Map<String, Object> respond(StaffLoginResult result) {
		return switch (result) {
			case StaffLoginResult.Success success -> {
				Map<String, Object> body = tokens(success.tokens());
				body.put("staff", Map.of("staff_id", success.staff().id(), "email", success.staff().email(),
						"display_name", success.staff().displayName(), "roles", success.staff().roles()));
				yield body;
			}
			case StaffLoginResult.WrongCode wrong -> throw new ApiProblem(HttpStatus.UNAUTHORIZED, "OTP_INVALID",
					"The OTP is not correct", Map.of("attempts_left", wrong.attemptsLeft()));
			case StaffLoginResult.Expired expired -> throw new ApiProblem(HttpStatus.GONE, "OTP_EXPIRED",
					"This OTP has expired. Please request a new one.");
			case StaffLoginResult.NotAllowed notAllowed -> throw new ApiProblem(HttpStatus.FORBIDDEN, "STAFF_NOT_ALLOWED",
					"This account cannot sign in to the staff console.");
		};
	}

	private static Map<String, Object> tokens(TokenPair tokens) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("access_token", tokens.accessToken());
		body.put("refresh_token", tokens.refreshToken());
		body.put("token_type", "Bearer");
		body.put("expires_in", tokens.expiresIn().toSeconds());
		return body;
	}

	private static String text(JsonNode body, String field) {
		JsonNode node = body == null ? null : body.get(field);
		if (node == null || node.isNull() || node.asString().isBlank()) {
			throw ValidationException.field(field, field + " is required");
		}
		return node.asString().trim();
	}
}
