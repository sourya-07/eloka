/**
 * Liveness check for the container and the TLS edge. No backend call, no data.
 * @param req
 * @param res
 */
export default function handler(req, res) {
	res.setHeader("Cache-Control", "no-store");
	res.status(200).json({ status: "UP" });
}
