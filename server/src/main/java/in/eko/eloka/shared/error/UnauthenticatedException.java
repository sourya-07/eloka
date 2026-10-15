package in.eko.eloka.shared.error;

import org.springframework.http.HttpStatus;

/** No valid session. */
public class UnauthenticatedException extends DomainException {

	public UnauthenticatedException(String message) {
		super("UNAUTHENTICATED", message);
	}

	@Override
	public HttpStatus getStatus() {
		return HttpStatus.UNAUTHORIZED;
	}
}
