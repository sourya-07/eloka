package in.eko.eloka.gateway.internal.web;

import java.util.LinkedHashMap;
import java.util.Map;
import in.eko.eloka.shared.error.DomainException;
import in.eko.eloka.shared.error.RateLimitedException;
import in.eko.eloka.shared.error.UnauthenticatedException;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions from EPS calls to the EPS envelope. Business failures stay HTTP 200 (the
 * client reads {@code status}); a missing session is 401; bugs are 500 with the request id.
 */
@RestControllerAdvice(assignableTypes = EpsGatewayController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class EpsGatewayExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(EpsGatewayExceptionHandler.class);

	/** EPS response type ids the client recognises for specific failures. */
	private static final Map<String, Integer> RESPONSE_TYPES = Map.of("OTP_LIMIT_REACHED", 2377);

	@ExceptionHandler(UnauthenticatedException.class)
	ResponseEntity<Map<String, Object>> unauthenticated(UnauthenticatedException ex, HttpServletRequest request) {
		return respond(401, envelope(ex.getMessage(), -1), request);
	}

	@ExceptionHandler(DomainException.class)
	ResponseEntity<Map<String, Object>> business(DomainException ex, HttpServletRequest request) {
		Map<String, Object> body = envelope(ex.getMessage(), RESPONSE_TYPES.getOrDefault(ex.getCode(), -1));
		body.put("error_code", ex.getCode());
		if (ex instanceof ValidationException validation) {
			body.put("invalid_params", validation.getInvalidParams());
		}
		if (ex instanceof RateLimitedException limited) {
			long minutes = Math.max(1, (limited.getRetryAfter().toSeconds() + 59) / 60);
			body.put("data", Map.of("description", Long.toString(minutes)));
		}
		log.info("EPS call failed: {} ({})", ex.getCode(), ex.getMessage());
		return respond(200, body, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error in EPS call", ex);
		Map<String, Object> body = envelope("Something went wrong. Please try again. Ref: " + MDC.get("request_id"), -1);
		return respond(500, body, request);
	}

	private static Map<String, Object> envelope(String message, int responseType) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", 1);
		body.put("response_status_id", 1);
		body.put("response_type_id", responseType);
		body.put("message", message);
		body.put("data", Map.of());
		return body;
	}

	private static ResponseEntity<Map<String, Object>> respond(int status, Map<String, Object> body,
			HttpServletRequest request) {
		request.setAttribute(RequestIdFilter.OUTCOME_ATTRIBUTE, 1);
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
