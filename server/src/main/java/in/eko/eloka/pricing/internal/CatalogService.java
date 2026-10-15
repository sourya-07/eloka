package in.eko.eloka.pricing.internal;

import java.util.List;
import in.eko.eloka.pricing.CatalogApi;
import in.eko.eloka.pricing.ProductView;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

interface ProductRepository extends JpaRepository<Product, Integer> {
}

/** The same catalog for every org today; per-org enablement can be layered on later. */
@Service
@Transactional(readOnly = true)
class CatalogService implements CatalogApi {

	private final ProductRepository products;

	CatalogService(ProductRepository products) {
		this.products = products;
	}

	@Override
	public List<ProductView> menu(long orgId) {
		return products.findAll(Sort.by("sortOrder", "id")).stream().map(CatalogService::toView).toList();
	}

	@Override
	public List<ProductView> historyTypes(long orgId) {
		return menu(orgId).stream().filter(ProductView::isHistoryType).toList();
	}

	private static ProductView toView(Product p) {
		return new ProductView(p.getId(), p.getLabel(), p.getIcon(), p.getBehavior(), p.isVisible(), p.getCategory(),
				p.getDescription(), p.getTxTypeId(), p.getHistoryLabel(), p.getGroupInteractionIds(),
				p.isImportant() ? "1" : "0");
	}
}
