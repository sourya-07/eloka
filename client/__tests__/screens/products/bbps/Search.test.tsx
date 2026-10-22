import "@testing-library/jest-dom";
import { fireEvent, waitFor } from "@testing-library/react";
import { ParamType } from "@/constants/trxnFramework";
import { Search } from "@/screens/products/bbps/Search";
import { BbpsContext } from "@/screens/products/bbps/context/BbpsContext";
import { initialState } from "@/screens/products/bbps/context/types";
import { useBbpsApi } from "@/screens/products/bbps/hooks/useBbpsApi";
import {
	mockBillFetchResponse,
	transformBillData,
} from "@/screens/products/bbps/utils";
import { pageRender } from "test-utils";

// Mock the hooks
jest.mock("@/screens/products/bbps/hooks/useBbpsNavigation", () => ({
	useBbpsNavigation: () => ({
		goPreview: jest.fn(),
		goPayment: jest.fn(),
		goSearch: jest.fn(),
		goStatus: jest.fn(),
		backToGrid: jest.fn(),
		goPreviousStep: jest.fn(),
	}),
}));

// Mock useBbpsApi
jest.mock("@/screens/products/bbps/hooks/useBbpsApi", () => ({
	useBbpsApi: jest.fn(),
}));

// Mock toast
const mockToast = jest.fn();
jest.mock("@chakra-ui/react", () => ({
	...jest.requireActual("@chakra-ui/react"),
	useToast: () => mockToast,
}));

// Define a default mock for useBbpsApi
const defaultMockApi = {
	fetchBills: jest.fn().mockResolvedValue({
		data: mockBillFetchResponse,
		error: null,
	}),
	processBillFetchResponse: transformBillData, // same transform the real hook applies
	fetchOperators: jest.fn().mockResolvedValue({ data: [], error: null }),
	fetchDynamicFields: jest.fn().mockResolvedValue({ data: [], error: null }),
	isLoadingBills: false,
};

