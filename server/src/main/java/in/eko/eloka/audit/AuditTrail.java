package in.eko.eloka.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Read side of the audit log (S21): the events about one target, oldest first. */
public interface AuditTrail {

	/** @param targetIds every id the target is recorded under (e.g. a public id and the row id) */
	List<AuditRecord> forTarget(Collection<String> targetIds, int limit);

	/** @param details the recorded key/value details as stored (ids and codes only, never personal data) */
	record AuditRecord(long seq, Instant occurredAt, String actorType, String actorId, String action, String targetType,
			String targetId, String details) {
	}
}
