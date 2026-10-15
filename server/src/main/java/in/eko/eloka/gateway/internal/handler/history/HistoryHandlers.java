package in.eko.eloka.gateway.internal.handler.history;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsParams;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.gateway.internal.handler.TxnRows;
import in.eko.eloka.gateway.internal.report.ReportFormat;
import in.eko.eloka.gateway.internal.report.ReportRenderer;
import in.eko.eloka.gateway.internal.report.ReportTable;
import in.eko.eloka.history.HistoryApi;
import in.eko.eloka.history.HistoryFilter;
import in.eko.eloka.history.TxnView;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.error.ForbiddenException;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.shared.security.Role;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.stereotype.Component;

/**
 * Transaction history: 154 (list) and 183 (export). Two views:
 * <ul>
 * <li>own history ({@code isNetworkTransactionHistory=0}): the caller's account only;</li>
 * <li>network history ({@code =1}, distributors/admins): everyone in the caller's scope,
 * optionally one agent ({@code agent_mobile} or {@code csp_code}).</li>
 * </ul>
 */
@Component
class HistoryHandlers implements EpsHandler {

	private static final int MAX_EXPORT_ROWS = 50_000;

	private final HistoryApi history;
	private final NetworkQueries network;
	private final WalletApi wallets;
	private final ReportRenderer reports;

	HistoryHandlers(HistoryApi history, NetworkQueries network, WalletApi wallets, ReportRenderer reports) {
		this.history = history;
		this.network = network;
		this.wallets = wallets;
		this.reports = reports;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.interaction(154), RouteKey.interaction(183));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.USER;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		Caller caller = call.caller();
		EpsParams p = call.params();
		boolean export = "183".equals(call.request().interactionId().orElse(""));
		boolean forNetwork = p.integer("isNetworkTransactionHistory", 0) == 1;

		Collection<UUID> users = forNetwork ? networkScope(caller, p) : List.of(caller.userId());
		HistoryFilter filter = filterOf(caller, p, forNetwork);
		int offset = export ? 0 : p.integer("start_index", 0);
		int limit = export ? MAX_EXPORT_ROWS : p.integer("limit", 25);
		List<TxnView> txns = history.search(caller.orgId(), users, filter, offset, limit);

		Map<UUID, UserView> agents = forNetwork
				? network.byIds(caller.orgId(), txns.stream().map(TxnView::userId).distinct().toList()) : Map.of();
		List<Map<String, Object>> rows = txns.stream().map(t -> {
			Map<String, Object> row = TxnRows.row(t);
			UserView agent = agents.get(t.userId());
			if (agent != null) {
				row.put("agent_name", agent.name());
				row.put("agent_type", EpsValues.typeLabel(agent.userType()));
				row.put("agent_cell", agent.mobile());
			}
			return row;
		}).toList();

		if (!export) {
			return EpsEnvelope.ok().data(Map.of("transaction_list", rows)).build();
		}
		ReportFormat format = ReportFormat.of(p.string("reporttype").orElse(null), ReportFormat.XLSX);
		ReportRenderer.Rendered file = reports.render(table(rows, forNetwork), format,
				forNetwork ? "network_transactions" : "transaction_history");
		return new EpsResponse.File(file.bytes(), file.fileName(), file.contentType());
	}

	/** Users whose history the caller may see in the network view, narrowed to one agent if asked. */
	private Collection<UUID> networkScope(Caller caller, EpsParams p) {
		if (caller.role() != Role.ORG_ADMIN && caller.role() != Role.DISTRIBUTOR) {
			throw new ForbiddenException("NOT_ALLOWED", "You are not allowed to do this.");
		}
		var agent = p.string("agent_mobile").or(() -> p.string("csp_code"));
		if (agent.isPresent()) {
			return List.of(network.findInScope(caller, agent.get()).id());
		}
		return network.userIdsInScope(caller); // null = whole org (admin)
	}

	private HistoryFilter filterOf(Caller caller, EpsParams p, boolean forNetwork) {
		Long accountId = null;
		if (!forNetwork) {
			accountId = p.longValue("account_id").orElse(null);
			if (accountId != null) {
				long requested = accountId;
				wallets.accountsOf(caller.orgId(), caller.userId()).stream().filter(a -> a.id() == requested).findFirst()
					.orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Account not found"));
			}
		}
		return new HistoryFilter(accountId, p.integer("tx_typeid").orElse(null),
				p.longValue("tid").orElse(null), p.string("customer_mobile").orElse(null),
				p.string("start_date").map(v -> EpsValues.date("start_date", v)).orElse(null),
				p.string("tx_date").map(v -> EpsValues.date("tx_date", v)).orElse(null),
				p.decimal("amount").orElse(null));
	}

	private static ReportTable table(List<Map<String, Object>> rows, boolean forNetwork) {
		List<String> headers = new ArrayList<>(List.of("TID", "Date", "Transaction", "Amount", "Debit/Credit", "Fee",
				"Commission", "Status", "Customer", "Customer Mobile"));
		if (forNetwork) {
			headers.addAll(0, List.of("Agent", "Agent Code"));
		}
		List<List<String>> body = rows.stream().map(r -> {
			List<String> line = new ArrayList<>();
			if (forNetwork) {
				line.add(text(r.get("agent_name")));
				line.add(text(r.get("agent_code")));
			}
			boolean debit = !Objects.equals(r.get("amount_dr"), 0);
			line.addAll(List.of(text(r.get("tid")), text(r.get("datetime")), text(r.get("tx_name")), text(r.get("amount")),
					debit ? "Debit" : "Credit", text(r.get("fee")), text(r.get("commission_earned")), text(r.get("status")),
					text(r.get("customer_name")), text(r.get("customer_mobile"))));
			return line;
		}).toList();
		return new ReportTable(forNetwork ? "Network transactions" : "Transaction history", headers, body);
	}

	private static String text(Object value) {
		return value == null ? "" : value.toString();
	}
}
