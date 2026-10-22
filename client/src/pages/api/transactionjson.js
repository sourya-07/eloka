import { apiBaseUrl, forwardHeaders, relayStatus } from "@/lib/proxyHelper";

/**
 * Proxy for JSON `dojson` calls (tf-req-* headers are passed through).
 * @param req
 * @param res
 */
export default async function handler(req, res) {
	if (req.method !== "POST") {
		return res.status(405).json({ error: "Method not allowed" });
	}

	try {
		const tfReqHeaders = {};
		["tf-req-uri-root-path", "tf-req-uri", "tf-req-method"].forEach((h) => {
			if (req.headers[h]) tfReqHeaders[h] = req.headers[h];
		});

		const response = await fetch(apiBaseUrl() + "/transactions/dojson", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
				...forwardHeaders(req),
				...tfReqHeaders,
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
