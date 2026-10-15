package in.eko.eloka.pricing;

import java.math.BigDecimal;

/** One line of "Know your commission" for an agent. */
public record CommissionLine(int productId, int txTypeId, String product, BigDecimal slabFrom, BigDecimal slabTo,
		PricingValue value) {
}
