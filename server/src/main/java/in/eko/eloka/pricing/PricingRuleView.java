package in.eko.eloka.pricing;

import java.math.BigDecimal;
import java.time.Instant;

/** An active pricing rule (for the export). */
public record PricingRuleView(int productId, String product, boolean distributorCommission, PricingScope scope,
		String targetUserCode, PricingValue value, BigDecimal slabMin, BigDecimal slabMax, Instant setAt) {
}
