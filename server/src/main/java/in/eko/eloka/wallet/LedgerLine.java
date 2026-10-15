package in.eko.eloka.wallet;

import java.math.BigDecimal;
import java.time.Instant;

/** One statement line. {@code reference} is the movement's unique reference (shown as the transaction id). */
public record LedgerLine(String reference, Instant postedAt, String narration, BigDecimal amount, boolean credit,
		BigDecimal balanceAfter) {
}
