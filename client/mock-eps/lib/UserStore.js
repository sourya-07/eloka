import { ONBOARDING_PLAN, SEED_USERS, STEP } from "../data/users.js";

/**
 * In-memory user database of the mock EPS server.
 * Owns every rule about users: lookup, sign-up, onboarding progress and the
 * EPS-shaped profile the frontend reads. State resets when the server restarts
 * (or on `POST /__mock/reset`).
 */
export class UserStore {
	/** @type {Map<string, object>} mobile → user */
	#users = new Map();
	#nextCode = 30000100;

	constructor() {
		this.reset();
	}

	/** Restore the seed users and forget every sign-up and onboarding step. */
	reset() {
		this.#users = new Map(
			SEED_USERS.map((seed) => [
				seed.mobile,
				{ ...seed, pendingRoles: [...seed.pendingRoles] },
			])
		);
		this.#nextCode = 30000100;
	}

	/**
	 * @param {string} mobile
	 * @returns {object|undefined} The user, or undefined if the number is not registered
	 */
	find(mobile) {
		return this.#users.get(String(mobile ?? ""));
	}

	/** @returns {Array<object>} All users (for the /__mock/users debug endpoint) */
	all() {
		return [...this.#users.values()];
	}

	/**
	 * Create a partial account after role selection (interaction 521).
	 * @param {string} mobile
	 * @param {number} userType - USER_TYPE.RETAILER or USER_TYPE.DISTRIBUTOR
	 * @returns {object} The new or updated user
	 */
	signUp(mobile, userType) {
		const plan = ONBOARDING_PLAN[userType] ?? ONBOARDING_PLAN[2];
		const existing = this.find(mobile);
		const user = existing ?? {
			mobile: String(mobile),
			persona: "Self-registered",
			code: String(this.#nextCode++),
			name: `New User ${String(mobile).slice(-4)}`,
			email: "",
			is_org_admin: 0,
			shop_name: "",
			balance: 0,
		};
		user.user_type = userType;
		user.pendingRoles = plan.map((step) => step.role);
		this.#users.set(user.mobile, user);
		return user;
	}

	/**
	 * Mark an onboarding step as done. A step can carry several roles (PAN = 12300 / 13000).
	 * @param {string} mobile
	 * @param {Array<number>} roles - Roles the finished step covers
	 * @returns {object|undefined} The updated user
	 */
	completeStep(mobile, roles) {
		const user = this.find(mobile);
		if (!user) return undefined;
		user.pendingRoles = user.pendingRoles.filter(
			(role) => !roles.includes(role)
		);
		return user;
	}

	/**
	 * @param {object} user
	 * @returns {boolean} True while KYC steps remain
	 */
	isOnboarding(user) {
		return user.pendingRoles.length > 0;
	}

	/**
	 * The `data` block of interaction 151 (user profile), in EPS shape.
	 * @param {object} user
	 * @param {string|number} orgId
	 * @returns {object} `{ user_detail, personal_detail, shop_detail, account_detail }`
	 */
	toProfile(user, orgId) {
		const plan = ONBOARDING_PLAN[user.user_type] ?? [];
		const onboarding = this.isOnboarding(user);
		return {
			user_detail: {
				name: user.name,
				mobile: user.mobile,
				login_id: user.mobile,
				code: user.code,
				user_code: user.code,
				org_id: String(orgId ?? 2),
				bc: String(orgId ?? 2),
				user_type: user.user_type,
				is_org_admin: user.is_org_admin,
				email: user.email,
				shop_name: user.shop_name,
				date_of_joining: "2026-01-15",
				onboarding: onboarding ? 1 : 0,
				onboarding_steps: onboarding ? plan : [],
				role_list: user.pendingRoles.join(","),
				agreement_id: 5,
			},
			personal_detail: {
				name: user.name,
				email: user.email,
				mobile: user.mobile,
			},
			shop_detail: {
				shop_name: user.shop_name,
				shop_address: "MG Road, Bengaluru 560001",
			},
			account_detail: {
				account_list: [
					{ id: 1, type_id: 1, account_number: user.code },
				],
			},
		};
	}
}

/** Which roles each finishing interaction completes. `doc_type` applies to upload 523. */
export const STEP_COMPLETION = {
	upload: {
		1: [STEP.AADHAAR.role],
		2: [STEP.PAN.role, 13000],
		3: [STEP.SELFIE.role],
		7: [STEP.BANK.role],
	},
	552: [STEP.DIGILOCKER.role],
	522: [STEP.BUSINESS.role],
	5: [STEP.SECRET_PIN.role],
	293: [STEP.AGREEMENT.role],
};
