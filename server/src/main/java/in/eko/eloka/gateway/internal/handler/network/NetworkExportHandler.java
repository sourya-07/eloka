package in.eko.eloka.gateway.internal.handler.network;

import java.util.ArrayList;
import java.util.List;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsValues;
import in.eko.eloka.gateway.internal.report.ReportFormat;
import in.eko.eloka.gateway.internal.report.ReportRenderer;
import in.eko.eloka.gateway.internal.report.ReportTable;
import in.eko.eloka.network.NetworkPage;
import in.eko.eloka.network.NetworkQueries;
import in.eko.eloka.network.ShopView;
import in.eko.eloka.network.UserView;
import in.eko.eloka.shared.security.Caller;
import org.springframework.stereotype.Component;

/** {@code POST /reports/agent-subnetwork}: the network list as a file (raw bytes, saved by the browser). */
@Component
class NetworkExportHandler implements EpsHandler {

	private static final int MAX_ROWS = 10_000;

	private final NetworkQueries network;
	private final ReportRenderer reports;

	NetworkExportHandler(NetworkQueries network, ReportRenderer reports) {
		this.network = network;
		this.reports = reports;
	}

	@Override
	public List<RouteKey> routes() {
		return List.of(RouteKey.path("/reports/agent-subnetwork"));
	}

	@Override
	public AccessPolicy access() {
		return AccessPolicy.DISTRIBUTOR_OR_ADMIN;
	}

	@Override
	public EpsResponse handle(EpsCall call) {
		Caller caller = call.caller();
		NetworkPage page = network.search(caller, NetworkHandlers.filterOf(call.params()), 1, MAX_ROWS);
		List<List<String>> rows = new ArrayList<>();
		for (UserView u : page.users()) {
			ShopView shop = network.shopOf(caller, u.id());
			rows.add(List.of(u.name(), u.userCode(), u.mobile(), EpsValues.typeLabel(u.userType()), EpsValues.statusLabel(u),
					u.dateOfJoining().toString(), shop.shopName() == null ? "" : shop.shopName(), shop.location()));
		}
		ReportTable table = new ReportTable("Network", List.of("Name", "Code", "Mobile", "Type", "Status", "Joined", "Shop",
				"Location"), rows);
		ReportRenderer.Rendered file = reports.render(table,
				ReportFormat.of(call.params().string("reporttype").orElse(null), ReportFormat.XLSX), "network");
		return new EpsResponse.Binary(file.bytes(), file.fileName(), file.contentType());
	}
}
