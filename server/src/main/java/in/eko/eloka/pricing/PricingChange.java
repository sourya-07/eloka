package in.eko.eloka.pricing;

import java.math.BigDecimal;
import java.util.List;

/**
 * A price/commission an admin sets.
 *
 * @param targetUserCodes users the rule is for (empty for {@link PricingScope#ALL})
 * @param percent true = percentage of the transaction amount, false = fixed ₹ amount
 * @param slabMin lower bound of the slab, or null when the product has no slabs
 */
public record PricingChange(int productId, PricingScope scope, List<String> targetUserCodes, boolean percent,
		BigDecimal value, BigDecimal slabMin, BigDecimal slabMax) {
}
