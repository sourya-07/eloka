package in.eko.eloka.gateway.internal.dispatch;

import java.util.Optional;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import in.eko.eloka.shared.security.SessionKind;

/** Who may call a handler. Checked by the dispatcher before the handler runs. */
public enum AccessPolicy {

	/** No session needed (login, org lookup). */
	PUBLIC,
	/** Any EPS session, including a verified mobile that has no account yet. */
	SESSION,
	/** A registered user of any role. */
	USER,
	/** Distributors and org admins. */
	DISTRIBUTOR_OR_ADMIN,
	/** Org admins only. */
	ORG_ADMIN;

	public enum Decision {
		ALLOW,
		UNAUTHENTICATED,
		FORBIDDEN
	}

	public Decision decide(Optional<Caller> caller) {
		if (this == PUBLIC) {
			return Decision.ALLOW;
		}
		if (caller.isEmpty()) {
			return Decision.UNAUTHENTICATED;
		}
		Caller c = caller.get();
		boolean epsSession = c.kind() == SessionKind.USER || c.kind() == SessionKind.PRE_REGISTRATION;
		boolean allowed = switch (this) {
			case PUBLIC -> true;
			case SESSION -> epsSession;
			case USER -> c.isRegisteredUser();
			case DISTRIBUTOR_OR_ADMIN -> c.isRegisteredUser() && (c.role() == Role.DISTRIBUTOR || c.role() == Role.ORG_ADMIN);
			case ORG_ADMIN -> c.isRegisteredUser() && c.role() == Role.ORG_ADMIN;
		};
		return allowed ? Decision.ALLOW : Decision.FORBIDDEN;
	}
}
