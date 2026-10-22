/**
 * `npm run dev:mock`: run the mock EPS server and the Next.js dev server together,
 * with the app pointed at the mock. Ctrl+C stops both.
 *
 * Env vars set here override .env.development and .env.local for this run only.
 */
import { spawn } from "node:child_process";

const MOCK_PORT = process.env.MOCK_EPS_PORT ?? "4010";
const APP_PORT = process.env.PORT ?? "3002";

const appEnv = {
	...process.env,
	NEXT_PUBLIC_API_BASE_URL: `http://localhost:${MOCK_PORT}`,
	NEXT_PUBLIC_MOCK_LOGIN: "false", // show the real login screen
	WLC_MOCK_HOST: "", // use the built-in local org (src/lib/fetchOrgDetailsHelper.js)
};

const children = [
	spawn(process.execPath, ["tools/mock-eps/server.js"], {
		stdio: "inherit",
		env: { ...process.env, MOCK_EPS_PORT: MOCK_PORT },
	}),
	spawn("npx", ["next", "dev", "-p", APP_PORT], {
		stdio: "inherit",
		env: appEnv,
	}),
];

const stopAll = (code = 0) => {
	for (const child of children) child.kill("SIGTERM");
	process.exit(code);
};

for (const child of children) {
	child.on("exit", (code) => stopAll(code ?? 0));
}
process.on("SIGINT", () => stopAll(0));
process.on("SIGTERM", () => stopAll(0));