describe("BBPS Search Component", () => {
	beforeEach(() => {
		jest.clearAllMocks();
		(useBbpsApi as jest.Mock).mockReturnValue(defaultMockApi);
	});

	const mockProduct = {
		id: "electricity",
		label: "Electricity Bill",
		desc: "Pay your electricity bills",
		icon: "electricity",
		url: "/products/bbps/electricity",
		useMockData: true,
		searchFields: [
			{
				name: "customer_id",
				label: "Customer ID",
				parameter_type_id: ParamType.TEXT,
				required: true,
			},
		],
		defaultSearchValues: {
			customer_id: "LOAN041101",
		},
	};

	const mockDispatch = jest.fn();

	const renderComponent = (
		stateOverrides = {},
		product: typeof mockProduct = mockProduct
	) => {
		return pageRender(
			<BbpsContext.Provider
				value={{
					state: {
						...initialState,
						currentStep: "search",
						...stateOverrides,
					},
					dispatch: mockDispatch,
				}}
			>
				<Search product={product} />
			</BbpsContext.Provider>
		);
	};

	/**
	 * The Proceed button stays disabled until react-hook-form has validated
	 * the default values, so wait for it before clicking.
	 * @param {Function} getByText - Query from the render result
	 * @returns {Promise<HTMLElement>} The enabled Proceed button
	 */
	const getEnabledProceedButton = async (getByText) => {
		const button = getByText("Proceed").closest("button");
		await waitFor(() => expect(button).toBeEnabled());
		return button;
	};

	describe("Basic Rendering", () => {
		it("renders the search form with default values", () => {
			const { getByLabelText } = renderComponent();
			const customerIdInput = getByLabelText(/Customer ID/i);
			expect(customerIdInput).toHaveValue("LOAN041101");
		});

		it("renders page title", () => {
			const { getByText } = renderComponent();
			expect(getByText("Fetch Bills")).toBeInTheDocument();
		});

		it("renders proceed button", () => {
			const { getByText } = renderComponent();
			expect(getByText("Proceed")).toBeInTheDocument();
		});
	});

	describe("Mock Data Flag", () => {
		it("sets mock data flag based on product configuration", () => {
			renderComponent();
			expect(mockDispatch).toHaveBeenCalledWith({
				type: "SET_MOCK_DATA_FLAG",
				useMockData: true,
			});
		});

		it("does not dispatch if mock data flag is already set correctly", () => {
			renderComponent({ useMockData: true });
			// Should not dispatch SET_MOCK_DATA_FLAG again
			expect(mockDispatch).not.toHaveBeenCalledWith({
				type: "SET_MOCK_DATA_FLAG",
				useMockData: true,
			});
		});
	});

	describe("Form Submission", () => {
		it("submits the form and processes the mock data", async () => {
			const { getByText } = renderComponent();
			const submitButton = await getEnabledProceedButton(getByText);

			// Submit the form
			fireEvent.click(submitButton);

			// Wait for the mock API call to resolve
			await waitFor(() => {
				// Verify dispatch was called with the correct actions
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_SEARCH_PAYLOAD",
					payload: { customer_id: "LOAN041101" },
				});

				// Verify loading state was set
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_LOADING",
					value: true,
				});

				// Verify validation response was set with transformed data
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_VALIDATION_RESPONSE",
					payload: expect.objectContaining({
						selectionMode: "multiOptional",
						bills: expect.arrayContaining([
							expect.objectContaining({
								billid: expect.any(String),
								amount: expect.any(Number),
							}),
						]),
					}),
				});

				// Verify loading state was unset
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_LOADING",
					value: false,
				});
			});
		});

		it("handles form submission with custom input values", async () => {
			const { getByLabelText, getByText } = renderComponent();
			const customerIdInput = getByLabelText(/Customer ID/i);

			// Change input value
			fireEvent.change(customerIdInput, {
				target: { value: "CUSTOM123" },
			});

			const submitButton = await getEnabledProceedButton(getByText);

			// Submit the form
			fireEvent.click(submitButton);

			await waitFor(() => {
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_SEARCH_PAYLOAD",
					payload: { customer_id: "CUSTOM123" },
				});
			});
		});

		it("handles API errors gracefully", async () => {
			const errorMessage = "API Error occurred";
			(useBbpsApi as jest.Mock).mockReturnValue({
				...defaultMockApi,
				fetchBills: jest.fn().mockResolvedValue({
					data: null,
					error: errorMessage,
				}),
			});

			const { getByText } = renderComponent();
			const submitButton = await getEnabledProceedButton(getByText);

			fireEvent.click(submitButton);

			await waitFor(() => {
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_ERROR",
					message: errorMessage,
				});
			});
			expect(mockToast).toHaveBeenCalledWith(
				expect.objectContaining({
					status: "error",
					description: errorMessage,
				})
			);
		});
	});

	describe("Dynamic Form Reset", () => {
		it("resets dynamic form state when product changes", () => {
			renderComponent();

			expect(mockDispatch).toHaveBeenCalledWith({
				type: "RESET_DYNAMIC_FORM",
			});
			expect(mockDispatch).toHaveBeenCalledWith({
				type: "SET_OPERATORS",
				payload: [],
			});
			expect(mockDispatch).toHaveBeenCalledWith({
				type: "SET_SELECTED_OPERATOR",
				payload: null,
			});
			expect(mockDispatch).toHaveBeenCalledWith({
				type: "SET_DYNAMIC_FIELDS",
				payload: [],
			});
			expect(mockDispatch).toHaveBeenCalledWith({
				type: "SET_LOADING_DYNAMIC_DATA",
				value: false,
			});
		});
	});

	describe("Operator Selection", () => {
		const productWithCategory = {
			...mockProduct,
			categoryId: "testCategory",
			useMockData: false,
			searchFields: [], // Ensure no static fields interfere
		};

		const mockOperators = [
			{ operator_id: 1, name: "Operator 1" },
			{ operator_id: 2, name: "Operator 2" },
		];

		it("fetches and renders operators when product has categoryId", async () => {
			const fetchOperators = jest
				.fn()
				.mockResolvedValue({ data: mockOperators, error: null });
			(useBbpsApi as jest.Mock).mockReturnValue({
				...defaultMockApi,
				fetchOperators,
			});

			const { findByText } = renderComponent({}, productWithCategory);

			expect(fetchOperators).toHaveBeenCalledWith("testCategory");

			expect(await findByText(/Select Operator/i)).toBeInTheDocument();

			await waitFor(() => {
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_OPERATORS",
					payload: mockOperators,
				});
			});
		});

		it("handles operator selection change", async () => {
			const { container, findByText } = renderComponent(
				{ operators: mockOperators },
				productWithCategory
			);

			// Open the operator dropdown (react-select) and pick an operator
			const selectInput = container.querySelector(
				"#phone_operator_code input"
			);
			fireEvent.keyDown(selectInput, { key: "ArrowDown" });
			fireEvent.click(await findByText("Operator 1"));

			await waitFor(() => {
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "RESET_DYNAMIC_FORM",
				});
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_SELECTED_OPERATOR",
					payload: mockOperators[0],
				});
			});
		});

		it("fetches dynamic fields when operator is selected", async () => {
			const mockDynamicFields = [
				{
					param_name: "account_number",
					param_label: "Account Number",
					param_id: "1",
					param_type: "Numeric",
				},
			];

			const fetchDynamicFields = jest.fn().mockResolvedValue({
				data: mockDynamicFields,
				error: null,
			});
			(useBbpsApi as jest.Mock).mockReturnValue({
				...defaultMockApi,
				fetchDynamicFields,
			});

			const selectedOperator = { operator_id: 1, name: "Test Operator" };

			renderComponent({
				selectedOperator,
			});

			await waitFor(() => {
				expect(fetchDynamicFields).toHaveBeenCalledWith(
					selectedOperator.operator_id
				);
				expect(mockDispatch).toHaveBeenCalledWith({
					type: "SET_DYNAMIC_FIELDS",
					payload: mockDynamicFields,
				});
			});
		});

		it("handles operator fetch errors", async () => {
			(useBbpsApi as jest.Mock).mockReturnValue({
				...defaultMockApi,
				fetchOperators: jest.fn().mockResolvedValue({
					data: null,
					error: "Failed to fetch operators",
				}),
			});

			renderComponent({}, productWithCategory);

			await waitFor(() => {
				expect(mockToast).toHaveBeenCalledWith({
					title: "Error",
					description: "Failed to load operators. Please try again.",
					status: "error",
					duration: 5000,
					isClosable: true,
				});
			});
		});
	});

	describe("Loading States", () => {
		it("shows loading state when fetching bills", () => {
			(useBbpsApi as jest.Mock).mockReturnValue({
				...defaultMockApi,
				isLoadingBills: true,
			});

			const { getByText } = renderComponent();
			const submitButton = getByText("Proceed").closest("button");

			expect(submitButton).toBeDisabled();
			expect(submitButton).toHaveAttribute("data-loading");
		});

		it("shows loading state when fetching dynamic data", () => {
			const { getByText } = renderComponent({
				isLoadingDynamicData: true,
			});

			// The Proceed button should show its loading indicator
			const submitButton = getByText("Proceed").closest("button");
			expect(submitButton).toBeDisabled();
			expect(submitButton).toHaveAttribute("data-loading");
		});
	});

	describe("Form Validation", () => {
		it("validates required fields", async () => {
			const { getByLabelText, getByText } = renderComponent();
			const customerIdInput = getByLabelText(/Customer ID/i);
			const submitButton = getByText("Proceed").closest("button");

			// Clear required field
			fireEvent.change(customerIdInput, { target: { value: "" } });

			// An invalid form keeps the Proceed button disabled
			await waitFor(() => expect(submitButton).toBeDisabled());
			fireEvent.click(submitButton);

			// Should show validation error
			await waitFor(() => {
				expect(mockDispatch).not.toHaveBeenCalledWith({
					type: "SET_SEARCH_PAYLOAD",
					payload: expect.anything(),
				});
			});
		});
	});

	describe("Error Handling", () => {
		it("handles dynamic fields fetch errors", async () => {
			(useBbpsApi as jest.Mock).mockReturnValue({
				...defaultMockApi,
				fetchDynamicFields: jest.fn().mockResolvedValue({
					data: null,
					error: "Failed to fetch dynamic fields",
				}),
			});

			const selectedOperator = { operator_id: 1, name: "Test Operator" };

			renderComponent({
				selectedOperator,
			});

			await waitFor(() => {
				expect(mockToast).toHaveBeenCalledWith({
					title: "Error",
					description:
						"Failed to load dynamic fields. Please try again.",
					status: "error",
					duration: 5000,
					isClosable: true,
				});
			});
		});

		it("handles missing product gracefully", () => {
			const { container } = pageRender(
				<BbpsContext.Provider
					value={{
						state: { ...initialState, currentStep: "search" },
						dispatch: mockDispatch,
					}}
				>
					<Search product={null as any} />
				</BbpsContext.Provider>
			);

			expect(container).not.toBeEmptyDOMElement();
		});
	});
});
