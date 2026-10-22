import { UserType } from "@/constants";
import { OnboardViaFile } from "@/screens/Admin/OnboardAgents/OnboardViaFile";
import { adminRender, screen } from "test-utils";

/*
	* React Testing Library:
		- Cheatsheet: https://testing-library.com/docs/react-testing-library/cheatsheet
		- How to query: https://testing-library.com/docs/queries/about/
		- Testing user events: https://testing-library.com/docs/user-event/intro
		- Migrate from Enzyme (examples): https://testing-library.com/docs/react-testing-library/migrate-from-enzyme/
		- Testing onChange event handlers: https://testing-library.com/docs/react-testing-library/faq
		- All APIs: https://testing-library.com/docs/react-testing-library/api
		- Debug: https://testing-library.com/docs/queries/about/#screendebug, https://testing-library.com/docs/dom-testing-library/api-debugging/#prettydom
	* Jest:
		- Docs: https://jestjs.io/docs/getting-started
		- Jest-dom (matchers): https://github.com/testing-library/jest-dom
*/

// Props as passed by the parent OnboardAgents page
const defaultProps = {
	permissions: {
		allowedAgentTypes: [UserType.MERCHANT],
		autoMapDistributor: false,
	},
	agentTypeList: [{ label: "Retailer", value: `${UserType.MERCHANT}` }],
	agentTypeValueToApi: { 1: "2", 2: "0" },
};

describe("OnboardViaFile", () => {
	it("renders without error with the required props", () => {
		const { container } = adminRender(<OnboardViaFile {...defaultProps} />);
		expect(container).not.toBeEmptyDOMElement();

		// Sample file link for the only allowed agent type
		expect(
			screen.getByText(/Download Sample File \(for Onboarding Retailer\)/)
		).toBeInTheDocument();
		expect(screen.getByRole("link", { name: /download/i })).toHaveAttribute(
			"href",
			"https://files.eko.co.in/docs/onboarding/sample_files/Bulk_Agent_Onboarding.xlsx"
		);

		// Agent type selector is hidden when only one type is allowed
		expect(screen.queryByText("Select Agent Type")).not.toBeInTheDocument();

		// Upload stays disabled until a file is chosen
		expect(screen.getByRole("button", { name: "Upload" })).toBeDisabled();

		// expect(container).toHaveTextContent("Any text");

		// const inp = screen.getByLabelText("Input Label");
		// expect(inp).toBeInTheDocument();

		// const btn = utils.getByRole("button", { name: "Submit" });

		// CUSTOM MATCHERS (jest-dom)
		// See all matchers here: https://github.com/testing-library/jest-dom#table-of-contents
		// expect(btn).toBeDisabled();
		// expect(btn).toBeEnabled();
		// expect(inp).toBeInvalid();
		// expect(inp).toBeRequired();
		// expect(btn).toBeVisible();
		// expect(btn).toContainElement(elm);
		// expect(btn).toContainHTML(htmlText: string);
		// expect(btn).toHaveFocus();

		// Check style
		// expect(getByTestId('background')).toHaveStyle(`background-image: url(${props.image})`);

		// Enable snapshot testing:
		// expect(container).toMatchSnapshot();
	});

	// TODO: Write other tests here..
	// Start by writting all possible test cases here using test.todo()
	test.todo(
		"TODO: add proper test cases for OnboardViaFile in __tests__/components/OnboardViaFile/OnboardViaFile.test.jsx"
	);
});
