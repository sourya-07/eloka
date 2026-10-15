package in.eko.eloka.gateway.internal.handler.home;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.pricing.CatalogApi;
import in.eko.eloka.pricing.ProductView;
import org.springframework.stereotype.Component;

/**
 * {@code /transactions/wlc}: the product menu. Must be a bare JSON array (the client treats
 * anything else as "no menu" and some cards then fail).
 */
@Component
class MenuHandler implements EpsHandler {

	private final CatalogApi catalog;

	MenuHandler(CatalogApi catalog) {
		this.catalog = catalog;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.path("/transactions/wlc"));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.SESSION;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		List<Map<String, Object>> items = catalog.menu(call.caller().orgId()).stream().map(MenuHandler::item).toList();
		return EpsResponse.Json.ok(items);
	}

	private static Map<String, Object> item(ProductView p) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("id", p.id());
		item.put("label", p.label());
		item.put("icon", p.icon());
		item.put("behavior", p.behavior());
		item.put("is_visible", p.visible() ? 1 : 0);
		item.put("category", p.category());
		item.put("description", p.description());
		item.put("imp", p.important());
		if (p.isHistoryType()) {
			item.put("history_label", p.historyLabel());
			item.put("interaction_type_id", p.txTypeId());
		}
		if (p.groupInteractionIds() != null) {
			item.put("group_interaction_ids", p.groupInteractionIds());
		}
		return item;
	}
}
