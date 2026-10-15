package in.eko.eloka.pricing;

import java.math.BigDecimal;

/**
 * A transaction-amount band with the pricing limits allowed in it.
 *
 * @param percentMin lowest allowed percentage, or null when percentage pricing is not allowed
 * @param fixedMin lowest allowed fixed amount, or null when fixed pricing is not allowed
 */
public record Slab(BigDecimal min, BigDecimal max, BigDecimal percentMin, BigDecimal percentMax, BigDecimal fixedMin,
		BigDecimal fixedMax) {

	public boolean allowsPercent() {
		return percentMin != null || percentMax != null;
	}

	public boolean allowsFixed() {
		return fixedMin != null || fixedMax != null;
	}

	public boolean matches(BigDecimal from, BigDecimal to) {
		return min.compareTo(from) == 0 && max.compareTo(to) == 0;
	}
}
