package in.eko.eloka.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated party behind a request. Built only from a verified session, so every
 * field can be trusted. Handlers must use this instead of identity fields in request bodies.
 *
 * @param sessionId session row id (safe to log as a short reference)
 * @param kind what the session is for
 * @param orgId tenant the caller belongs to; every data access is scoped to it
 * @param userId account id, or {@code null} before registration
 * @param userCode public account code (EPS "user_code"), or {@code null}
 * @param mobile verified 10-digit mobile
 * @param role business role
 * @param permissions staff permissions (empty for other kinds)
 */
public record Caller(UUID sessionId, SessionKind kind, long orgId, UUID userId, String userCode, String mobile, Role role,
		Set<String> permissions) {

	public Caller {
		permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
	}

	public boolean hasPermission(String permission) {
		return permissions.contains(permission);
	}

	public boolean isRegisteredUser() {
		return kind == SessionKind.USER && userId != null;
	}

	/** Short, non-secret reference for logs. */
	public String sessionRef() {
		return sessionId.toString().substring(0, 8);
	}
}
