// k6 load test: the calls every home page makes after login (P6).
//   k6 run server/load/home.js                                   (server on :8090, local profile)
//   docker run --rm -i --network host grafana/k6 run - < server/load/home.js
//   BASE_URL=https://api.<root> ORG_SECRET=... MOBILES=... k6 run server/load/home.js  (staging)
// Login is rate-limited per mobile and IP (by design), so setup() logs each user in once and
// the virtual users reuse those sessions.
import http from "k6/http";
import { check, fail } from "k6";

const BASE = (__ENV.BASE_URL || "http://localhost:8090").replace(/\/$/, "");
const SUBDOMAIN = __ENV.SUBDOMAIN || "demo";
const ORG_SECRET = __ENV.ORG_SECRET || "eloka-local-org-meta-secret";
const MOBILES = (__ENV.MOBILES || "9000000001,9000000002,9000000003").split(",");
const OTP = __ENV.OTP || "123456";

export const options = {
	scenarios: {
		home: {
			executor: "ramping-vus",
			stages: [
				{ duration: "20s", target: 20 },
				{ duration: "1m", target: 50 },
				{ duration: "20s", target: 0 },
			],
		},
	},
	thresholds: {
		http_req_failed: ["rate<0.01"],
		http_req_duration: ["p(95)<300"],
		checks: ["rate>0.99"],
	},
};

const json = { headers: { "Content-Type": "application/json" } };
const post = (path, body, token) =>
	http.post(BASE + path, JSON.stringify(body), {
		headers: { ...json.headers, ...(token && { Authorization: `Bearer ${token}` }) },
		tags: { call: String(body.interaction_type_id ?? path) },
	});

export function setup() {
	const org = post("/", { interaction_type_id: "677", sub_domain: SUBDOMAIN, secret: ORG_SECRET }).json();
	if (!org.org_token) fail(`org lookup failed: ${JSON.stringify(org)}`);
	return MOBILES.map((mobile) => {
		post("/", { interaction_type_id: "515", mobile, org_token: org.org_token });
		const verified = post("/", { interaction_type_id: "518", mobile, otp: OTP, org_token: org.org_token }).json();
		const token = verified?.data?.otp_verification_token;
		if (!token) fail(`login failed for ${mobile.slice(-4)}`);
		return { mobile, token };
	});
}

export default function (sessions) {
	const { mobile, token } = sessions[(__VU - 1) % sessions.length];
	const ok = (res, name) => check(res, { [`${name} ok`]: (r) => r.status === 200 && r.json("status") === 0 });

	ok(post("/", { interaction_type_id: "151", mobile }, token), "profile");
	ok(post("/transactions/do", { interaction_type_id: 9 }, token), "wallet");
	ok(post("/transactions/do", { interaction_type_id: 10010 }, token), "notifications");
	ok(post("/transactions/do", { interaction_type_id: 154 }, token), "recent transactions");
	check(post("/transactions/wlc", {}, token), { "menu ok": (r) => r.status === 200 && Array.isArray(r.json()) });
}
