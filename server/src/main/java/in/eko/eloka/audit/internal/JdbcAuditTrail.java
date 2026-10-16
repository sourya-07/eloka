package in.eko.eloka.audit.internal;

import java.util.Collection;
import java.util.List;
import in.eko.eloka.audit.AuditTrail;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditTrail implements AuditTrail {

	private final NamedParameterJdbcTemplate jdbc;

	JdbcAuditTrail(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<AuditRecord> forTarget(Collection<String> targetIds, int limit) {
		if (targetIds.isEmpty()) {
			return List.of();
		}
		return jdbc.query("""
				SELECT seq, occurred_at, actor_type, actor_id, action, target_type, target_id, details
				FROM audit_event WHERE target_id IN (:ids) ORDER BY seq LIMIT :limit""",
				new MapSqlParameterSource("ids", targetIds).addValue("limit", Math.max(1, Math.min(limit, 1000))),
				(rs, row) -> new AuditRecord(rs.getLong("seq"), rs.getTimestamp("occurred_at").toInstant(),
						rs.getString("actor_type"), rs.getString("actor_id"), rs.getString("action"),
						rs.getString("target_type"), rs.getString("target_id"), rs.getString("details")));
	}
}
