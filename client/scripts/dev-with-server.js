/**
 * `npm run dev:server`: the website on :3002 talking to the real backend in ../server
 * (start it first: `cd ../server && ./gradlew bootRun`). Uses the server's local dummy users,
 * OTP 123456. Open http://demo.eloka.localhost:3002 (or http://localhost:3002).
 *
 * Env vars set here override .env.development and .env.local for this run only.
 */
import { spawn } from "node:child_process";

const SERVER = process.env.ELOKA_SERVER_URL ?? "http://localhost:8090";
const APP_PORT = process.env.PORT ?? "3002";

const env = {
	...process.env,
	NEXT_PUBLIC_API_BASE_URL: SERVER,
	ORG_META_SERVICE: `${SERVER}/`,
	ORG_META_SERVICE_SECRET:
		process.env.ORG_META_SERVICE_SECRET ?? "eloka-local-org-meta-secret",
	WLC_SUBDOMAIN_ROOT: "eloka.localhost",
	// Plain localhost has no tenant sub-domain, so resolve it as the "demo" org.
	WLC_MOCK_HOST: process.env.WLC_MOCK_HOST ?? "demo.eloka.localhost",
	NEXT_PUBLIC_MOCK_LOGIN: "false",
};

const next = spawn("npx", ["next", "dev", "-p", APP_PORT], {
	stdio: "inherit",
	env,
});
next.on("exit", (code) => process.exit(code ?? 0));
process.on("SIGINT", () => next.kill("SIGINT"));
process.on("SIGTERM", () => next.kill("SIGTERM"));
