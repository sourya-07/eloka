import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import { Preview } from "page-components/products/bbps/Preview";
import { BbpsContext } from "page-components/products/bbps/context/BbpsContext";
import { BbpsState } from "page-components/products/bbps/context/types";
import { useBbpsNavigation } from "page-components/products/bbps/hooks/useBbpsNavigation";

// Mock the navigation hook
jest.mock("page-components/products/bbps/hooks/useBbpsNavigation", () => ({
	useBbpsNavigation: jest.fn(),
}));

/**
 * Build a bill in the shape produced by transformBillData
 * @param {string} billid - Bill ID shown on the card
 * @param {string} billNumber - Bill number
 * @param {number} amount - Bill amount
 * @param {object} amountRules - Amount constraints
 * @returns {object} Bill for the preview step
 */
const makeBill = (billid, billNumber, amount, amountRules) => ({
	billid,
	label: `${billNumber} - 15/01/2024`,
	amount,
	amountRules,
	billNumber,
	billDate: "01/01/2024",
	billDueDate: "15/01/2024",
	customerName: `Customer ${billNumber}`,
});

describe("BBPS Preview Component", () => {
	// Mock navigation functions
	const mockNavigation = {
		goSearch: jest.fn(),
		goPreview: jest.fn(),
		goPayment: jest.fn(),
		goStatus: jest.fn(),
		backToGrid: jest.fn(),
		goPreviousStep: jest.fn(),
	};

	beforeEach(() => {
		jest.clearAllMocks();
		(useBbpsNavigation as jest.Mock).mockReturnValue(mockNavigation);
	});

	// Test for single selection mode
	test("renders single selection mode correctly", () => {
		// Mock state for single selection
		const mockState: Partial<BbpsState> = {
			currentStep: "preview",
			searchFormData: {},
			selectedProduct: {
				id: "electricity",
				label: "Electricity",
				desc: "Electricity Bill Payment",
				icon: "electricity",
				url: "/electricity",
				searchFields: [],
			},
			billFetchResult: {
				selectionMode: "single",
				bills: [
					makeBill("bill-1", "EB-123", 1500, {
						min: 500,
						max: 5000,
						multiple: 100,
					}),
				],
			},
			selectedBills: [],
			totalAmount: 0,
			isLoading: false,
			error: null,
			paymentStatus: null,
			useMockData: true,
			mockResponseType: "success",
		};

		const mockDispatch = jest.fn();

		render(
			<BbpsContext.Provider
				value={{
					state: mockState as BbpsState,
					dispatch: mockDispatch,
				}}
			>
				<Preview />
			</BbpsContext.Provider>
		);

		// Check if the title is correct
		expect(screen.getByText("Select a bill to pay")).toBeInTheDocument();

		// Check if the bill is displayed (bill ID and its details)
		expect(screen.getByText("bill-1")).toBeInTheDocument();
		expect(screen.getByText("Customer EB-123")).toBeInTheDocument();

		// Check if amount rules are not displayed initially (until selected)
		expect(screen.queryByText(/Amount range:/)).not.toBeInTheDocument();

		// Select the bill
		const radioButton = screen.getByRole("radio");
		fireEvent.click(radioButton);

		// Verify dispatch was called to select the bill
		expect(mockDispatch).toHaveBeenCalledWith({
			type: "TOGGLE_BILL_SELECTION",
			billid: "bill-1",
		});
	});

	// Test for multi-optional selection mode
	test("renders multiOptional mode correctly", () => {
		// Mock state for multi-optional selection
		const mockState: Partial<BbpsState> = {
			currentStep: "preview",
			searchFormData: {},
			selectedProduct: {
				id: "credit-card",
				label: "Credit Card",
				desc: "Credit Card Bill Payment",
				icon: "credit-card",
				url: "/credit-card",
				searchFields: [],
			},
			billFetchResult: {
				selectionMode: "multiOptional",
				arePartialPaymentsAllowed: true,
				bills: [
					makeBill("bill-1", "CC-123", 5000, { min: 500, max: 5000 }),
					makeBill("bill-2", "CC-456", 2500, {
						min: 200,
						max: 2500,
						multiple: 50,
					}),
				],
			},
			selectedBills: [],
			totalAmount: 0,
			isLoading: false,
			error: null,
			paymentStatus: null,
			useMockData: true,
			mockResponseType: "success",
		};

		const mockDispatch = jest.fn();

		render(
			<BbpsContext.Provider
				value={{
					state: mockState as BbpsState,
					dispatch: mockDispatch,
				}}
			>
				<Preview />
			</BbpsContext.Provider>
		);

		// Check if the title is correct
		expect(
			screen.getByText("Select one or more bills to pay")
		).toBeInTheDocument();

		// Check if both bills are displayed
		expect(screen.getByText("bill-1")).toBeInTheDocument();
		expect(screen.getByText("Customer CC-123")).toBeInTheDocument();
		expect(screen.getByText("bill-2")).toBeInTheDocument();
		expect(screen.getByText("Customer CC-456")).toBeInTheDocument();

		// Check if checkboxes are present
		const checkboxes = screen.getAllByRole("checkbox");
		expect(checkboxes.length).toBe(2);

		// Select the first bill
		fireEvent.click(checkboxes[0]);

		// Verify dispatch was called to select the bill
		expect(mockDispatch).toHaveBeenCalledWith({
			type: "TOGGLE_BILL_SELECTION",
			billid: "bill-1",
		});
	});

	// Test for multi-mandatory selection mode
	test("renders multi-mandatory selection mode correctly", () => {
		// Mock state for multi-mandatory selection
		const mockState: Partial<BbpsState> = {
			currentStep: "preview",
			searchFormData: {},
			selectedProduct: {
				id: "loan",
				label: "Loan",
				desc: "Loan Payment",
				icon: "loan",
				url: "/loan",
				searchFields: [],
			},
			billFetchResult: {
				selectionMode: "multiMandatory",
				bills: [
					makeBill("bill-1", "LOAN-123", 10000, {
						min: 10000,
						max: 10000,
					}),
					makeBill("bill-2", "LOAN-456", 5000, {
						min: 5000,
						max: 5000,
					}),
				],
			},
			selectedBills: [],
			totalAmount: 0,
			isLoading: false,
			error: null,
			paymentStatus: null,
			useMockData: true,
			mockResponseType: "success",
		};

		const mockDispatch = jest.fn();

		render(
			<BbpsContext.Provider
				value={{
					state: mockState as BbpsState,
					dispatch: mockDispatch,
				}}
			>
				<Preview />
			</BbpsContext.Provider>
		);

		// Check if the title is correct
		expect(screen.getByText("All bills must be paid")).toBeInTheDocument();

		// Check if both bills are displayed
		expect(screen.getByText("Customer LOAN-123")).toBeInTheDocument();
		expect(screen.getByText("Customer LOAN-456")).toBeInTheDocument();

		// Check if checkboxes are present and disabled
		const checkboxes = screen.getAllByRole("checkbox");
		expect(checkboxes.length).toBe(2);
		checkboxes.forEach((checkbox) => {
			expect(checkbox).toBeDisabled();
		});
	});

	// Test for no bills found
	test("renders no bills found message when no bills are available", () => {
		// Mock state with no bills
		const mockState: Partial<BbpsState> = {
			currentStep: "preview",
			searchFormData: {},
			selectedProduct: null,
			billFetchResult: {
				selectionMode: "multiOptional",
				bills: [],
				arePartialPaymentsAllowed: false,
			},
			selectedBills: [],
			totalAmount: 0,
			isLoading: false,
			error: null,
			paymentStatus: null,
			useMockData: true,
			mockResponseType: "success",
		};

		const mockDispatch = jest.fn();

		render(
			<BbpsContext.Provider
				value={{
					state: mockState as BbpsState,
					dispatch: mockDispatch,
				}}
			>
				<Preview />
			</BbpsContext.Provider>
		);

		// Check if no bills message is displayed
		expect(screen.getByText("No bills found")).toBeInTheDocument();

		// Check if back button is present
		const backButton = screen.getByText("Back to Search");
		expect(backButton).toBeInTheDocument();

		// Click back button
		fireEvent.click(backButton);

		// Verify navigation was called
		expect(mockNavigation.goSearch).toHaveBeenCalled();
	});

	// Test for amount validation
	test("validates amount input correctly", () => {
		// Mock state with selected bill
		const mockState: Partial<BbpsState> = {
			currentStep: "preview",
			searchFormData: {},
			selectedProduct: {
				id: "electricity",
				label: "Electricity",
				desc: "Electricity Bill Payment",
				icon: "electricity",
				url: "/electricity",
				searchFields: [],
			},
			billFetchResult: {
				selectionMode: "single",
				arePartialPaymentsAllowed: true,
				bills: [
					makeBill("bill-1", "EB-123", 1500, {
						min: 500,
						max: 5000,
						multiple: 100,
					}),
				],
			},
			selectedBills: [
				makeBill("bill-1", "EB-123", 1500, {
					min: 500,
					max: 5000,
					multiple: 100,
				}),
			],
			totalAmount: 1500,
			isLoading: false,
			error: null,
			paymentStatus: null,
			useMockData: true,
			mockResponseType: "success",
		};

		const mockDispatch = jest.fn();

		render(
			<BbpsContext.Provider
				value={{
					state: mockState as BbpsState,
					dispatch: mockDispatch,
				}}
			>
				<Preview />
			</BbpsContext.Provider>
		);

		// Payment options are shown for the selected bill
		expect(screen.getByText("Payment Options")).toBeInTheDocument();

		// Amount rules appear only once a custom amount is chosen
		expect(screen.queryByText(/Amount range:/)).not.toBeInTheDocument();
		fireEvent.click(screen.getByText("Custom Amount"));

		expect(screen.getByText(/Amount range:/)).toBeInTheDocument();

		// The custom amount input is pre-filled with the bill amount
		const amountInput = screen.getByPlaceholderText("1500");
		expect(amountInput).toHaveValue("1500");

		// Pay button is enabled since the bill is selected with a valid amount
		const paymentButton = screen.getByRole("button", { name: /^Pay ₹/ });
		expect(paymentButton).not.toBeDisabled();

		// An amount that is not a multiple of 100 is rejected
		fireEvent.change(amountInput, { target: { value: "1550" } });
		expect(
			screen.getByText(/Amount must be a multiple of/)
		).toBeInTheDocument();
		expect(paymentButton).toBeDisabled();

		// A valid amount clears the error and updates the bill amount
		fireEvent.change(amountInput, { target: { value: "1600" } });
		expect(
			screen.queryByText(/Amount must be a multiple of/)
		).not.toBeInTheDocument();
		expect(mockDispatch).toHaveBeenCalledWith({
			type: "UPDATE_BILL_AMOUNT",
			billid: "bill-1",
			amount: 1600,
		});
		expect(paymentButton).not.toBeDisabled();
	});
});
