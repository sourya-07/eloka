package in.eko.eloka.pricing;

import java.util.List;

/** The product catalog shown as the app's menu. */
public interface CatalogApi {

	/** All products in menu order. */
	List<ProductView> menu(long orgId);

	/** Products whose transactions appear in history (one per transaction type). */
	List<ProductView> historyTypes(long orgId);
}
