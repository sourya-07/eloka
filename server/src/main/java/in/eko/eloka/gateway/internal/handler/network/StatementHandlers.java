package in.eko.eloka.gateway.internal.handler.network;

import java.math.BigDecimal;
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
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.wallet.LedgerLine;
import in.eko.eloka.wallet.StatementPage;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

/**
 * Legacy "Network Statement" screens: find an agent, their recent wallet activity, and the
 * full account statement with running balance.
 */
@Component
class StatementHandlers implements EpsHandler {

	private static final String BASE = "/network/agents/transaction_history";

	private final NetworkQueries network;
	private final WalletApi wallets;
	private final NetworkHandlers.Balances balances;

	StatementHandlers(NetworkQueries network, WalletApi wallets, NetworkHandlers.Balances balances) {
		this.network = network;
		this.wallets = wallets;
		this.balances = balances;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.tf(NetworkHandlers.V1, HttpMethod.GET, BASE),
				RouteKey.tf(NetworkHandlers.V1, HttpMethod.GET, BASE + "/recent_transaction"),
				RouteKey.tf(NetworkHandlers.V1, HttpMethod.GET, BASE + "/recent_transaction/account_statement"));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		Caller caller = call.caller();
		EpsParams p = call.params();
		String uri = call.request().tf().orElseThrow().uri();
		UserView agent = network.findInScope(caller, p.requireString("search_value"));

		if (uri.equals(BASE)) {
			ShopView shop = network.shopOf(caller, agent.id());
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("agent_name", agent.name());
			row.put("agent_mobile", agent.mobile());
			row.put("agent_type", EpsValues.typeLabel(agent.userType()));
			row.put("account_status", EpsValues.statusLabel(agent));
			row.put("location", shop.location());
			row.put("latitude", shop.latitude());
			row.put("longitude", shop.longitude());
			return EpsEnvelope.ok().data(Map.of("transaction_details", List.of(row))).build();
		}

		BigDecimal balance = balances.of(agent);
		int size = p.integer("record_count", 10);
		if (uri.endsWith("/recent_transaction")) {
			StatementPage page = wallets.statement(caller.orgId(), agent.id(), null, null, 0, size);
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("agent_name", agent.name());
			data.put("saving_balance", balance);
			data.put("recent_transaction_details", page.lines().stream().map(StatementHandlers::recentRow).toList());
			return EpsEnvelope.ok().data(data).build();
		}

		int pageNumber = p.integer("page_number", 1);
		StatementPage page = wallets.statement(caller.orgId(), agent.id(),
				p.string("transaction_date_from").map(v -> EpsValues.date("transaction_date_from", v)).orElse(null),
				p.string("transaction_date_to").map(v -> EpsValues.date("transaction_date_to", v)).orElse(null),
				(Math.max(1, pageNumber) - 1) * size, size);
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("agent_name", agent.name());
		data.put("saving_balance", balance);
		data.put("totalRecords", page.total());
		data.put("account_statement_details", page.lines().stream().map(StatementHandlers::statementRow).toList());
		return EpsEnvelope.ok().data(data).build();
	}

	private static Map<String, Object> recentRow(LedgerLine line) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("transaction_id", line.reference());
		row.put("date_time", EpsDates.format(line.postedAt()));
		row.put("activity", line.credit() ? "Credit" : "Debit");
		row.put("description", line.narration());
		row.put("amount", line.amount());
		return row;
	}

	private static Map<String, Object> statementRow(LedgerLine line) {
		Map<String, Object> row = recentRow(line);
		row.put("debit_credit", line.credit() ? "CR" : "DR");
		row.put("running_balance", line.balanceAfter());
		return row;
	}
}
