package in.eko.eloka.audit.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.TreeMap;
import in.eko.eloka.audit.AuditEntry;
import in.eko.eloka.audit.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores each entry with {@code hash = SHA-256(prev_hash ‖ canonical entry)}, so editing or
 * deleting any row breaks the chain from that point on. Also mirrors the entry to the
 * {@code eloka.audit} log file.
 */
@Service
class DatabaseAuditLog implements AuditLog {

	private static final Logger auditLog = LoggerFactory.getLogger("eloka.audit");

	private final AuditEventRepository events;
	private final AuditChainHeadRepository heads;
	private final JsonMapper json;
	private final Clock clock;

	DatabaseAuditLog(AuditEventRepository events, AuditChainHeadRepository heads, JsonMapper json, Clock clock) {
		this.events = events;
		this.heads = heads;
		this.json = json;
		this.clock = clock;
	}

	@Override
	@Transactional
	public void record(AuditEntry entry) {
		Instant now = Instant.now(clock);
		String details = json.writeValueAsString(new TreeMap<>(entry.details()));
		AuditChainHead head = heads.lockHead();
		long seq = head.lastSeq() + 1;
		String canonical = String.join("|", Long.toString(seq), now.toString(), String.valueOf(entry.orgId()),
				entry.actorType(), String.valueOf(entry.actorId()), entry.action(), String.valueOf(entry.targetType()),
				String.valueOf(entry.targetId()), details);
		byte[] hash = sha256(head.lastHash(), canonical);

		events.save(new AuditEvent(seq, now, entry.orgId(), entry.actorType(), entry.actorId(), entry.action(),
				entry.targetType(), entry.targetId(), details, head.lastHash(), hash));
		head.advance(seq, hash);

		auditLog.info("seq={} action={} actor={}:{} org={} target={}:{} details={}", seq, entry.action(),
				entry.actorType(), entry.actorId(), entry.orgId(), entry.targetType(), entry.targetId(), details);
	}

	private static byte[] sha256(byte[] previous, String canonical) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(previous);
			digest.update(canonical.getBytes(StandardCharsets.UTF_8));
			return digest.digest();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
