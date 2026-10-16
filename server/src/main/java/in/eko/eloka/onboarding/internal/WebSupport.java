package in.eko.eloka.onboarding.internal;

import java.util.UUID;
import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.CallerContext;
import in.eko.eloka.shared.security.SessionKind;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Small helpers shared by the {@code /v1/onboarding} controllers. */
final class WebSupport {

	static final String REAUTH_HEADER = "X-Reauth-Token";

	private WebSupport() {
	}

	/** The applicant behind this request (security already requires an applicant session). */
	static Caller applicant() {
		Caller caller = CallerContext.require();
		if (caller.kind() != SessionKind.LA_APPLICANT || caller.userId() == null) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this.");
		}
		return caller;
	}

	/** The Eko staff member behind this request (security already requires a staff session). */
	static Caller staff() {
		Caller caller = CallerContext.require();
		if (caller.kind() != SessionKind.STAFF || caller.userId() == null) {
			throw new ApiProblem(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to do this.");
		}
		return caller;
	}

	static UUID applicantId() {
		return applicant().userId();
	}

	/** {@code If-Match: "v14"} → 14. Writes without it get {@code 428 PRECONDITION_REQUIRED}. */
	static long expectedVersion(String ifMatch) {
		if (ifMatch == null || ifMatch.isBlank()) {
			throw new ApiProblem(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
					"Send If-Match with the ETag of the application you are changing");
		}
		String value = ifMatch.trim();
		if (value.startsWith("W/")) {
			value = value.substring(2);
		}
		value = value.replace("\"", "");
		if (value.startsWith("v")) {
			value = value.substring(1);
		}
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException ex) {
			throw new ApiProblem(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED", "If-Match is not a valid ETag");
		}
	}

	static String etag(ApplicationSnapshot snapshot) {
		return "\"v" + snapshot.version() + "\"";
	}

	static ResponseEntity<ApplicationSnapshot> ok(ApplicationSnapshot snapshot) {
		return ResponseEntity.ok().eTag(etag(snapshot)).body(snapshot);
	}

	/** Method, path and body: what makes two requests "the same" for idempotency. */
	static String fingerprint(HttpServletRequest request, Object body) {
		return request.getMethod() + " " + request.getRequestURI() + "\n" + (body == null ? "" : body.toString());
	}
}
