package in.eko.eloka.shared.web;

import in.eko.eloka.shared.error.ApiProblem;
import in.eko.eloka.shared.error.DomainException;
import in.eko.eloka.shared.error.RateLimitedException;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns exceptions into RFC 9457 {@code application/problem+json} responses.
 * Framework errors (validation, 404, 405) are handled by the parent class.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(DomainException.class)
	ProblemDetail handleDomainException(DomainException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setProperty("code", ex.getCode());
		if (ex instanceof ValidationException validation && !validation.getInvalidParams().isEmpty()) {
			problem.setProperty("invalid_params", validation.getInvalidParams());
		}
		if (ex instanceof ApiProblem apiProblem) {
			apiProblem.getProperties().forEach(problem::setProperty);
		}
		if (ex instanceof RateLimitedException limited && limited.getRetryAfter() != null) {
			problem.setProperty("retry_after", limited.getRetryAfter().toSeconds());
		}
		return problem;
	}
}
