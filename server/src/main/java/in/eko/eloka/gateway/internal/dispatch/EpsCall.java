package in.eko.eloka.gateway.internal.dispatch;

import java.util.List;
import java.util.Optional;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsRequest;
import in.eko.eloka.gateway.internal.envelope.UploadedFile;
import in.eko.eloka.shared.error.UnauthenticatedException;
import in.eko.eloka.shared.security.Caller;

/** Everything a handler gets: the parsed request and the verified caller. */
public final class EpsCall {

	private final EpsRequest request;
	private final Optional<Caller> caller;

	public EpsCall(EpsRequest request, Optional<Caller> caller) {
		this.request = request;
		this.caller = caller;
	}

	public EpsParams params() {
		return request.params();
	}

	public List<UploadedFile> files() {
		return request.files();
	}

	public EpsRequest request() {
		return request;
	}

	/** The caller; guaranteed present for any policy other than PUBLIC. */
	public Caller caller() {
		return caller.orElseThrow(() -> new UnauthenticatedException("Please log in again"));
	}

	public Optional<Caller> optionalCaller() {
		return caller;
	}
}
