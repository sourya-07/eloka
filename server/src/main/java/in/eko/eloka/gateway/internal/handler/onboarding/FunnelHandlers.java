package in.eko.eloka.gateway.internal.handler.onboarding;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import in.eko.eloka.agentonboarding.AgentOnboardingApi;
import in.eko.eloka.agentonboarding.FunnelStage;
import in.eko.eloka.agentonboarding.OnboardingStatus;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.security.Caller;
import org.springframework.stereotype.Component;

/** Admin dashboard → Onboarding tab: who is still onboarding, grouped by stage. */
final class FunnelHandlers {

	private FunnelHandlers() {
	}

	/** In-progress onboardings within the caller's network. */
	@Component
	static class Funnel {

		private final AgentOnboardingApi onboarding;
		private final NetworkQueries network;

		Funnel(AgentOnboardingApi onboarding, NetworkQueries network) {
			this.onboarding = onboarding;
			this.network = network;
		}

		Map<UUID, OnboardingStatus> inScope(Caller caller) {
			Map<UUID, OnboardingStatus> all = onboarding.inProgress(caller.orgId());
			List<UUID> scope = network.userIdsInScope(caller);
			if (scope == null) {
				return all;
			}
			Set<UUID> allowed = new HashSet<>(scope);
			return all.entrySet().stream().filter(e -> allowed.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}
	}

	/** 683: one card per stage with its count; {@code totalRecords} = everyone in progress. */
	@Component
	static class Stages implements EpsHandler {

		private final Funnel funnel;

		Stages(Funnel funnel) {
			this.funnel = funnel;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(683));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Map<UUID, OnboardingStatus> inProgress = funnel.inScope(call.caller());
			Map<FunnelStage, Long> counts = new EnumMap<>(FunnelStage.class);
			inProgress.values().forEach(status -> counts.merge(FunnelStage.of(status.current()), 1L, Long::sum));
			List<Map<String, Object>> cards = Arrays.stream(FunnelStage.values())
				.map(stage -> Map.<String, Object>of("label", stage.label(), "value", counts.getOrDefault(stage, 0L),
						"status_ids", Integer.toString(stage.id())))
				.toList();
			return EpsEnvelope.ok().data(Map.of("totalRecords", inProgress.size(), "onboarding_funnel", cards)).build();
		}
	}

	/** 727: the agents in the selected stages ({@code account_status} = stage ids), paged. */
	@Component
	static class Agents implements EpsHandler {

		private static final DateTimeFormatter ONBOARDED_ON = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

		private final Funnel funnel;
		private final NetworkQueries network;

		Agents(Funnel funnel, NetworkQueries network) {
			this.funnel = funnel;
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(727));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			Set<Integer> stageIds = Arrays.stream(call.params().string("account_status").orElse("").split(","))
				.map(String::trim).filter(v -> v.matches("\\d+")).map(Integer::valueOf).collect(Collectors.toSet());
			int size = Math.max(1, Math.min(call.params().integer("record_count", 10), 100));
			int page = Math.max(1, call.params().integer("page_number", 1));

			Map<UUID, OnboardingStatus> matching = funnel.inScope(caller).entrySet().stream()
				.filter(e -> stageIds.isEmpty() || stageIds.contains(FunnelStage.of(e.getValue().current()).id()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			List<UserView> agents = new ArrayList<>(network.byIds(caller.orgId(), matching.keySet()).values());
			agents.sort(Comparator.comparing(UserView::dateOfJoining).reversed().thenComparing(UserView::name));

			List<Map<String, Object>> rows = agents.stream().skip((long) (page - 1) * size).limit(size).map(user -> {
				ShopView shop = network.shopOf(caller, user.id());
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("name", user.name());
				row.put("mobile", user.mobile());
				row.put("user_code", user.userCode());
				row.put("agent_type", EpsValues.typeLabel(user.userType()));
				row.put("account_status_desc", FunnelStage.of(matching.get(user.id()).current()).label());
				row.put("current_step", matching.get(user.id()).current().label());
				row.put("last_updated", user.dateOfJoining() + " 00:00:00");
				row.put("business_type", shop.shopType());
				row.put("location", shop.location());
				row.put("onboardedOn", user.dateOfJoining().format(ONBOARDED_ON));
				return row;
			}).toList();
			return EpsEnvelope.ok().data(Map.of("totalRecords", agents.size(), "onboarding_funnel", rows)).build();
		}
	}

	/** 792: download an agent's signed agreement. Needs a real e-sign provider, which keeps the signed PDF. */
	@Component
	static class AgreementDownload implements EpsHandler {

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(792));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			return EpsEnvelope.fail("The signed agreement copy is not available yet.").build();
		}
	}
}
