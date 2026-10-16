/**
 *
 * @param req
 * @param res
 */
// /pages/api/transaction.js
import { apiBaseUrl, forwardHeaders, relayStatus } from "helpers/proxyHelper";

/**
 *
 * @param req
 * @param res
 */
export default async function handler(req, res) {
	if (req.method !== "POST") {
		return res.status(405).json({ error: "Method not allowed" });
	}

	try {
		const isTfReq = !!req.headers["tf-req-uri"];

		if (isTfReq) {
			// tf-req-* calls: construct a direct GET request to the backend endpoint
			const tfRootPath = req.headers["tf-req-uri-root-path"] || "";
			const tfUri = req.headers["tf-req-uri"] || "";
			const tfMethod = (
				req.headers["tf-req-method"] || "GET"
			).toUpperCase();

			// Backend origin (internal URL inside a container, see apiBaseUrl)
			const backendOrigin = new URL(apiBaseUrl()).origin;
			const targetUrl = new URL(backendOrigin + tfRootPath + tfUri);

			// Append all body params as query string params
			const bodyParams = req.body || {};
			Object.entries(bodyParams).forEach(([key, value]) => {
				if (value !== undefined && value !== null && value !== "") {
					targetUrl.searchParams.set(key, String(value));
				}
			});

			const response = await fetch(targetUrl.toString(), {
				method: tfMethod,
				headers: forwardHeaders(req),
			});

			const data = await response.json();
			return res.status(relayStatus(response, res)).json(data);
		}

		// Standard call: POST body to backend
		const response = await fetch(apiBaseUrl(), {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				...forwardHeaders(req),
			},
			body: JSON.stringify(req.body),
		});

		const data = await response.json();

		return res.status(relayStatus(response, res)).json(data);
	} catch (err) {
		console.error("Proxy error:", err);
		return res.status(500).json({ error: "Internal Server Error" });
	}
}
