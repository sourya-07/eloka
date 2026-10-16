package in.eko.eloka.gateway.internal.handler.network;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.network.NetworkCommands;
import in.eko.eloka.network.NetworkFilter;
import in.eko.eloka.network.NetworkPage;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.PersonalInfo;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserStatus;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.error.ValidationException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.wallet.WalletAccountView;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/** "My Network" screens: list, agent profile, status, role changes, edits. */
final class NetworkHandlers {

	static final String V1 = "/ekoicici/v1";

	private NetworkHandlers() {
	}

	/** Shared helper: E-value balance of a user (0 when none). */
	@Component
	static class Balances {

		private final WalletApi wallets;

		Balances(WalletApi wallets) {
			this.wallets = wallets;
		}

		BigDecimal of(UserView user) {
			return wallets.accountsOf(user.orgId(), user.id()).stream()
				.filter(a -> a.typeId() == WalletAccountView.TYPE_EVALUE)
				.map(WalletAccountView::balance)
				.findFirst()
				.orElse(BigDecimal.ZERO);
		}
	}

	/**
	 * tf GET /network/agents: the paged network list, or (with {@code search_value}) one agent's
	 * profile answered with {@code response_type_id 1827}.
	 */
	@Component
	static class Agents implements EpsHandler {

		static final int AGENT_FOUND = 1827;

		private final NetworkQueries network;
		private final Balances balances;

		Agents(NetworkQueries network, Balances balances) {
			this.network = network;
			this.balances = balances;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.GET, "/network/agents"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			EpsParams params = call.params();
			if (params.string("search_value").isPresent()) {
				UserView user = network.findInScope(caller, params.requireString("search_value"));
				Map<String, Object> data = new LinkedHashMap<>();
				data.put("totalRecords", 1);
				data.put("agent_details", List.of(AgentRows.detail(user, network.shopOf(caller, user.id()), balances.of(user))));
				if (params.string("document").isPresent()) {
					// KYC images are not served yet: the profile shows no documents.
					data.put("cspDetails", new LinkedHashMap<>(Map.of()));
				}
				return EpsEnvelope.ok().type(AGENT_FOUND).data(data).build();
			}
			NetworkFilter filter = filterOf(params);
			NetworkPage page = network.search(caller, filter, params.integer("page_number", 1), params.integer("record_count", 10));
			List<Map<String, Object>> rows = page.users().stream()
				.map(u -> AgentRows.listRow(u, network.shopOf(caller, u.id()), balances.of(u)))
				.toList();
			return EpsEnvelope.ok().data(Map.of("totalRecords", page.total(), "agent_details", rows)).build();
		}
	}

	/** Builds the list filter from the EPS query fields. Shared with the network export. */
	static NetworkFilter filterOf(EpsParams params) {
		return new NetworkFilter(params.integer("agentType").orElse(null), params.string("agentAccountStatus").orElse(null),
				params.string("parent_user_code").orElse(null),
				params.string("onBoardingDateFrom").map(v -> EpsValues.date("onBoardingDateFrom", v)).orElse(null),
				params.string("onBoardingDateTo").map(v -> EpsValues.date("onBoardingDateTo", v)).orElse(null), null);
	}

	/** tf GET /network/agent-list: every user in scope (optionally by type / under one user). */
	@Component
	static class AgentList implements EpsHandler {

		private final NetworkQueries network;

		AgentList(NetworkQueries network) {
			this.network = network;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.GET, "/network/agent-list"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			Integer type = call.params().integer("usertype").orElse(null);
			List<UserView> members = call.params().string("user_id")
				.map(parent -> network.search(caller, new NetworkFilter(type, null, parent, null, null, null), 1, 100).users())
				.orElseGet(() -> network.members(caller, type));
			Map<UUID, UserView> parents = network.byIds(caller.orgId(),
					members.stream().map(UserView::parentUserId).filter(java.util.Objects::nonNull).distinct().toList());
			List<Map<String, Object>> list = members.stream().map(u -> {
				Map<String, Object> row = new LinkedHashMap<>();
				row.put("user_code", u.userCode());
				UserView parent = u.parentUserId() == null ? null : parents.get(u.parentUserId());
				row.put("parent_user_code", parent == null ? "root" : parent.userCode());
				row.put("user_type_id", u.userType());
				row.put("name", u.name());
				row.put("mobile", u.mobile());
				row.put("account_status", EpsValues.statusLabel(u));
				row.put("fos_user_code", null);
				return row;
			}).toList();
			return EpsEnvelope.ok().data(Map.of("csp_list", list)).build();
		}
	}

	/** tf POST /network/agents/updateStatus → 1831. */
	@Component
	static class UpdateStatus implements EpsHandler {

		private final NetworkCommands commands;

