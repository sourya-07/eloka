/**
 * Contract tests for the EPS wire format: what the frontend sends and reads back
 * (see hooks/useLogin.js, features/onboarding, docs/eps-compat/CONTRACT.md).
 *
 * - `npm run test:mock` runs them against the local mock (client/mock-eps).
 * - `EPS_CONTRACT_BASE_URL=http://localhost:8090 EPS_CONTRACT_PHASE=1 npm run test:mock`
 *   runs them against the real server (../server, local profile). Blocks for phases the
 *   server has not reached yet are skipped.
 */
import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";
import { MockEpsServer } from "../lib/MockEpsServer.js";
import { MOCK_OTP } from "../data/users.js";

const EXTERNAL = process.env.EPS_CONTRACT_BASE_URL;
const PHASE = Number(process.env.EPS_CONTRACT_PHASE ?? 99);
/**
 * Skip a block when testing the real server before it implements that phase.
 * @param phase
 */
const until = (phase) => ({
	skip:
		EXTERNAL && PHASE < phase ? `server phase ${PHASE} < P${phase}` : false,
});

let server;
let base;
let orgToken;

before(async () => {
	if (EXTERNAL) {
		base = EXTERNAL.replace(/\/$/, "");
	} else {
		server = new MockEpsServer({ log: () => {} });
		base = `http://localhost:${await server.listen(0)}`;
	}
	// The real server identifies the tenant by org_token (from the org lookup); the mock ignores it.
	const org = await post("/", {
		interaction_type_id: "677",
		sub_domain: "demo",
		secret:
			process.env.EPS_CONTRACT_ORG_SECRET ??
			"eloka-local-org-meta-secret",
	});
	orgToken = org.org_token;
});

after(() => server?.close());

beforeEach(() => server?.store.reset());

/**
 * POST JSON, the way pages/api/transaction.js does.
 * @param path
 * @param body
 * @param token
 */
async function post(path, body, token) {
	const res = await fetch(base + path, {
		method: "POST",
		headers: {
			"Content-Type": "application/json",
			...(token && { Authorization: `Bearer ${token}` }),
		},
		body: JSON.stringify(body),
	});
	return res.json();
}

/**
 * Log in with the OTP flow and return the access token.
 * @param mobile
 */
const login = async (mobile) => {
	const sent = await post("/", {
		interaction_type_id: "515",
		mobile,
		org_token: orgToken,
	});
	const verified = await post("/", {
		interaction_type_id: "518",
		mobile,
		otp: sent.data.otp,
		org_token: orgToken,
	});
	return verified.data.otp_verification_token;
};

describe("login", () => {
	it("sends the fixed demo OTP", async () => {
		const res = await post("/", {
			interaction_type_id: "515",
			mobile: "9000000001",
			org_token: orgToken,
		});
		assert.equal(res.status, 0);
		assert.equal(res.data.otp, MOCK_OTP);
	});

	it("rejects a wrong OTP", async () => {
		await post("/", {
			interaction_type_id: "515",
			mobile: "9000000001",
			org_token: orgToken,
		});
		const res = await post("/", {
			interaction_type_id: "518",
			mobile: "9000000001",
			otp: "000000",
			org_token: orgToken,
		});
		assert.equal(res.otpFailed, true);
	});

	it("returns the registered profile (369) for a seed agent", async () => {
		const token = await login("9000000001");
		const res = await post(
			"/",
			{ interaction_type_id: "151", mobile: "9000000001", org_id: 2 },
			token
		);
		assert.equal(res.response_type_id, 369);
		assert.equal(res.data.user_detail.code, "30000001");
		assert.equal(res.data.user_detail.onboarding, 0);
	});

	it("marks the admin as org admin", async () => {
		const token = await login("9000000003");
		const res = await post(
			"/",
			{ interaction_type_id: "151", mobile: "9000000003" },
			token
		);
		assert.equal(res.data.user_detail.is_org_admin, 1);
	});

	it("treats an unknown number as a new user (not 369)", async () => {
		const token = await login("9000000004");
		const res = await post(
			"/",
			{ interaction_type_id: "151", mobile: "9000000004" },
			token
		);
		assert.notEqual(res.response_type_id, 369);
	});

	it("flags calls that are not supported", async () => {
		const res = await post("/", { interaction_type_id: 99999 });
		assert.equal(res.status, 1);
	});
});

describe("boot calls", until(2), () => {
	it("returns the menu as a top-level array", async () => {
		const token = await login("9000000001");
		const menu = await post("/transactions/wlc", {}, token);
		assert.ok(Array.isArray(menu) && menu.length > 0);
	});

	it("returns the wallet balance of the token's user", async () => {
		const token = await login("9000000002");
		const res = await post(
			"/",
			{ interaction_type_id: 9, initiator_id: "1234567891" },
			token
		);
		assert.equal(res.data.balance, 182300);
	});
});

