/**
 * Shared behaviour of the API proxies in `pages/api/*` (FE-1): which headers go to the
 * backend and which status comes back.
 */

/**
 * Backend URL for the server-side proxies. In a container the public API host may not be
 * reachable from inside, so `API_INTERNAL_BASE_URL` (e.g. http://server:8080) wins when set.
 * The browser keeps using NEXT_PUBLIC_API_BASE_URL for its direct calls.
 * @returns {string} Base URL without a trailing slash
 */
export const apiBaseUrl = () =>
	(
		process.env.API_INTERNAL_BASE_URL ||
		process.env.NEXT_PUBLIC_API_BASE_URL ||
		""
	).replace(/\/$/, "");

/**
 * Headers forwarded to the backend: the caller's token, the request id for log tracing,
 * and the browser's IP appended to X-Forwarded-For (the server trusts it only from known proxies).
 * @param {import("http").IncomingMessage} req - Incoming request
 * @returns {Record<string, string>} Headers to send
 */
export const forwardHeaders = (req) => {
	const clientIp = req.socket?.remoteAddress;
	const priorHops = req.headers["x-forwarded-for"];
	return {
		...(req.headers.authorization && {
			Authorization: req.headers.authorization,
		}),
		...(req.headers["x-request-id"] && {
			"X-Request-Id": req.headers["x-request-id"],
		}),
		...(clientIp && {
			"X-Forwarded-For": priorHops
				? `${priorHops}, ${clientIp}`
				: clientIp,
		}),
	};
};

/**
 * Status to answer the browser with. 401 is passed through so `fetcher` refreshes the
 * token; everything else stays 200 because errors travel inside the EPS envelope.
 * @param {Response} backendRes - Backend response
 * @param {import("http").ServerResponse} res - Response to the browser (gets X-Request-Id)
 * @returns {number} HTTP status
 */
export const relayStatus = (backendRes, res) => {
	const requestId = backendRes.headers.get("x-request-id");
	if (requestId) res.setHeader("X-Request-Id", requestId);
	return backendRes.status === 401 ? 401 : 200;
};
