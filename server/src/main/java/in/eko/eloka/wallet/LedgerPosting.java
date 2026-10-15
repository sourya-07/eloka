package in.eko.eloka.wallet;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One movement on a user's E-value wallet.
 *
 * @param credit true adds money, false takes it
 * @param reference unique per account; posting the same reference twice is a no-op (idempotent)
 * @param narration short description shown in statements
 */
public record LedgerPosting(long orgId, UUID userId, boolean credit, BigDecimal amount, String reference,
		String narration) {
}
