import { apiBaseUrl, forwardHeaders, relayStatus } from "helpers/proxyHelper";

const backend = (status, requestId) => ({
	status,
	headers: { get: (name) => (name === "x-request-id" ? requestId : null) },
});

describe("proxyHelper", () => {
	it("forwards the token, the request id and the browser IP", () => {
		const headers = forwardHeaders({
			headers: {
				authorization: "Bearer abc",
				"x-request-id": "req-1",
			},
			socket: { remoteAddress: "203.0.113.7" },
		});
		expect(headers).toEqual({
			Authorization: "Bearer abc",
			"X-Request-Id": "req-1",
			"X-Forwarded-For": "203.0.113.7",
		});
	});

	it("appends the browser IP to an existing X-Forwarded-For chain", () => {
		const headers = forwardHeaders({
			headers: { "x-forwarded-for": "198.51.100.1" },
			socket: { remoteAddress: "10.0.0.5" },
		});
		expect(headers["X-Forwarded-For"]).toBe("198.51.100.1, 10.0.0.5");
		expect(headers.Authorization).toBeUndefined();
	});

	it("passes 401 through so the client refreshes its token", () => {
		const res = { setHeader: jest.fn() };
		expect(relayStatus(backend(401, "req-2"), res)).toBe(401);
		expect(res.setHeader).toHaveBeenCalledWith("X-Request-Id", "req-2");
	});

	it("keeps other statuses at 200 (errors travel in the envelope)", () => {
		const res = { setHeader: jest.fn() };
		expect(relayStatus(backend(500), res)).toBe(200);
		expect(res.setHeader).not.toHaveBeenCalled();
	});

	describe("apiBaseUrl", () => {
		const saved = { ...process.env };
		afterEach(() => {
			process.env = { ...saved };
		});

		it("prefers the internal URL inside a container", () => {
			process.env.NEXT_PUBLIC_API_BASE_URL = "https://api.example.in";
			process.env.API_INTERNAL_BASE_URL = "http://server:8080/";
			expect(apiBaseUrl()).toBe("http://server:8080");
		});

		it("falls back to the public URL", () => {
			delete process.env.API_INTERNAL_BASE_URL;
			process.env.NEXT_PUBLIC_API_BASE_URL = "http://localhost:8090";
			expect(apiBaseUrl()).toBe("http://localhost:8090");
		});
	});
});
