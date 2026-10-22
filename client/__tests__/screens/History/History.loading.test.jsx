import { useMediaQuery } from "@chakra-ui/media-query";
import { act, waitFor } from "@testing-library/react";
import { TransactionTypes } from "@/constants";
import { History } from "@/screens/History";
import { pageRender } from "test-utils";

// The logged-in mock user has no accounts; each test sets the list History reads.
let mockAccountList;
// When set, history requests answer with these transactions instead of hanging.
let mockTransactions;

// "contexts" imports itself, so the real module is loaded lazily on first use.
jest.mock("@/contexts", () => {
	const useUser = () => {
		const user = jest.requireActual("@/contexts").useUser();
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
				return jest.requireActual("@/contexts")[key];
			},
		}
	);
});

// History requests hang until aborted (unless mockTransactions is set), so the spinner
// state can be checked mid-request. Mock the module that defines fetcher, not the "@/lib"
// barrel: spreading the whole barrel loads CopilotProvider and hits an import cycle.
jest.mock("@/lib/apiHelper", () => {
	const actual = jest.requireActual("@/lib/apiHelper");
	return {
		...actual,
		fetcher: (url, options, ...rest) => {
			if (
				options?.body?.interaction_type_id !==
				TransactionTypes.GET_TRANSACTION_HISTORY
			) {
				return actual.fetcher(url, options, ...rest);
			}
			if (mockTransactions) {
				return Promise.resolve({
					data: { transaction_list: mockTransactions },
				});
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

// Table body rows: 5 placeholder rows while loading, else the transactions' rows
const tableRowCount = (container) =>
	container.querySelectorAll("tbody tr").length;

describe("History loading spinner", () => {
	beforeEach(() => {
		// Desktop layout (jest.setup.js defaults to small screens): the table shows
		// placeholder rows while loading, mobile cards show nothing
		useMediaQuery.mockReturnValue([false]);
		mockTransactions = undefined;
	});

	it("stops when the account list empties during a request", async () => {
		mockAccountList = [{ id: 1 }];
		const { container, rerender } = pageRender(<History />);

		await waitFor(() =>
			expect(tableRowCount(container)).toBeGreaterThan(0)
		);

		// The effect re-runs: the pending request is aborted, the new one has no account
		mockAccountList = [];
		await act(async () => {
			rerender(<History />);
		});

		await waitFor(() => expect(tableRowCount(container)).toBe(0));
	});

	it("clears the previous account's rows when the account list empties", async () => {
		mockAccountList = [{ id: 1 }];
		mockTransactions = [{ tid: "1001", amount: "100" }];
		const { container, rerender, findByText, queryByText } = pageRender(
			<History />
		);

		// Loaded: the 5 placeholder rows are replaced by the transaction's rows
		await waitFor(() => {
			const rows = tableRowCount(container);
			expect(rows).toBeGreaterThan(0);
			expect(rows).not.toBe(5);
		});
		expect(queryByText("Nothing Found")).not.toBeInTheDocument();

		mockAccountList = [];
		await act(async () => {
			rerender(<History />);
		});

		expect(await findByText("Nothing Found")).toBeInTheDocument();
	});
});
