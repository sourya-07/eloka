package in.eko.eloka.history;

import java.math.BigDecimal;

/** Commission earned: this month till yesterday, last month, the month before, and not yet paid. */
public record EarningsSummary(BigDecimal thisMonth, BigDecimal lastMonth, BigDecimal previousMonth,
		BigDecimal commissionDue) {
}
