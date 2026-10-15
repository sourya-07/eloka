export default async function handler(req, res) {
	if (req.method !== "POST") {
		return res.status(405).json({ error: "Method not allowed" });
	}

	try {
		const tfReqHeaders = {};
		["tf-req-uri-root-path", "tf-req-uri", "tf-req-method"].forEach((h) => {
			if (req.headers[h]) tfReqHeaders[h] = req.headers[h];
		});

		const response = await fetch(
			process.env.NEXT_PUBLIC_API_BASE_URL + "/transactions/dojson",
			{
				method: "POST",
				headers: {
					"Content-Type": "application/json",
					...(req.headers.authorization && {
						Authorization: req.headers.authorization,
					}),
					...tfReqHeaders,
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
