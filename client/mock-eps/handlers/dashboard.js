import { list, ok } from "../lib/envelope.js";
import {
	ACTIVE_USERS,
	DASHBOARD_PRODUCTS,
	EARNING_SUMMARY,
	NOTIFICATIONS,
	RECENT_TRANSACTIONS,
	TOP_MERCHANTS,
} from "../data/catalog.js";
import { NETWORK_USERS } from "../data/users.js";

/**
 * Data fetched right after login: wallet, notifications, dashboards, history, network.
 */

/** @type {Record<string, (ctx: object) => object>} keyed by interaction_type_id */
export const dashboardInteractions = {
	/** Wallet balance. */
	9: ({ user }) => ok({ balance: user?.balance ?? 0 }),

	10010: () => ok({ notifications: NOTIFICATIONS }),
	10012: () => ok({}),
	10023: () => ok({}),

	623: () => ok(EARNING_SUMMARY),
	154: () => ok({ transaction_list: RECENT_TRANSACTIONS }),

	818: () => ok({ dashboard_object: { totalActiveData: ACTIVE_USERS } }),
	1044: () => list(DASHBOARD_PRODUCTS),
	/** Admin business dashboard widgets. Lists must stay arrays or the widgets crash. */
	682: () =>
		ok({
			dashboard_object: {
				products_overview: {},
				successRate: {},
				mostUsedServices: {},
				verificationTrends: [],
				gtv_top_merchants: TOP_MERCHANTS,
			},
		}),
};

/** @type {Record<string, (ctx: object) => object>} keyed by tf-req-uri */
export const dashboardTfRoutes = {
	"/network/agent/pricing_commission": () =>
		ok({ pricing_commission_data: [] }),
	"/network/agent-list": () => ok({ csp_list: NETWORK_USERS }),
};
