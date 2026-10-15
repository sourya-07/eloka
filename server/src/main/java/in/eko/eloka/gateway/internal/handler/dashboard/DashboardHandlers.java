package in.eko.eloka.gateway.internal.handler.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsDates;
import in.eko.eloka.history.DashboardStats;
import in.eko.eloka.history.HistoryApi;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserDirectory;
import in.eko.eloka.network.UserView;
import in.eko.eloka.pricing.CatalogApi;
import in.eko.eloka.pricing.ProductView;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Admin / distributor dashboard calls. All numbers are scoped to the caller's network. */
final class DashboardHandlers {

	private DashboardHandlers() {
	}

	/** 818: active vs total users per user type. Retailers get an empty map. */
	@Component
	static class ActiveUsers implements EpsHandler {

		private final NetworkQueries network;

		ActiveUsers(NetworkQueries network) {
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(818));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Map<String, Object> totals = new LinkedHashMap<>();
			network.activeCountsByType(call.caller()).forEach((type, count) -> totals.put(type.toString(),
					Map.of("activecount", Long.toString(count.active()), "totalcount", Long.toString(count.total()))));
			return EpsEnvelope.ok().data(Map.of("dashboard_object", Map.of("totalActiveData", totals))).build();
		}
	}

	/** 1044: products that appear in history, for dashboard filters and labels. */
	@Component
	static class DashboardProducts implements EpsHandler {

		private final CatalogApi catalog;

		DashboardProducts(CatalogApi catalog) {
			this.catalog = catalog;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(1044));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			return EpsEnvelope.list(catalog.historyTypes(call.caller().orgId()).stream()
				.map(p -> Map.of("label", p.historyLabel(), "tx_typeid", Integer.toString(p.txTypeId())))
				.toList());
		}
	}

	/**
	 * 682: business dashboard. Each widget asks for one key of {@code requestPayload}
	 * ({@code products_overview}, {@code success_rate}, {@code most_used_services},
	 * {@code verification_trends}, {@code gtv_top_merchants}) with {@code datefrom/dateto}.
	 */
	@Component
	static class BusinessDashboard implements EpsHandler {

		private final HistoryApi history;
		private final NetworkQueries network;
		private final UserDirectory users;
		private final CatalogApi catalog;

		BusinessDashboard(HistoryApi history, NetworkQueries network, UserDirectory users, CatalogApi catalog) {
			this.history = history;
			this.network = network;
			this.users = users;
			this.catalog = catalog;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(682));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			JsonNode payload = call.params().node("requestPayload")
				.orElseThrow(() -> ValidationException.field("requestPayload", "requestPayload is required"));
			Map<String, Object> result = new LinkedHashMap<>();
			for (Map.Entry<String, JsonNode> entry : payload.properties()) {
				JsonNode range = entry.getValue();
				Instant from = EpsDates.parse("datefrom", range.path("datefrom").asString(""));
				Instant to = EpsDates.parse("dateto", range.path("dateto").asString("")).plusSeconds(1);
				String type = range.path("typeid").asString("");
				Integer txType = type.isBlank() ? null : Integer.valueOf(type);
				DashboardStats stats = history.dashboard(caller.orgId(), network.userIdsInScope(caller), from, to, txType);
				switch (entry.getKey()) {
					case "products_overview" -> result.put("products_overview", overview(caller, stats));
					case "success_rate" -> result.put("successRate", successRate(stats));
					case "most_used_services" -> result.put("mostUsedServices", mostUsed(stats));
					case "verification_trends" -> result.put("verificationTrends", List.of()); // no verification products yet
					case "gtv_top_merchants" -> result.put("gtv_top_merchants", topMerchants(caller, stats));
					default -> throw ValidationException.field("requestPayload", "Unknown dashboard widget " + entry.getKey());
				}
			}
			return EpsEnvelope.ok().data(Map.of("dashboard_object", result)).build();
		}

		private Map<String, Object> overview(Caller caller, DashboardStats s) {
			Map<Integer, String> names = catalog.historyTypes(caller.orgId()).stream()
				.collect(Collectors.toMap(ProductView::txTypeId, ProductView::historyLabel, (a, b) -> a));
			Map<String, Object> breakdown = new LinkedHashMap<>();
			s.byType().forEach((type, t) -> {
				if (t.amount().signum() > 0) {
					breakdown.put(type.toString(), Map.of("name", names.getOrDefault(type, "Type " + type), "amount", t.amount()));
				}
			});
			BigDecimal average = s.successful() == 0 ? BigDecimal.ZERO
					: s.revenue().divide(BigDecimal.valueOf(s.successful()), 2, RoundingMode.HALF_UP);
			Map<String, Object> gtv = new LinkedHashMap<>();
			gtv.put("amount", s.gtv());
			gtv.put("revenue", s.revenue());
			gtv.put("averageRevenue", average);
			gtv.put("typeBreakdown", breakdown);
			Map<String, Object> overview = new LinkedHashMap<>();
			overview.put("gtv", gtv);
			overview.put("transactions", Map.of("transactions", s.transactions()));
			overview.put("successCases", Map.of("successCases", s.successful()));
			overview.put("failedCases", Map.of("failedCases", s.failed()));
			overview.put("activeAgents", Map.of("active", s.activeAgents()));
			overview.put("raCases", Map.of("raCases", s.pending()));
			overview.put("commissionDue", Map.of("commissionDue", 0));
			return overview;
		}

		private static Map<String, Object> successRate(DashboardStats s) {
			Map<String, Object> rate = new LinkedHashMap<>();
			s.byType().forEach((type, t) -> rate.put(type.toString(),
					Map.of("successCount", t.successCount(), "totalCount", t.count())));
			return rate;
		}

		private static Map<String, Object> mostUsed(DashboardStats s) {
			Map<String, Object> used = new LinkedHashMap<>();
			s.byType().forEach((type, t) -> used.put(type.toString(),
					Map.of("totalCount", t.count(), "totalRevenue", t.revenue())));
			return used;
		}

		private List<Map<String, Object>> topMerchants(Caller caller, DashboardStats s) {
			List<Map<String, Object>> rows = new ArrayList<>();
			Map<java.util.UUID, String> parentNames = new LinkedHashMap<>();
			for (DashboardStats.UserStats u : s.topUsers().stream().limit(20).toList()) {
				UserView user = users.getById(caller.orgId(), u.userId());
				String parent = user.parentUserId() == null ? "" : parentNames.computeIfAbsent(user.parentUserId(),
						id -> users.getById(caller.orgId(), id).name());
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("usercode", u.userCode());
				row.put("name", user.name());
				row.put("status", user.isActive() ? "Active" : "Inactive");
				row.put("gtv", u.gtv());
				row.put("revenue", u.revenue());
				row.put("totalTransactions", u.count());
				row.put("raCases", u.pending());
				row.put("onboardingDate", user.dateOfJoining() + " 00:00:00");
				row.put("distributorMapped", parent);
				row.put("cellNumber", user.mobile());
				rows.add(row);
			}
			return rows;
		}
	}

	/** 817: users who joined in the period, per user type ({@code requestPayload.Top_panel}). */
	@Component
	static class OnboardedCounts implements EpsHandler {

		private final NetworkQueries network;

		OnboardedCounts(NetworkQueries network) {
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(817));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			JsonNode panel = call.params().node("requestPayload").map(p -> p.path("Top_panel"))
				.orElseThrow(() -> ValidationException.field("requestPayload", "requestPayload.Top_panel is required"));
			var from = EpsDates.localDate(EpsDates.parse("datefrom", panel.path("datefrom").asString("")));
			var to = EpsDates.localDate(EpsDates.parse("dateto", panel.path("dateto").asString("")));
			Map<String, Object> counts = new LinkedHashMap<>();
			network.joinedByType(call.caller(), from, to)
				.forEach((type, n) -> counts.put(type.toString(), Map.of("totalCount", Long.toString(n))));
			return EpsEnvelope.ok().data(Map.of("onboarding_funnel", List.of(counts))).build();
		}
	}
}
