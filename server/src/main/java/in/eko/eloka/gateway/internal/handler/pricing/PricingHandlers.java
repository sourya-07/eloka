package in.eko.eloka.gateway.internal.handler.pricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsDates;
import in.eko.eloka.gateway.internal.report.ReportFormat;
import in.eko.eloka.gateway.internal.report.ReportRenderer;
import in.eko.eloka.gateway.internal.report.ReportTable;
import in.eko.eloka.pricing.CommissionLine;
import in.eko.eloka.pricing.PricingApi;
import in.eko.eloka.pricing.PricingChange;
import in.eko.eloka.pricing.PricingRuleView;
import in.eko.eloka.pricing.PricingScope;
import in.eko.eloka.shared.error.ValidationException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Pricing & Commissions screens (837 tree, 838 set, 1046 current, 756 export) and the agent's commissions. */
final class PricingHandlers {

	private PricingHandlers() {
	}

	/** EPS {@code operation_type}: 3 entire network, 2 a distributor's network, 1 individual, 4 distributor commission. */
	static PricingScope scopeOf(String operationType) {
		return switch (operationType == null ? "3" : operationType) {
			case "3" -> PricingScope.ALL;
			case "2" -> PricingScope.NETWORK;
			case "1", "4" -> PricingScope.USER;
			default -> throw ValidationException.field("operation_type", "Unknown option");
		};
	}

	/** {@code CspList} arrives as "A,B", as a JSON array of codes, or as an array of {value} objects. */
	static List<String> codes(EpsParams p) {
		return p.node("CspList").map(node -> {
			List<String> out = new ArrayList<>();
			if (node.isArray()) {
				node.forEach(item -> out.add(item.isObject() ? item.path("value").asString(item.path("user_code").asString("")) : item.asString()));
			}
			else {
				out.addAll(Arrays.asList(node.asString("").split(",")));
			}
			return out.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
		}).orElse(List.of());
	}

	/** 837: the product tree. */
	@Component
	static class ProductTree implements EpsHandler {

		private final PricingApi pricing;
		private final JsonMapper json;

		ProductTree(PricingApi pricing, JsonMapper json) {
			this.pricing = pricing;
			this.json = json;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(837));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			JsonNode tree = json.readTree(pricing.productTreeJson());
			return EpsEnvelope.ok().data(Map.of("product_list", tree)).build();
		}
	}

	/** 838: set pricing / commission. */
	@Component
	static class SetPricing implements EpsHandler {

		private final PricingApi pricing;

		SetPricing(PricingApi pricing) {
			this.pricing = pricing;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(838));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EpsParams p = call.params();
			PricingScope scope = scopeOf(p.string("operation_type").orElse("3"));
			BigDecimal value = p.decimal("actual_pricing")
				.orElseThrow(() -> ValidationException.field("actual_pricing", "Enter the price"));
			pricing.set(call.caller(), new PricingChange(p.integer("product_id")
				.orElseThrow(() -> ValidationException.field("product_id", "Choose a product")), scope,
					scope == PricingScope.ALL ? List.of() : codes(p), "0".equals(p.string("pricing_type").orElse("1")), value,
					p.decimal("min_slab_amount").orElse(null), p.decimal("max_slab_amount").orElse(null)));
			return EpsEnvelope.ok().message("Pricing updated successfully").build();
		}
	}

	/** 1046: the price currently in force. {@code param_attributes} is top-level: type "1" fixed, "0" percent. */
	@Component
	static class CurrentPricing implements EpsHandler {

		private final PricingApi pricing;

		CurrentPricing(PricingApi pricing) {
			this.pricing = pricing;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(1046));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EpsParams p = call.params();
			PricingScope scope = scopeOf(p.string("operation_type").orElse("3"));
			return pricing.current(call.caller(), p.integer("product_id").orElseThrow(
					() -> ValidationException.field("product_id", "Choose a product")), scope,
					scope == PricingScope.ALL ? List.of() : codes(p), p.decimal("min_slab_amount").orElse(null),
					p.decimal("max_slab_amount").orElse(null))
				.map(v -> EpsEnvelope.ok().put("param_attributes",
						Map.of("type", v.percent() ? "0" : "1", "value", v.value().toPlainString())).build())
				.orElseGet(() -> EpsEnvelope.ok().message("No pricing set yet").put("param_attributes", null).build());
		}
	}

	/** 756: every active rule as a spreadsheet. */
	@Component
	static class ExportPricing implements EpsHandler {

		private final PricingApi pricing;
		private final ReportRenderer reports;

		ExportPricing(PricingApi pricing, ReportRenderer reports) {
			this.pricing = pricing;
			this.reports = reports;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(756));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			List<List<String>> rows = new ArrayList<>();
			for (PricingRuleView r : pricing.activeRules(call.caller())) {
				rows.add(List.of(r.product(), r.distributorCommission() ? "Distributor commission" : "Agent",
						switch (r.scope()) {
							case ALL -> "Entire network";
							case NETWORK -> "Network of " + r.targetUserCode();
							case USER -> r.targetUserCode();
						},
						r.slabMin().signum() == 0 && r.slabMax().signum() == 0 ? "Any amount"
								: r.slabMin().toPlainString() + " - " + r.slabMax().toPlainString(),
						r.value().percent() ? r.value().value().toPlainString() + "%" : "₹" + r.value().value().toPlainString(),
						EpsDates.format(r.setAt())));
			}
			ReportRenderer.Rendered file = reports.render(new ReportTable("Existing pricing",
					List.of("Product", "For", "Applies to", "Slab", "Value", "Set at"), rows), ReportFormat.XLSX, "existing_pricing");
			Map<String, Object> wrapped = new LinkedHashMap<>();
			wrapped.put("blob", java.util.Base64.getEncoder().encodeToString(file.bytes()));
			wrapped.put("name", file.fileName());
			wrapped.put("content-type", file.contentType());
			// 756 reads data.file (not top-level file), unlike the history export.
			return EpsEnvelope.ok().data(Map.of("file", wrapped)).build();
		}
	}

	/** tf GET /network/agent/pricing_commission: what the calling agent earns, per product and slab. */
	@Component
	static class AgentCommissions implements EpsHandler {

		private final PricingApi pricing;

		AgentCommissions(PricingApi pricing) {
			this.pricing = pricing;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf("/ekoicici/v1", HttpMethod.GET, "/network/agent/pricing_commission"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			List<Map<String, Object>> rows = pricing.commissionsFor(call.caller()).stream().map(AgentCommissions::row).toList();
			return EpsEnvelope.ok().data(Map.of("pricing_commission_data", rows)).build();
		}

		private static Map<String, Object> row(CommissionLine line) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("product_id", line.productId());
			row.put("tx_type_id", line.txTypeId());
			row.put("product", line.product());
			boolean anyAmount = line.slabFrom().signum() == 0 && line.slabTo().signum() == 0;
			row.put("slab_from", anyAmount ? null : line.slabFrom().toPlainString());
			row.put("slab_to", anyAmount ? null : line.slabTo().toPlainString());
			row.put("calc_type", line.value().percent() ? 1 : 2); // read side: 1 = percent
			row.put("value", line.value().value().toPlainString());
			row.put("min_value", null);
			row.put("max_value", null);
			return row;
		}
	}
}
