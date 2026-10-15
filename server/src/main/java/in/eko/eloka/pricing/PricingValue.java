package in.eko.eloka.pricing;

import java.math.BigDecimal;

/** A price/commission: percentage or fixed amount. */
public record PricingValue(boolean percent, BigDecimal value) {
}
