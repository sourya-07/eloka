import { useMediaQuery } from "@chakra-ui/media-query";
import { act, waitFor } from "@testing-library/react";
import { TransactionTypes } from "constants";
import { History } from "page-components/History";
import { pageRender } from "test-utils";

// The logged-in mock user has no accounts; each test sets the list History reads.
let mockAccountList;

// "contexts" imports itself, so the real module is loaded lazily on first use.
jest.mock("contexts", () => {
	const useUser = () => {
		const user = jest.requireActual("contexts").useUser();
		return {
			...user,
			userData: {
				...user.userData,
				accountDetails: { account_list: mockAccountList },
			},
		};
	};
	return new Proxy(
		{},
		{
			get: (_target, key) => {
				if (key === "__esModule") return true;
				if (key === "useUser") return useUser;
				return jest.requireActual("contexts")[key];
			},
		}
	);
});

// History requests hang until aborted, so the spinner state can be checked mid-request.
jest.mock("helpers", () => {
	const actual = jest.requireActual("helpers");
	return {
		...actual,
		fetcher: (url, options, ...rest) => {
			if (
				options?.body?.interaction_type_id !==
				TransactionTypes.GET_TRANSACTION_HISTORY
			) {
				return actual.fetcher(url, options, ...rest);
			}
			return new Promise((_resolve, reject) => {
				options.controller?.signal.addEventListener("abort", () => {
					const err = new Error("aborted");
					err.name = "AbortError";
					reject(err);
				});
			});
		},
	};
});

// While loading, the table body shows placeholder rows
const loadingRowCount = (container) =>
	container.querySelectorAll("tbody tr").length;

describe("History loading spinner", () => {
	beforeEach(() => {
		// Desktop layout (jest.setup.js defaults to small screens): the table shows
		// placeholder rows while loading, mobile cards show nothing
		useMediaQuery.mockReturnValue([false]);
	});

	it("stops when the account list empties during a request", async () => {
		mockAccountList = [{ id: 1 }];
		const { container, rerender } = pageRender(<History />);

		await waitFor(() =>
			expect(loadingRowCount(container)).toBeGreaterThan(0)
		);

		// The effect re-runs: the pending request is aborted, the new one has no account
		mockAccountList = [];
		await act(async () => {
			rerender(<History />);
		});

		await waitFor(() => expect(loadingRowCount(container)).toBe(0));
	});
});
