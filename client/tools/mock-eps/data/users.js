/**
 * Dummy users for local testing. Every login uses the same OTP: {@link MOCK_OTP}.
 * Any other 10-digit mobile number logs in as a brand-new user (self-onboarding from role selection).
 *
 * Keep this table in sync with docs/LOCAL_DEVELOPMENT.md at the repo root.
 */

/** The OTP accepted for every dummy login. */
export const MOCK_OTP = "123456";

/** User types (constants/UserTypes.js). */
export const USER_TYPE = {
	DISTRIBUTOR: 1,
	RETAILER: 2,
	ORG_ADMIN: 24,
};

/** Onboarding step roles (features/onboarding/constants.ts), with the label the API sends. */
export const STEP = {
	AADHAAR: { role: 12400, label: "Aadhaar Verification" },
	DIGILOCKER: { role: 24000, label: "Digilocker Verification" },
	PAN: { role: 12300, label: "PAN Verification" },
	SELFIE: { role: 12500, label: "Selfie KYC" },
	BUSINESS: { role: 13300, label: "Business Details" },
	BANK: { role: 51700, label: "Add Bank Account" },
	SECRET_PIN: { role: 12600, label: "Secret Pin" },
	AGREEMENT: { role: 12800, label: "Sign Agreement" },
};

/** The KYC steps each role must finish, in order. */
export const ONBOARDING_PLAN = {
	[USER_TYPE.RETAILER]: [
		STEP.AADHAAR,
		STEP.PAN,
		STEP.SELFIE,
		STEP.BUSINESS,
		STEP.BANK,
		STEP.SECRET_PIN,
		STEP.AGREEMENT,
	],
	[USER_TYPE.DISTRIBUTOR]: [
		STEP.DIGILOCKER,
		STEP.PAN,
		STEP.BUSINESS,
		STEP.BANK,
		STEP.AGREEMENT,
	],
};

/**
 * Seed users. `pendingRoles` lists the onboarding steps still to do
 * (empty = fully onboarded).
 */
export const SEED_USERS = [
	{
		mobile: "9000000001",
		persona: "Retailer (onboarded agent)",
		code: "30000001",
		name: "Ravi Retailer",
		email: "ravi.retailer@example.test",
		user_type: USER_TYPE.RETAILER,
		is_org_admin: 0,
		shop_name: "Ravi General Store",
		balance: 25450.75,
		pendingRoles: [],
	},
	{
		mobile: "9000000002",
		persona: "Distributor (onboarded, has a network)",
		code: "30000002",
		name: "Divya Distributor",
		email: "divya.distributor@example.test",
		user_type: USER_TYPE.DISTRIBUTOR,
		is_org_admin: 0,
		shop_name: "Divya Enterprises",
		balance: 182300.0,
		pendingRoles: [],
	},
	{
		mobile: "9000000003",
		persona: "Org Admin (Local Admin portal)",
		code: "30000003",
		name: "Arjun Admin",
		email: "arjun.admin@example.test",
		user_type: USER_TYPE.ORG_ADMIN,
		is_org_admin: 1,
		shop_name: "Abcorp HQ",
		balance: 500000.0,
		pendingRoles: [],
	},
	{
		mobile: "9000000005",
		persona: "Retailer, onboarding half done (resumes at Selfie KYC)",
		code: "30000005",
		name: "Meena Midway",
		email: "meena.midway@example.test",
		user_type: USER_TYPE.RETAILER,
		is_org_admin: 0,
		shop_name: "Meena Mobile Point",
		balance: 0,
		pendingRoles: ONBOARDING_PLAN[USER_TYPE.RETAILER]
			.slice(2)
			.map((step) => step.role),
	},
];

/** A mobile number that is not registered: logs in as a new user. Listed for the docs table. */
export const NEW_USER_MOBILE = "9000000004";

/** Retailers that sit under the distributor / admin in the Network pages. */
export const NETWORK_USERS = [
	{
		user_code: "30000001",
		name: "Ravi Retailer",
		mobile: "9000000001",
		user_type_id: 2,
	},
	{
		user_code: "30000011",
		name: "Kiran Kirana",
		mobile: "9000000011",
		user_type_id: 2,
	},
	{
		user_code: "30000012",
		name: "Sana Stores",
		mobile: "9000000012",
		user_type_id: 2,
	},
	{
		user_code: "30000002",
		name: "Divya Distributor",
		mobile: "9000000002",
		user_type_id: 1,
	},
];
