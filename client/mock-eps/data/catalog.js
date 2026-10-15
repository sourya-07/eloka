/**
 * Static reference data returned by the mock EPS server: menu, dropdown lists,
 * notifications and sample transactions. Plain data only, no logic.
 */

/** Product menu (`POST /transactions/wlc`). Transactions themselves run in the EkoConnect widget, which is not mocked. */
export const MENU = [
	{
		id: 40,
		label: "Money Transfer",
		is_visible: 1,
		icon: "money-note",
		history_label: "Money Transfer",
		interaction_type_id: 221,
		description: "Send money to any bank account",
	},
	{
		id: 41,
		label: "AePS Cashout",
		is_visible: 1,
		icon: "fingerprint",
		history_label: "AePS",
		interaction_type_id: 225,
		description: "Aadhaar-enabled cash withdrawal",
	},
	{
		id: 42,
		label: "Bill Payment",
		is_visible: 1,
		icon: "invoice",
		history_label: "Bill Payment",
		interaction_type_id: 244,
		description: "Electricity, water, gas and more",
	},
	{
		id: 43,
		label: "Mobile Recharge",
		is_visible: 1,
		icon: "phone",
		history_label: "Recharge",
		interaction_type_id: 64,
	},
	{
		id: 44,
		label: "QR Payments",
		is_visible: 1,
		icon: "qrcode",
		history_label: "QR",
		interaction_type_id: 860,
	},
];

export const SHOP_TYPES = [
	{ value: "1", label: "Kirana / General Store" },
	{ value: "2", label: "Mobile Shop" },
	{ value: "3", label: "Medical Store" },
	{ value: "4", label: "Cyber Cafe" },
];

export const STATES = [
	{ value: "29", label: "Karnataka" },
	{ value: "27", label: "Maharashtra" },
	{ value: "7", label: "Delhi" },
	{ value: "9", label: "Uttar Pradesh" },
	{ value: "33", label: "Tamil Nadu" },
];

export const BANKS = [
	{
		value: "SBIN",
		label: "State Bank of India",
		dependent_params: [{ name: "bank_id", value: "108" }],
	},
	{
		value: "HDFC",
		label: "HDFC Bank",
		dependent_params: [{ name: "bank_id", value: "56" }],
	},
	{
		value: "ICIC",
		label: "ICICI Bank",
		dependent_params: [{ name: "bank_id", value: "60" }],
	},
	{
		value: "UTIB",
		label: "Axis Bank",
		dependent_params: [{ name: "bank_id", value: "14" }],
	},
];

export const NOTIFICATIONS = [
	{
		id: 1,
		notification_type: 0,
		title: "Welcome to the local mock",
		desc: "You are using dummy data from client/mock-eps. Nothing here touches a real system.",
		notify_time: "2026-10-15 10:00:00",
		priority: 1,
		read: 0,
		delivery_status: 1,
	},
];

export const RECENT_TRANSACTIONS = [
	{
		tid: "MOCK1001",
		tx_name: "Money Transfer",
		tx_typeid: 221,
		amount_dr: 2500,
		amount_cr: 0,
		customer_mobile: "9811111111",
	},
	{
		tid: "MOCK1002",
		tx_name: "AePS Cashout",
		tx_typeid: 225,
		amount_dr: 0,
		amount_cr: 1000,
		customer_mobile: "9822222222",
	},
	{
		tid: "MOCK1003",
		tx_name: "Bill Payment",
		tx_typeid: 244,
		amount_dr: 845,
		amount_cr: 0,
		customer_mobile: "9833333333",
	},
];

export const EARNING_SUMMARY = {
	this_month: 1840.5,
	last_month: 2310.25,
	prev_month: 1975,
	commission_due: 120,
};

/** Admin dashboard: active vs total users per user type (interaction 818). */
export const ACTIVE_USERS = {
	1: { activecount: 1, totalcount: 1 },
	2: { activecount: 3, totalcount: 4 },
};

/** Admin dashboard product filter (interaction 1044). */
export const DASHBOARD_PRODUCTS = MENU.map((item) => ({
	label: item.label,
	tx_typeid: item.interaction_type_id,
}));

/** Admin dashboard "Top merchants" widget (interaction 682, gtv_top_merchants). */
export const TOP_MERCHANTS = [
	{
		usercode: "30000001",
		name: "Ravi Retailer",
		gtv: 482000,
		totalTransactions: 312,
		revenue: 1840,
	},
	{
		usercode: "30000011",
		name: "Kiran Kirana",
		gtv: 265500,
		totalTransactions: 190,
		revenue: 1105,
	},
	{
		usercode: "30000012",
		name: "Sana Stores",
		gtv: 98000,
		totalTransactions: 74,
		revenue: 402,
	},
];
