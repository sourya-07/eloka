package in.eko.eloka.gateway.internal.dispatch;

import java.util.List;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;

/**
 * One EPS call (or a small family of identical ones). Strategy pattern: the dispatcher
 * picks the handler by {@link RouteKey}; the handler translates EPS fields into a call on
 * a domain module's public API and maps the result back to the EPS envelope.
 *
 * <p>Rules: never read identity ({@code user_code}, {@code initiator_id}, {@code org_id},
 * {@code bc}) from params; use {@link EpsCall#caller()}.
 */
public interface EpsHandler {

	List<RouteKey> routes();

	/** Who may call this. There is no default, on purpose. */
	AccessPolicy access();

	EpsResponse handle(EpsCall call);
}
