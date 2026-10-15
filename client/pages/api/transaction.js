/**
 *
 * @param req
 * @param res
 */
// /pages/api/transaction.js

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
			const tfMethod = (req.headers["tf-req-method"] || "GET").toUpperCase();

			// Derive backend origin from NEXT_PUBLIC_API_BASE_URL
			const backendOrigin = new URL(process.env.NEXT_PUBLIC_API_BASE_URL).origin;
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
				headers: {
					...(req.headers.authorization && {
						Authorization: req.headers.authorization,
					}),
				},
			});

			const data = await response.json();
			return res.status(200).json(data);
		}

		// Standard call: POST body to backend
		const response = await fetch(
			process.env.NEXT_PUBLIC_API_BASE_URL,
			{
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					...(req.headers.authorization && {
						Authorization: req.headers.authorization,
					}),
				},
				body: JSON.stringify(req.body),
			}
		);

		const data = await response.json();

		return res.status(200).json(data);
	} catch (err) {
		console.error("Proxy error:", err);
		return res.status(500).json({ error: "Internal Server Error" });
	}
}
