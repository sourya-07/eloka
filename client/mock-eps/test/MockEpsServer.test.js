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
/** Skip a block when testing the real server before it implements that phase. */
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

/** POST JSON, the way pages/api/transaction.js does. */
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

/** Log in with the OTP flow and return the access token. */
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
		const res = await (
			await fetch(
				`${base}/ekoicici/v1/network/agent-list?user_code=30000003`
			)
		).json();
		assert.ok(res.data.csp_list.length > 0);
	});
});

describe("self-onboarding journey", until(5), () => {
	it("takes a new retailer from role selection to onboarded", async () => {
		const mobile = "9000000004";
		const profile = async () =>
			(await post("/", { interaction_type_id: "151", mobile })).data
				.user_detail;

		const role = await post("/", {
			interaction_type_id: 521,
			mobile,
			applicant_type: 0,
		});
		assert.equal(role.response_type_id, 1566);
		assert.equal((await profile()).onboarding, 1);
		assert.equal((await profile()).onboarding_steps.length, 7);

		const upload = (docType) => {
			const form = new FormData();
			form.append(
				"form-data",
				new URLSearchParams({
					interaction_type_id: "523",
					mobile,
					doc_type: docType,
				}).toString()
			);
			form.append("file1", new Blob(["fake"]), "doc.png");
			return fetch(base, { method: "POST", body: form }).then((r) =>
				r.json()
			);
		};

		assert.equal((await upload("1")).response_type_id, 1569); // Aadhaar
		assert.equal((await upload("2")).response_type_id, 1569); // PAN
		await post("/", { interaction_type_id: 298, mobile });
		await upload("3"); // selfie
		assert.equal(
			(await post("/", { interaction_type_id: 522, mobile }))
				.response_type_id,
			1567
		);
		await post("/", { interaction_type_id: 454, mobile });
		await upload("7"); // passbook
		assert.equal(
			(await post("/", { interaction_type_id: 5, mobile }))
				.response_type_id,
			9
		);
		assert.equal(
			(await post("/", { interaction_type_id: 287, mobile }))
				.response_type_id,
			1069
		);
		assert.equal(
			(await post("/", { interaction_type_id: 293, mobile }))
				.response_type_id,
			1615
		);

		const done = await profile();
		assert.equal(done.onboarding, 0);
		assert.equal(done.role_list, "");
	});

	it("resumes the half-done retailer at Selfie KYC", async () => {
		const res = await post("/", {
			interaction_type_id: "151",
			mobile: "9000000005",
		});
		assert.equal(res.data.user_detail.role_list.split(",")[0], "12500");
	});
});
