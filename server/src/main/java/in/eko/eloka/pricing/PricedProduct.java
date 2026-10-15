package in.eko.eloka.pricing;

import java.util.List;

/**
 * Something an admin can set a price/commission for.
 *
 * @param productId id rules are stored against
 * @param label product name, e.g. "Money Transfer"
 * @param distributorCommission true for the distributor-commission form, false for agent pricing
 * @param commission true when the agent value is a commission they earn (else a fee they charge)
 * @param txTypeId transaction type the rule applies to (links to history and the menu)
 */
public record PricedProduct(int productId, String label, boolean distributorCommission, boolean commission,
		int txTypeId, List<Slab> slabs) {
}
