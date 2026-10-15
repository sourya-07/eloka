package in.eko.eloka.gateway.internal.dispatch;

import java.util.Optional;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsRequest;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.UnauthenticatedException;
import in.eko.eloka.shared.logging.RequestIdFilter;
import in.eko.eloka.shared.security.Caller;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Finds the handler, enforces its access policy, runs it. */
@Component
public class EpsDispatcher {

	private static final Logger log = LoggerFactory.getLogger(EpsDispatcher.class);

	private final HandlerRegistry registry;

	public EpsDispatcher(HandlerRegistry registry) {
		this.registry = registry;
	}

	public EpsResponse dispatch(EpsRequest request, Optional<Caller> caller, HttpServletRequest servletRequest) {
		Optional<HandlerRegistry.Match> match = registry.find(request);
		if (match.isEmpty()) {
			String what = request.tf().map(tf -> "tf " + tf).or(() -> request.interactionId().map(id -> "#" + id))
				.orElse(request.path());
			servletRequest.setAttribute(RequestIdFilter.ROUTE_ATTRIBUTE, what + " (unsupported)");
			log.warn("Unsupported EPS call {}", what);
			return EpsEnvelope.fail("This feature is not available yet.").type(-2).build();
		}

		HandlerRegistry.Match found = match.get();
		servletRequest.setAttribute(RequestIdFilter.ROUTE_ATTRIBUTE, found.label());
		MDC.put("interaction", found.label());
		switch (found.handler().access().decide(caller)) {
			case UNAUTHENTICATED -> throw new UnauthenticatedException("Your session has expired. Please log in again.");
			case FORBIDDEN -> throw new ForbiddenException("NOT_ALLOWED", "You are not allowed to do this.");
			case ALLOW -> {
			}
		}
		return found.handler().handle(new EpsCall(request, caller));
	}
}
