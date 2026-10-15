package in.eko.eloka.gateway.internal.handler.home;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import in.eko.eloka.gateway.internal.dispatch.AccessPolicy;
import in.eko.eloka.gateway.internal.dispatch.EpsCall;
import in.eko.eloka.gateway.internal.dispatch.EpsHandler;
import in.eko.eloka.gateway.internal.dispatch.RouteKey;
import in.eko.eloka.gateway.internal.envelope.EpsEnvelope;
import in.eko.eloka.gateway.internal.envelope.EpsResponse;
import in.eko.eloka.gateway.internal.handler.EpsDates;
import in.eko.eloka.history.EarningsSummary;
import in.eko.eloka.history.HistoryApi;
import in.eko.eloka.notification.InboxItem;
import in.eko.eloka.notification.NotificationApi;
import in.eko.eloka.shared.error.NotFoundException;
import in.eko.eloka.shared.security.Caller;
import in.eko.eloka.wallet.WalletAccountView;
import in.eko.eloka.wallet.WalletApi;
import org.springframework.stereotype.Component;

/** Calls the agent home page makes right after login. */
final class HomeHandlers {

	private HomeHandlers() {
	}

	/** 9: wallet balance (own E-value wallet, or a named account the caller owns). */
	@Component
	static class WalletBalance implements EpsHandler {

		private final WalletApi wallets;

		WalletBalance(WalletApi wallets) {
			this.wallets = wallets;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(9));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			List<WalletAccountView> accounts = wallets.accountsOf(caller.orgId(), caller.userId());
			WalletAccountView account = call.params().string("account")
				.map(number -> accounts.stream().filter(a -> a.accountNumber().equals(number)).findFirst()
					.orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND", "Account not found")))
				.orElseGet(() -> accounts.stream().filter(a -> a.typeId() == WalletAccountView.TYPE_EVALUE).findFirst()
					.orElse(null));
			BigDecimal balance = account == null ? BigDecimal.ZERO : account.balance();
			return EpsEnvelope.ok().data(Map.of("balance", balance)).build();
		}
	}

	/** 623: earning summary. */
	@Component
	static class Earnings implements EpsHandler {

		private final HistoryApi history;

		Earnings(HistoryApi history) {
			this.history = history;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(623));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			EarningsSummary e = history.earnings(call.caller().orgId(), call.caller().userId());
			Map<String, Object> data = new LinkedHashMap<>();
			data.put("this_month", e.thisMonth());
			data.put("last_month", e.lastMonth());
			data.put("prev_month", e.previousMonth());
			data.put("commission_due", e.commissionDue());
			return EpsEnvelope.ok().data(data).build();
		}
	}

	/** 10010: in-app notifications, newest first. */
	@Component
	static class Notifications implements EpsHandler {

		private final NotificationApi notifications;

		Notifications(NotificationApi notifications) {
			this.notifications = notifications;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(10010));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			List<Map<String, Object>> items = notifications.inbox(call.caller().orgId(), call.caller().userId(), 50)
				.stream()
				.map(Notifications::item)
				.toList();
			return EpsEnvelope.ok().data(Map.of("notifications", items)).build();
		}

		private static Map<String, Object> item(InboxItem n) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", n.id());
			item.put("notification_type", "AD".equals(n.type()) ? 2 : 0);
			item.put("title", n.title());
			item.put("desc", n.body() == null ? "" : n.body());
			item.put("image", n.imageUrl());
			item.put("link", n.link());
			item.put("link_label", n.linkLabel());
			item.put("notify_time", EpsDates.format(n.notifyTime()));
			item.put("priority", n.priority());
			item.put("state", 1);
			item.put("read", n.read());
			item.put("delivery_status", n.delivered() ? 2 : 0);
			return item;
		}
	}

	/** 10023 (delivered) and 10012 (read / dismissed). Answers are ignored by the client. */
	@Component
	static class NotificationReceipts implements EpsHandler {

		private final NotificationApi notifications;

		NotificationReceipts(NotificationApi notifications) {
			this.notifications = notifications;
		}

		@Override
		public List<RouteKey> routes() {
			return List.of(RouteKey.interaction(10023), RouteKey.interaction(10012));
		}

		@Override
		public AccessPolicy access() {
			return AccessPolicy.USER;
		}

		@Override
		public EpsResponse handle(EpsCall call) {
			Caller caller = call.caller();
			long id = Long.parseLong(call.params().requireString("notification_id"));
			if ("10023".equals(call.request().interactionId().orElse(""))) {
				notifications.markDelivered(caller.orgId(), caller.userId(), id);
			}
			else {
				notifications.markStatus(caller.orgId(), caller.userId(), id, call.params().integer("notification_status", 1));
			}
			return EpsEnvelope.ok().build();
		}
	}
}
