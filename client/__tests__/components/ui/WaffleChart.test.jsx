import { WaffleChart } from "@/components/ui/WaffleChart";
import { act, fireEvent, render, screen } from "test-utils";

/**
 * Collect the CSS rules (as text) that apply to an element's emotion class.
 * Needed for properties jsdom does not compute, such as `grid-gap`.
 * @param {Element} element - Element styled by Chakra/emotion
 * @returns {string[]} cssText of the matching rules
 */
const getCssRulesFor = (element) => {
	const className = element.className.split(" ").pop();
	return Array.from(document.styleSheets)
		.flatMap((sheet) => Array.from(sheet.cssRules))
		.map((rule) => rule.cssText)
		.filter((text) => text.startsWith(`.${className} {`));
};

describe("WaffleChart component", () => {
	const mockData = [
		{ value: 30, label: "Category A" },
		{ value: 20, label: "Category B" },
		{ value: 50, label: "Category C" },
	];

	it("renders successfully", () => {
		const { container } = render(<WaffleChart data={mockData} />);
		expect(container).not.toBeEmptyDOMElement();
	});

	it("renders correct number of squares", () => {
		const rows = 5;
		const cols = 4;
		const { container } = render(
			<WaffleChart data={mockData} rows={rows} cols={cols} />
		);

		// Should render rows * cols total squares (the children of the grid)
		const gridElement = container.firstChild;
		expect(gridElement.children).toHaveLength(rows * cols);
	});

	it("applies custom colors when provided", () => {
		const customColors = ["#FF0000", "#00FF00", "#0000FF"];
		render(<WaffleChart data={mockData} colors={customColors} />);

		// The component should use the provided colors
		// Note: This test would need more specific assertions based on how colors are applied
	});

	it("handles empty data gracefully", () => {
		// Nothing to chart: the component renders no grid
		const { container } = render(<WaffleChart data={[]} />);
		expect(container.querySelector("div")).toBeNull();
	});

	it("handles zero values in data", () => {
		const dataWithZero = [
			{ value: 0, label: "Zero Category" },
			{ value: 100, label: "Full Category" },
		];

		const { container } = render(<WaffleChart data={dataWithZero} />);
		expect(container).not.toBeEmptyDOMElement();
	});

	it("applies default props when not specified", () => {
		const { container } = render(<WaffleChart data={mockData} />);

		// Default should be 10x10 grid (the Tooltip renders the grid as its trigger)
		const gridElement = container.firstChild;
		expect(gridElement).toHaveStyle({
			gridTemplateRows: "repeat(10, 8px)",
			gridTemplateColumns: "repeat(10, 8px)",
		});
	});

	it("applies custom size and gap props", () => {
		const customSize = "12px";
		const customGap = "6px";
		const rows = 8;
		const cols = 8;

		const { container } = render(
			<WaffleChart
				data={mockData}
				size={customSize}
				gap={customGap}
				rows={rows}
				cols={cols}
			/>
		);

		const gridElement = container.firstChild; // The Tooltip renders the grid as its trigger
		expect(gridElement).toHaveStyle({
			gridTemplateRows: `repeat(${rows}, ${customSize})`,
			gridTemplateColumns: `repeat(${cols}, ${customSize})`,
		});
		// jsdom does not compute grid gaps, so check the generated CSS rule
		expect(getCssRulesFor(gridElement).join(" ")).toContain(
			`grid-gap: ${customGap};`
		);
	});

	it("displays tooltip with percentages", async () => {
		const { container } = render(<WaffleChart data={mockData} />);

		// No tooltip until the chart is hovered
		expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();

		// Hover the chart to open the tooltip
		act(() => {
			fireEvent.pointerEnter(container.firstChild);
			jest.runOnlyPendingTimers();
		});

		const tooltip = await screen.findByRole("tooltip");
		expect(tooltip).toHaveTextContent("Category A:");
		expect(tooltip).toHaveTextContent("30 (30%)");
		expect(tooltip).toHaveTextContent("20 (20%)");
		expect(tooltip).toHaveTextContent("50 (50%)");
	});
});
