package in.eko.eloka.pricing;

/** Who a pricing rule applies to. EPS {@code operation_type}: 3 = ALL, 2 = NETWORK, 1 / 4 = USER. */
public enum PricingScope {
	/** Everyone in the org. */
	ALL,
	/** Everyone in a distributor's network (the target is the distributor). */
	NETWORK,
	/** One user. */
	USER
}
