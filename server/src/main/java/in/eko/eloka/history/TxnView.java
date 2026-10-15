package in.eko.eloka.history;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** A recorded transaction. Contains a decrypted customer mobile: never log it. */
public record TxnView(long tid, long orgId, UUID userId, String userCode, Long accountId, int txTypeId, String txName,
		boolean debit, BigDecimal amount, BigDecimal fee, BigDecimal commission, TxnStatus status, String customerName,
		String customerMobile, Instant occurredAt, Map<String, String> details) {
}