describe("network", until(3), () => {
	it("answers tf-req calls sent through the proxy as GET paths", async () => {
		const token = await login("9000000003");
		const res = await (
			await fetch(
				`${base}/ekoicici/v1/network/agent-list?user_code=99029899`,
				{
					headers: { Authorization: `Bearer ${token}` },
				}
			)
		).json();
		assert.ok(res.data.csp_list.length > 0);
	});

	it("finds an agent profile in the network (1827)", async () => {
		const token = await login("9000000002");
		const res = await (
			await fetch(
				`${base}/ekoicici/v1/network/agents?record_count=1&search_value=9000000001`,
				{ headers: { Authorization: `Bearer ${token}` } }
			)
		).json();
		assert.equal(res.response_type_id, 1827);
		assert.equal(res.data.agent_details[0].agent_mobile, "9000000001");
	});
});

describe("onboard agents", until(4), () => {
	it("onboards a retailer by form and reports each row", async () => {
		const token = await login("9000000003");
		const res = await (
			await fetch(`${base}/transactions/dojson`, {
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					Authorization: `Bearer ${token}`,
					"tf-req-uri-root-path": "/ekoicici/v1",
					"tf-req-uri": "/network/agent/multiple_onboarding",
					"tf-req-method": "POST",
				},
				body: JSON.stringify({
					applicant_type: "0",
					CspList: [
						{
							agent_name: "Contract Test",
							agent_mobile: `98${Date.now().toString().slice(-8)}`,
						},
					],
				}),
			})
		).json();
		assert.equal(res.status, 0);
		assert.equal(res.data.csp_list.length, 1);
	});
});

describe("self-onboarding journey", until(5), () => {
	/** A 1x1 PNG: the server checks that uploads really are images. */
	const PNG = Buffer.from(
		"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
		"base64"
	);

	it("takes a new retailer from role selection to onboarded", async () => {
		// The server keeps its data between runs, so use a fresh number (and PAN) there.
		const suffix = String(Date.now()).slice(-5);
		const mobile = EXTERNAL ? `98555${suffix}` : "9000000004";
		const token = await login(mobile);
		const step = (body) => post("/", { mobile, ...body }, token);
		const profile = async () =>
			(await step({ interaction_type_id: "151" })).data.user_detail;

		const role = await step({
			interaction_type_id: 521,
			applicant_type: 0,
		});
		assert.equal(role.response_type_id, 1566);
		assert.equal((await profile()).onboarding, 1);
		assert.equal((await profile()).onboarding_steps.length, 7);

		const upload = (docType, extra = {}, files = ["file1"]) => {
			const form = new FormData();
			form.append(
				"form-data",
				new URLSearchParams({
					interaction_type_id: "523",
					mobile,
					doc_type: docType,
					...extra,
				}).toString()
			);
			for (const name of files) {
				form.append(
					name,
					new Blob([PNG], { type: "image/png" }),
					`${name}.png`
				);
			}
			return fetch(base, {
				method: "POST",
				headers: { Authorization: `Bearer ${token}` },
				body: form,
			}).then((r) => r.json());
		};

		assert.equal(
			(await upload("1", {}, ["file1", "file2"])).response_type_id,
			1569
		); // Aadhaar front + back
		assert.equal(
			(await upload("2", { doc_id: `ABCDE${suffix.slice(0, 4)}F` }))
				.response_type_id,
			1569
		); // PAN
		await step({
			interaction_type_id: 298,
			latlong: "12.97,77.59",
			shop_type: "1",
		});
		await upload("3"); // selfie
		const business = await step({
			interaction_type_id: 522,
			name: "Asha Stores",
			authorized_signatory_name: "Asha Rao",
			current_address_line1: "1 MG Road",
			current_address_district: "Bengaluru",
			current_address_state: "Karnataka",
			current_address_pincode: "560001",
			company_type: "1",
		});
		assert.equal(business.response_type_id, 1567);
		await step({
			interaction_type_id: 454,
			bank_code: "SBIN",
			account: "123456789012",
			ifsc: "SBIN0001234",
		});
		await upload("7"); // passbook

		await step({ interaction_type_id: 170 });
		const key = (await step({ interaction_type_id: 10005 })).data;
		const encoded = `${[..."4826"].map((d) => key.pintwin_key[d]).join("")}|${key.key_id}`;
		assert.equal(
			(
				await step({
					interaction_type_id: 5,
					first_okekey: encoded,
					second_okekey: encoded,
					is_pintwin_user: 0,
				})
			).response_type_id,
			9
		);

		const esign = await step({ interaction_type_id: 287, agreement_id: 5 });
		assert.equal(esign.response_type_id, 1069);
		assert.equal(
			(
				await step({
					interaction_type_id: 293,
					document_id: esign.data.document_id,
					esign_completed: true,
				})
			).response_type_id,
			1615
		);

		const done = await profile();
		assert.equal(done.onboarding, 0);
		assert.equal(done.role_list, "");
	});

	it("resumes the half-done retailer at Selfie KYC", async () => {
		const token = await login("9000000005");
		const res = await post(
			"/",
			{ interaction_type_id: "151", mobile: "9000000005" },
			token
		);
		assert.equal(res.data.user_detail.role_list.split(",")[0], "12500");
	});
});
