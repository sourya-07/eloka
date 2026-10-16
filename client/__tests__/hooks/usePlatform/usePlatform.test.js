import { usePlatform } from "hooks";
import { renderHook } from "test-utils";

// The hook reads navigator.userAgent. jsdom's default agent depends on the machine
// running the tests (macOS says "darwin", Linux CI says "linux"), so set it per case.
const realUserAgent = window.navigator.userAgent;
const setUserAgent = (value) =>
	Object.defineProperty(window.navigator, "userAgent", {
		value,
		configurable: true,
	});

afterEach(() => setUserAgent(realUserAgent));

test.each([
	[
		"Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0",
		"Windows",
		false,
	],
	["Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) Safari/605.1", "Mac", true],
	["Mozilla/5.0 (Linux; Android 14; Pixel 8) Chrome/120.0", "Android", false],
	["Mozilla/5.0 (iPhone; CPU iPhone OS 17_0) Safari/604.1", "iPhone", false],
	["Mozilla/5.0 (X11; Ubuntu; rv:120.0) Firefox/120.0", "Unknown", false],
])("detects the platform from %s", (agent, platform, isMac) => {
	setUserAgent(agent);
	const { result } = renderHook(() => usePlatform());
	expect(result.current.platform).toBe(platform);
	expect(result.current.isMac).toBe(isMac);
});

test("reports Unknown when there is no user agent", () => {
	setUserAgent("");
	const { result } = renderHook(() => usePlatform());
	expect(result.current.platform).toBe("Unknown");
	expect(result.current.isMac).toBe(false);
});
