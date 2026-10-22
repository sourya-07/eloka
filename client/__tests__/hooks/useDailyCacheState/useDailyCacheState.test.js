import { MockUser } from "../../fixtures/session";
import { useDailyCacheState } from "@/hooks";
import { act, renderHook } from "test-utils";

const KEY = "test-daily-cache";

/**
 * The "today" stamp used by useDailyCacheState (day-of-month + "-" + month)
 * @param {Date} [dt] - Date to stamp
 * @returns {string} Cache date stamp
 */
const stampFor = (dt = new Date()) => dt.getDate() + "-" + dt.getMonth();

describe("useDailyCacheState", () => {
	beforeEach(() => {
		window.localStorage.clear();
	});

	it("returns [value, setValue, isValid]", () => {
		const { result } = renderHook(() => useDailyCacheState(KEY));
		expect(result.current).toBeDefined();
		expect(result.current.length).toBe(3);
		expect(result.current[0]).toBeUndefined();
		expect(result.current[1]).toBeInstanceOf(Function);
		expect(typeof result.current[2]).toBe("boolean");
	});

	it("caches a value for today and the current user", () => {
		const { result } = renderHook(() => useDailyCacheState(KEY));

		act(() => {
			result.current[1]({ balance: 100 });
		});

		expect(result.current[0]).toEqual({ balance: 100 });
		expect(result.current[2]).toBe(true);
		expect(JSON.parse(window.localStorage.getItem(KEY))).toMatchObject({
			cachedAt: stampFor(),
			data: { balance: 100 },
		});
	});

	it("treats a value cached on an earlier day as stale", () => {
		const yesterday = new Date();
		yesterday.setDate(yesterday.getDate() - 1);
		window.localStorage.setItem(
			KEY,
			JSON.stringify({
				cachedAt: stampFor(yesterday),
				id: MockUser.userId,
				data: "old",
			})
		);

		const { result } = renderHook(() => useDailyCacheState(KEY));

		expect(result.current[0]).toBe("old");
		expect(result.current[2]).toBe(false);
	});

	it("treats a value cached for another user as stale", () => {
		window.localStorage.setItem(
			KEY,
			JSON.stringify({
				cachedAt: stampFor(),
				id: "some-other-user",
				data: "other",
			})
		);

		const { result } = renderHook(() => useDailyCacheState(KEY));

		expect(result.current[2]).toBe(false);
	});
});