		UpdateStatus(NetworkCommands commands) {
			this.commands = commands;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.POST, "/network/agents/updateStatus"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			int statusId = call.params().integer("agentAccountStatus")
				.orElseThrow(() -> ValidationException.field("agentAccountStatus", "Choose a status"));
			UserStatus status = switch (statusId) {
				case EpsValues.STATUS_ACTIVE -> UserStatus.ACTIVE;
				case EpsValues.STATUS_INACTIVE -> UserStatus.INACTIVE;
				default -> throw ValidationException.field("agentAccountStatus", "Unknown status");
			};
			commands.changeStatus(call.caller(), call.params().requireString("csp_code"), status,
					call.params().string("updateStatusNote").orElse(null));
			return EpsEnvelope.ok().type(1831).message("Status updated").build();
		}
	}

	/** tf PUT /network/agents/profile/changeRole/{upgrademerchant 1842 | promotecsps 1967 | transfercsps 1872}. */
	@Component
	static class ChangeRole implements EpsHandler {

		private static final String BASE = "/network/agents/profile/changeRole/";

		private final NetworkCommands commands;

		ChangeRole(NetworkCommands commands) {
			this.commands = commands;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.PUT, BASE + "upgrademerchant"),
					RouteKey.tf(V1, HttpMethod.PUT, BASE + "promotecsps"), RouteKey.tf(V1, HttpMethod.PUT, BASE + "transfercsps"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.ORG_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			String uri = call.request().tf().orElseThrow().uri();
			EpsParams p = call.params();
			if (uri.endsWith("upgrademerchant")) {
				commands.upgradeToIndependentRetailer(call.caller(), p.requireString("agent_mobile"));
				return EpsEnvelope.ok().type(1842).message("Agent upgraded").build();
			}
			if (uri.endsWith("promotecsps")) {
				commands.promoteToDistributor(call.caller(), p.requireString("agent_mobile"));
				return EpsEnvelope.ok().type(1967).message("Agent promoted").build();
			}
			List<String> codes = Arrays.stream(p.requireString("selectedTransferredCSPsList").split(","))
				.map(String::trim).filter(s -> !s.isEmpty()).toList();
			commands.transfer(call.caller(), p.requireString("scspTo"), codes);
			return EpsEnvelope.ok().type(1872).message("Agents moved").build();
		}
	}

	/** tf POST /network/agents/profile/personalInfo/update. */
	@Component
	static class UpdatePersonal implements EpsHandler {

		private final NetworkCommands commands;

		UpdatePersonal(NetworkCommands commands) {
			this.commands = commands;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.POST, "/network/agents/profile/personalInfo/update"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EpsParams p = call.params();
			String code = p.string("merchant_code").orElse(null);
			String name = String.join(" ", java.util.stream.Stream.of("first_name", "middle_name", "last_name")
				.map(f -> p.string(f).orElse("")).filter(s -> !s.isBlank()).toList());
			PersonalInfo info = new PersonalInfo(p.string("dob").map(v -> EpsValues.date("dob", v)).orElse(null),
					p.string("gender").orElse(null), p.string("marital_status").orElse(null), null);
			commands.updatePersonal(call.caller(), code, name, info);
			if (p.string("shop_name").isPresent() || p.string("shop_type").isPresent()) {
				commands.updateShop(call.caller(), code, new ShopView(p.string("shop_name").orElse(null),
						p.string("shop_type").orElse(null), null, null, null, null, null, null, null, null, null));
			}
			return EpsEnvelope.ok().message("Details updated").build();
		}
	}

	/** tf POST /network/agents/profile/address/update (also used by agreement signing for the admin's own address). */
	@Component
	static class UpdateAddress implements EpsHandler {

		private final NetworkCommands commands;

		UpdateAddress(NetworkCommands commands) {
			this.commands = commands;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.POST, "/network/agents/profile/address/update"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EpsParams p = call.params();
			String pincode = p.string("pincode").orElse(null);
			if (pincode != null && !pincode.matches("\\d{6}")) {
				throw ValidationException.field("pincode", "Enter a 6-digit pincode");
			}
			commands.updateShop(call.caller(), p.string("merchant_code").orElse(null),
					new ShopView(null, null, p.string("address_line1").orElse(null), p.string("address_line2").orElse(null),
							p.string("city").orElse(null), p.string("country_state").orElse(null), pincode,
							p.string("country").orElse(null), p.string("shop_ownership_type").orElse(null), null, null));
			return EpsEnvelope.ok().message("Address updated").build();
		}
	}

	/** tf POST /network/agent: remove a demo user. */
	@Component
	static class RemoveDemoUser implements EpsHandler {

		private final NetworkCommands commands;

		RemoveDemoUser(NetworkCommands commands) {
			this.commands = commands;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.tf(V1, HttpMethod.POST, "/network/agent"));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			commands.removeDemoUser(call.caller(), call.params().requireString("csp_code"));
			return EpsEnvelope.ok().message("Demo user removed").build();
		}
	}
}
