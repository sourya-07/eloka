import { fail, ok } from "../lib/envelope.js";
import { MOCK_OTP } from "../data/users.js";
import { issueToken } from "../lib/token.js";

/**
 * Login, profile and session handlers.
 * Real flow: 515 send OTP → 518 verify OTP (1541) → 151 profile (369 = registered).
 */

/** @type {Record<string, (ctx: object) => object>} keyed by interaction_type_id */
export const authInteractions = {
	/**
	 * Send OTP. The UI shows `data.otp` as a "Demo OTP" toast outside production.
	 * @param root0
	 * @param root0.body
	 */
	515: ({ body }) =>
		ok({ otp: MOCK_OTP, mobile: body.mobile, otp_length: MOCK_OTP.length }),

	/**
	 * Verify OTP.
	 * @param root0
	 * @param root0.body
	 */
	518: ({ body }) => {
		const otp = String(body.otp ?? body.id_token ?? "");
		if (otp !== MOCK_OTP) {
			return {
				...fail(`Wrong OTP. Use ${MOCK_OTP} on the local mock.`),
				otpFailed: true,
			};
		}
		return ok(
			{ otp_verification_token: issueToken(body.mobile) },
			{ response_type_id: 1541, message: "OTP verified" }
		);
	},

	/**
	 * User profile. 369 = registered user; anything else = new user → self-onboarding.
	 * @param root0
	 * @param root0.body
	 * @param root0.store
	 */
	151: ({ body, store }) => {
		const found = store.find(body.mobile ?? body.user_identity);
		if (!found) {
			return fail("User not found", { response_type_id: 370 });
		}
		return ok(store.toProfile(found, body.org_id), {
			response_type_id: 369,
			message: "Profile found",
		});
	},
};

/** @type {Record<string, (ctx: object) => object>} keyed by URL path */
export const authRoutes = {
	"/authentication/revoke": () => ok({}, { message: "Logged out" }),

	/**
	 * Refresh a logged-in user's profile, or (with `csp_id`) look up an agent for assisted onboarding.
	 * @param root0
	 * @param root0.body
	 * @param root0.store
	 * @param root0.user
	 */
	"/authentication/refresh-profile": ({ body, store, user }) => {
		if (body.csp_id) {
			const agent = store.find(body.csp_id);
			return agent ? { details: store.toProfile(agent) } : ok({});
		}
		if (!user) return fail("Session expired");
		const profile = store.toProfile(user);
		return {
			access_token: issueToken(user.mobile),
			refresh_token: issueToken(user.mobile),
			details: profile.user_detail,
			personal_details: profile.personal_detail,
			shop_details: profile.shop_detail,
			account_details: profile.account_detail,
		};
	},

	"/authentication/token": ({ user }) =>
		user
			? {
					access_token: issueToken(user.mobile),
					refresh_token: issueToken(user.mobile),
				}
			: fail("Invalid refresh token"),
};
