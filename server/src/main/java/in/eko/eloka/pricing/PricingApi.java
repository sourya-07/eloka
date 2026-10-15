package in.eko.eloka.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import in.eko.eloka.shared.security.Caller;

/** Pricing and commissions. Only org admins change pricing; every target must be in the admin's org. */
public interface PricingApi {

	/** The product tree shown on the pricing screens (EPS 837 {@code product_list}), as JSON. */
	String productTreeJson();

	/**
	 * @throws in.eko.eloka.shared.error.ValidationException when the value breaks the slab's limits
	 */
	void set(Caller caller, PricingChange change);

	/** The rule currently in force for this product, slab and target (most specific first). */
	Optional<PricingValue> current(Caller caller, int productId, PricingScope scope, List<String> targetUserCodes,
			BigDecimal slabMin, BigDecimal slabMax);

	List<PricingRuleView> activeRules(Caller caller);

	/** What the calling agent earns/charges per product and slab. */
	List<CommissionLine> commissionsFor(Caller caller);
}
