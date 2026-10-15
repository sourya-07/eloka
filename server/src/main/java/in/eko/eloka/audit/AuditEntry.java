package in.eko.eloka.audit;

import java.util.Map;

/**
 * One audited action. {@code details} must never contain personal data (no mobiles,
 * names, PAN); use ids and codes.
 *
 * @param action what happened, e.g. {@code LOGIN_SUCCEEDED}
 * @param actorType {@code USER}, {@code STAFF}, {@code SYSTEM} or {@code ANONYMOUS}
 * @param actorId user/staff id or {@code null}
 * @param orgId tenant, or {@code null} for platform-level events
 * @param targetType kind of thing acted on, e.g. {@code ORG}
 * @param targetId its id
 * @param details small, non-personal key/values
 */
public record AuditEntry(String action, String actorType, String actorId, Long orgId, String targetType,
		String targetId, Map<String, String> details) {

	public AuditEntry {
		details = details == null ? Map.of() : Map.copyOf(details);
	}
}
