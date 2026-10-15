package in.eko.eloka.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A transaction to record (import or seed).
 *
 * @param debit true when money left the agent's wallet
 * @param commission what the agent earned on it (may be zero)
 * @param details product-specific extras shown in history (operator, bank, recipient...), no secrets
 */
public record NewTxn(long orgId, UUID userId, String userCode, Long accountId, int txTypeId, String txName, boolean debit,
		BigDecimal amount, BigDecimal fee, BigDecimal commission, TxnStatus status, String customerName,
		String customerMobile, Instant occurredAt, Map<String, String> details) {
}
