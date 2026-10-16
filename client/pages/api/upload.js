/**
 * Proxy for multipart file upload requests.
 * Forwards the raw multipart/form-data body to the backend upload endpoint.
 *
 * Must disable Next.js body parser so the raw stream is forwarded intact
 * (the multipart boundary in Content-Type must match the raw body).
 */
import { apiBaseUrl, forwardHeaders, relayStatus } from "helpers/proxyHelper";

export const config = {
	api: {
		bodyParser: false,
	},
};

/**
 * Read the raw request body as a Buffer (Node.js readable stream).
 * @param {import("http").IncomingMessage} req
 * @returns {Promise<Buffer>}
 */
function readRawBody(req) {
	return new Promise((resolve, reject) => {
		const chunks = [];
		req.on("data", (chunk) => chunks.push(chunk));
		req.on("end", () => resolve(Buffer.concat(chunks)));
		req.on("error", reject);
	});
}

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
		const body = await readRawBody(req);

		const backendRes = await fetch(apiBaseUrl(), {
			method: "POST",
			headers: {
				// Forward Content-Type including the multipart boundary
				"content-type": req.headers["content-type"],
				...forwardHeaders(req),
			},
			body,
		});

		const text = await backendRes.text();

		let data;
		try {
			data = JSON.parse(text);
		} catch {
			console.error(
				"[upload proxy] non-JSON response from backend:",
				text
			);
			return res
				.status(backendRes.status || 500)
				.json({ error: "Unexpected response from backend", raw: text });
		}

		return res.status(relayStatus(backendRes, res)).json(data);
	} catch (err) {
		console.error("[upload proxy] error:", err);
		return res.status(500).json({ error: "Internal Server Error" });
	}
}
