import { randomUUID } from "node:crypto";

/**
 * Mock access tokens carry the mobile number so a session survives a mock-server
 * restart: `mock.<mobile>.<random>`. They are NOT secure and exist only locally.
 */

const PREFIX = "mock";

/**
 * @param {string} mobile
 * @returns {string} A new mock token for this mobile number
 */
export const issueToken = (mobile) => `${PREFIX}.${mobile}.${randomUUID()}`;

/**
 * @param {string|undefined} authorizationHeader - e.g. `Bearer mock.9000000001.…`
 * @returns {string|undefined} The mobile number inside the token, if it is a mock token
 */
export const mobileFromAuthorization = (authorizationHeader) => {
	const token = String(authorizationHeader ?? "").replace(/^Bearer\s+/i, "");
	const [prefix, mobile] = token.split(".");
	return prefix === PREFIX && mobile ? mobile : undefined;
};
