package in.eko.eloka.history;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business numbers for a period, within one scope (whole org or a set of users).
 *
 * @param gtv value of successful transactions
 * @param revenue fees charged on successful transactions
 * @param activeAgents distinct users with at least one successful transaction
 * @param byType per transaction type
 * @param topUsers per user, highest value first (at most 50)
 */
public record DashboardStats(BigDecimal gtv, BigDecimal revenue, long transactions, long successful, long failed,
		long pending, long activeAgents, Map<Integer, TypeStats> byType, List<UserStats> topUsers) {

	public record TypeStats(long count, long successCount, BigDecimal amount, BigDecimal revenue) {
	}

	public record UserStats(UUID userId, String userCode, BigDecimal gtv, BigDecimal revenue, long count, long pending) {
	}
}
