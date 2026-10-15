package in.eko.eloka.pricing;

/**
 * One catalog entry, in the shape of an EPS menu item.
 *
 * @param id menu/route id ({@code /transaction/<id>})
 * @param behavior 1 normal, 6 group, 7 grid (children in {@code groupInteractionIds}), 10 redirect
 * @param visible false keeps it routable (e.g. as a group child) but out of the menu
 * @param txTypeId transaction type recorded in history for this product, or 0
 * @param historyLabel name in history; with {@code txTypeId > 0} it makes the product a history type
 * @param groupInteractionIds comma-separated child ids, or null
 * @param important "1" pins it to the home grid
 */
public record ProductView(int id, String label, String icon, int behavior, boolean visible, String category,
		String description, int txTypeId, String historyLabel, String groupInteractionIds, String important) {

	/** True when transactions of this product appear in history and dashboards. */
	public boolean isHistoryType() {
		return txTypeId > 0 && historyLabel != null && !historyLabel.isBlank();
	}
}
