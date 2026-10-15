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
	/**
	 * Wallet balance.
	 * @param root0
	 * @param root0.user
	 */
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
	/**
	 * Network list, or one agent's profile with search_value (1827).
	 * @param root0
	 * @param root0.body
	 */
	"/network/agents": ({ body }) => {
		const rows = NETWORK_USERS.filter(
			(u) =>
				!body.search_value ||
				u.mobile === body.search_value ||
				u.user_code === body.search_value
		).map((u) => ({
			agent_name: u.name,
			agent_mobile: u.mobile,
			user_id: u.user_code,
			eko_code: u.user_code,
			user_type_id: u.user_type_id,
			account_status_id: 16,
			account_status: "Active",
		}));
		return ok(
			{ totalRecords: rows.length, agent_details: rows },
			body.search_value ? { response_type_id: 1827 } : {}
		);
	},
};
