import { fail, list, ok } from "../lib/envelope.js";
import { STEP_COMPLETION } from "../lib/UserStore.js";
import { BANKS, SHOP_TYPES, STATES } from "../data/catalog.js";
import { MOCK_OTP, USER_TYPE } from "../data/users.js";

/**
 * Agent onboarding: role selection, KYC steps, lookups and assisted onboarding.
 * Every step succeeds with the response_type_id the frontend expects
 * (features/onboarding/constants.ts) and moves the user one step forward.
 */

/** applicant_type sent by role selection → user type. */
const APPLICANT_TYPE_TO_USER_TYPE = {
	0: USER_TYPE.RETAILER,
	2: USER_TYPE.DISTRIBUTOR,
};

/**
 * Finish the roles an interaction covers and answer with its success id.
 * @param {object} ctx - Request context
 * @param ctx.body
 * @param ctx.store
 * @param {Array<number>} roles - Roles completed by this call
 * @param {number} responseTypeId - Success id the step waits for
 * @returns {object} Envelope
 */
const completeStep = ({ body, store }, roles, responseTypeId) => {
	const mobile = body.mobile ?? body.user_id ?? body.csp_id;
	if (!store.find(mobile)) return fail(`No user with mobile ${mobile}`);
	store.completeStep(mobile, roles);
	return ok({}, { response_type_id: responseTypeId, message: "Saved" });
};

/** @type {Record<string, (ctx: object) => object>} keyed by interaction_type_id */
export const onboardingInteractions = {
	/**
	 * Role selection: create the partial account.
	 * @param root0
	 * @param root0.body
	 * @param root0.store
	 */
	521: ({ body, store }) => {
		const userType =
			APPLICANT_TYPE_TO_USER_TYPE[Number(body.applicant_type)];
		if (!userType) return fail("Only Retailer and Distributor are mocked");
		store.signUp(body.mobile ?? body.user_id, userType);
		return ok({}, { response_type_id: 1566, message: "Role saved" });
	},

	/**
	 * Upload document (multipart). `doc_type` decides which step it finishes.
	 * @param ctx
	 */
	523: (ctx) =>
		completeStep(
			ctx,
			STEP_COMPLETION.upload[Number(ctx.body.doc_type)] ?? [],
			1569
		),

	/** Selfie step, part 1: location + shop type. Completion happens on the selfie upload. */
	298: () => ok({}, { response_type_id: 1076 }),

	522: (ctx) => completeStep(ctx, STEP_COMPLETION[522], 1567),
	552: (ctx) => completeStep(ctx, STEP_COMPLETION[552], 1621),
	/** Bank, part 1: account details. Completion happens on the passbook upload. */
	454: () => ok({}, { response_type_id: 1445 }),
	5: (ctx) => completeStep(ctx, STEP_COMPLETION[5], 9),
	293: (ctx) => completeStep(ctx, STEP_COMPLETION[293], 1615),

	/**
	 * Secret PIN booklet. Not a PinTwin user → plain PIN entry.
	 * @param root0
	 * @param root0.body
	 */
	170: ({ body }) =>
		ok(
			{
				user_code: body.user_code,
				booklet_serial_number: "",
				is_pintwin_user: 0,
			},
			{ response_type_id: 1646 }
		),

	/** PinTwin key: identity mapping, so the encoded PIN equals the typed PIN. */
	10005: () => ok({ pintwin_key: "0123456789", key_id: 1 }),

	/** e-Sign URL. 1069 = "already signed", so the step needs no provider SDK locally. */
	287: () => ok({ document_id: "MOCK-DOC-1" }, { response_type_id: 1069 }),

	519: () => list(SHOP_TYPES),
	387: () => list(STATES),
	155: () => list(BANKS),
	353: () => list([{ value: "560001", label: "560001 - Bengaluru" }]),

	/**
	 * Assisted onboarding: add an agent by mobile.
	 * @param root0
	 * @param root0.body
	 * @param root0.store
	 */
	194: ({ body, store }) => {
		const agent = store.find(body.csp_id);
		if (agent)
			return ok({ csp_id: body.csp_id }, { response_type_id: 874 });
		return ok(
			{ otp: MOCK_OTP, csp_id: body.csp_id },
			{ response_type_id: 873 }
		);
	},

	/**
	 * Assisted onboarding: verify the agent's OTP.
	 * @param root0
	 * @param root0.body
	 */
	197: ({ body }) =>
		String(body.otp) === MOCK_OTP
			? ok({ csp_id: body.csp_id }, { response_type_id: 876 })
			: fail("Invalid OTP", { response_type_id: 302 }),
};

/** @type {Record<string, (ctx: object) => object>} keyed by tf-req-uri */
export const onboardingTfRoutes = {
	/**
	 * DigiLocker consent link. Opens the mock consent page served by this server.
	 * @param root0
	 * @param root0.origin
	 */
	"/karza/digilocker-redirection-url": ({ origin }) => {
		const requestId = `MOCK-DL-${Date.now()}`;
		return ok({
			link: `${origin}/__mock/digilocker?requestId=${requestId}`,
			requestId,
		});
	},

	/**
	 * Bulk onboarding by form / file (admin).
	 * @param root0
	 * @param root0.body
	 */
	"/network/agent/multiple_onboarding": ({ body }) => {
		const rows = (Array.isArray(body.CspList) ? body.CspList : []).map(
			(agent) => ({
				name: agent.agent_name,
				mobile: agent.agent_mobile,
				status: "Success",
				reason: "",
			})
		);
		return ok(
			{ csp_list: rows, processed_records: rows.length, failed_count: 0 },
			{ message: "All agents onboarded (mock)" }
		);
	},
	"/network/agent/bulk_onboarding": () =>
		ok({}, { message: "File accepted (mock)" }),
};
