import http from "node:http";
import { fail, ok } from "./envelope.js";
import { parseBody } from "./parseBody.js";
import { mobileFromAuthorization } from "./token.js";
import { UserStore } from "./UserStore.js";
import { authInteractions, authRoutes } from "../handlers/auth.js";
import {
	dashboardInteractions,
	dashboardTfRoutes,
} from "../handlers/dashboard.js";
import {
	onboardingInteractions,
	onboardingTfRoutes,
} from "../handlers/onboarding.js";
import { MENU } from "../data/catalog.js";

const INTERACTIONS = {
	...authInteractions,
	...onboardingInteractions,
	...dashboardInteractions,
};

const TF_ROUTES = { ...onboardingTfRoutes, ...dashboardTfRoutes };

const PATH_ROUTES = {
	...authRoutes,
	"/transactions/wlc": () => MENU,
	"/transactions/getip": () => ({ ip: "127.0.0.1" }),
};

/**
 * Local stand-in for EPS (Eko Platform Services), the backend the client talks to.
 * Speaks the same envelope and routes the frontend uses, backed by dummy data.
 *
 * Routing order: debug routes (`/__mock/*`) → tf-req REST tunnel → fixed paths → interaction_type_id.
 */
export class MockEpsServer {
	#store = new UserStore();
	#server;
	#log;

	/**
	 * @param {object} [options]
	 * @param {(line: string) => void} [options.log] - Logger (default: console.log)
	 */
	constructor({ log = console.log } = {}) {
		this.#log = log;
		this.#server = http.createServer((req, res) => {
			this.#handle(req, res).catch((err) => {
				this.#log(`[mock-eps] ERROR ${err.stack ?? err}`);
				this.#send(req, res, 500, fail("Mock server error"));
			});
		});
	}

	/** @returns {UserStore} The in-memory users (exposed for tests) */
	get store() {
		return this.#store;
	}

	/**
	 * @param {number} port
	 * @returns {Promise<number>} The port actually bound (useful with port 0 in tests)
	 */
	listen(port) {
		return new Promise((resolve) => {
			this.#server.listen(port, () =>
				resolve(this.#server.address().port)
			);
		});
	}

	/** @returns {Promise<void>} */
	close() {
		return new Promise((resolve) => this.#server.close(() => resolve()));
	}

	async #handle(req, res) {
		if (req.method === "OPTIONS") return this.#send(req, res, 204);

		const url = new URL(req.url, `http://${req.headers.host}`);
		const raw = await readRaw(req);
		const body = {
			...Object.fromEntries(url.searchParams),
			...parseBody(raw, req.headers["content-type"]),
		};
		const tokenMobile = mobileFromAuthorization(req.headers.authorization);
		const ctx = {
			body,
			path: url.pathname,
			headers: req.headers,
			origin: `http://${req.headers.host}`,
			store: this.#store,
			user: [
				tokenMobile,
				body.mobile,
				body.initiator_id,
				body.customer_id,
			]
				.map((mobile) => this.#store.find(mobile))
				.find(Boolean),
		};

		if (url.pathname.startsWith("/__mock"))
			return this.#debugRoute(req, res, ctx);

		const { label, response } = this.#dispatch(ctx);
		if (process.env.MOCK_EPS_DEBUG) {
			this.#log(`[mock-eps]   request ${JSON.stringify(body)}`);
		}
		this.#log(
			`[mock-eps] ${req.method} ${url.pathname} ${label} → ${summarize(response)}`
		);
		return this.#send(req, res, 200, response);
	}

	/**
	 * Pick the handler for a request.
	 * @param {object} ctx
	 * @returns {{label: string, response: object}} What was matched and the response body
	 */
	#dispatch(ctx) {
		const tfUri =
			ctx.headers["tf-req-uri"] ??
			Object.keys(TF_ROUTES).find((uri) => ctx.path.endsWith(uri));
		if (tfUri && TF_ROUTES[tfUri]) {
			return { label: `tf ${tfUri}`, response: TF_ROUTES[tfUri](ctx) };
		}

		if (PATH_ROUTES[ctx.path]) {
			return { label: ctx.path, response: PATH_ROUTES[ctx.path](ctx) };
		}

		const id = ctx.body.interaction_type_id;
		if (id !== undefined && INTERACTIONS[id]) {
			return { label: `#${id}`, response: INTERACTIONS[id](ctx) };
		}

		const what = tfUri
			? `tf ${tfUri}`
			: id !== undefined
				? `#${id}`
				: ctx.path;
		return {
			label: `${what} (NOT MOCKED)`,
			response: fail(
				`[mock-eps] ${what} has no mock yet. Add it under client/mock-eps/handlers.`
			),
		};
	}

	#debugRoute(req, res, ctx) {
		switch (ctx.path) {
			case "/__mock/health":
				return this.#send(req, res, 200, ok({ service: "mock-eps" }));
			case "/__mock/users":
				return this.#send(req, res, 200, this.#store.all());
			case "/__mock/reset":
				this.#store.reset();
				this.#log("[mock-eps] users reset to seed data");
				return this.#send(
					req,
					res,
					200,
					ok({}, { message: "Reset to seed users" })
				);
			case "/__mock/digilocker":
				res.writeHead(200, {
					"Content-Type": "text/html; charset=utf-8",
				});
				return res.end(DIGILOCKER_PAGE);
			default:
				return this.#send(req, res, 404, fail("Unknown mock route"));
		}
	}

	#send(req, res, status, payload) {
		res.writeHead(status, {
			"Content-Type": "application/json",
			"Access-Control-Allow-Origin": req.headers.origin ?? "*",
			"Access-Control-Allow-Credentials": "true",
			"Access-Control-Allow-Methods":
				"GET, POST, PUT, PATCH, DELETE, OPTIONS",
			"Access-Control-Allow-Headers":
				req.headers["access-control-request-headers"] ??
				"authorization, content-type, tf-req-uri, tf-req-uri-root-path, tf-req-method",
			Vary: "Origin",
		});
		res.end(payload === undefined ? undefined : JSON.stringify(payload));
	}
}

/**
 * @param {http.IncomingMessage} req
 * @returns {Promise<Buffer>} The full request body
 */
const readRaw = (req) =>
	new Promise((resolve, reject) => {
		const chunks = [];
		req.on("data", (chunk) => chunks.push(chunk));
		req.on("end", () => resolve(Buffer.concat(chunks)));
		req.on("error", reject);
	});

/**
 * @param {object|Array} response
 * @returns {string} Short log summary of a response
 */
const summarize = (response) => {
	if (Array.isArray(response)) return `${response.length} items`;
	if (response?.response_type_id > 0)
		return `type ${response.response_type_id}`;
	return response?.status === 1 ? `FAIL "${response.message}"` : "ok";
};

const DIGILOCKER_PAGE = `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mock DigiLocker</title>
<style>body{font-family:system-ui,sans-serif;max-width:32rem;margin:4rem auto;padding:0 1rem;line-height:1.5}</style></head>
<body><h1>Mock DigiLocker</h1>
<p>Consent recorded. This page stands in for DigiLocker on the local mock; no real Aadhaar data is used.</p>
<p>Close this tab and continue in the app.</p></body></html>`;
