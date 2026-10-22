import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { fireEvent, render, screen } from "test-utils";

const segments = [
	{ value: "day", label: "Day" },
	{ value: "week", label: "Week" },
	{ value: "month", label: "Month" },
];

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

describe("SegmentedControl", () => {
	it("renders without error with no attributes", () => {
		const { container } = render(<SegmentedControl />);
		expect(container).not.toBeEmptyDOMElement();
	});

	it("selects segments in uncontrolled mode", () => {
		const onChange = jest.fn();
		render(
			<SegmentedControl
				name="Period"
				segments={segments}
				defaultIndex={1}
				onChange={onChange}
			/>
		);

		expect(screen.getByRole("radio", { name: "Week" })).toHaveAttribute(
			"aria-checked",
			"true"
		);

		fireEvent.click(screen.getByRole("radio", { name: "Month" }));

		expect(onChange).toHaveBeenCalledWith("month", 2);
		expect(screen.getByRole("radio", { name: "Month" })).toHaveAttribute(
			"aria-checked",
			"true"
		);
		expect(screen.getByRole("radio", { name: "Week" })).toHaveAttribute(
			"aria-checked",
			"false"
		);
	});

	it("follows the value prop in controlled mode", () => {
		const { rerender } = render(
			<SegmentedControl name="Period" segments={segments} value="day" />
		);
		expect(screen.getByRole("radio", { name: "Day" })).toHaveAttribute(
			"aria-checked",
			"true"
		);

		rerender(
			<SegmentedControl name="Period" segments={segments} value="month" />
		);
		expect(screen.getByRole("radio", { name: "Month" })).toHaveAttribute(
			"aria-checked",
			"true"
		);

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
		"TODO: add proper test cases for SegmentedControl in __tests__/components/SegmentedControl/SegmentedControl.test.jsx"
	);
});
