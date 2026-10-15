package in.eko.eloka.history;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public API of the history module. All reads are scoped to one org (and usually one user). */
public interface HistoryApi {

	TxnView record(NewTxn txn);

	/** Newest first. */
	List<TxnView> recentFor(long orgId, UUID userId, int offset, int limit);

	EarningsSummary earnings(long orgId, UUID userId);

	/**
	 * Newest first.
	 *
	 * @param userIds users whose transactions to include, or {@code null} for the whole org
	 */
	List<TxnView> search(long orgId, Collection<UUID> userIds, HistoryFilter filter, int offset, int limit);

	/**
	 * @param userIds users in scope, or {@code null} for the whole org
	 * @param txTypeId only this transaction type, or {@code null} for all
	 */
	DashboardStats dashboard(long orgId, Collection<UUID> userIds, Instant from, Instant to, Integer txTypeId);
}
