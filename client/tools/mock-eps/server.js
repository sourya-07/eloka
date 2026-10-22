/**
 * Start the mock EPS server: `npm run mock` (port 4010, or MOCK_EPS_PORT).
 * Usually started for you by `npm run dev:mock`.
 */
import { MockEpsServer } from "./lib/MockEpsServer.js";
import { MOCK_OTP, NEW_USER_MOBILE, SEED_USERS } from "./data/users.js";

const port = Number(process.env.MOCK_EPS_PORT ?? 4010);
const server = new MockEpsServer();
const boundPort = await server.listen(port);

console.log(
	`[mock-eps] listening on http://localhost:${boundPort}  (OTP for every login: ${MOCK_OTP})`
);
for (const user of SEED_USERS) {
	console.log(`[mock-eps]   ${user.mobile}  ${user.persona}`);
}
console.log(
	`[mock-eps]   ${NEW_USER_MOBILE}  New user (or any other 10-digit number): self-onboarding`
);

const shutdown = () => server.close().then(() => process.exit(0));
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
