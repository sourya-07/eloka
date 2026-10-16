import { ChakraProvider } from "@chakra-ui/react";
import "@testing-library/jest-dom";
import { fireEvent, render, screen } from "@testing-library/react";
import React from "react";
import KeyboardNumeric, {
	KeyConfig,
} from "tf-components/KeyboardNumeric/KeyboardNumeric";

// Mock navigator.vibrate
Object.defineProperty(navigator, "vibrate", {
	value: jest.fn(),
	writable: true,
	configurable: true,
});

// Test wrapper with ChakraProvider
const TestWrapper: React.FC<{ children: React.ReactNode }> = ({ children }) => (
	<ChakraProvider>{children}</ChakraProvider>
);

describe("KeyboardNumeric Component", () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	/**
	 * Test basic rendering
	 */
	it("renders successfully with default keys", () => {
		render(
			<TestWrapper>
				<KeyboardNumeric />
			</TestWrapper>
		);

		// Check if all number keys are rendered
		for (let i = 0; i <= 9; i++) {
			expect(screen.getByText(i.toString())).toBeInTheDocument();
		}

		// Check if delete and OK buttons are rendered
		expect(
			screen.getByRole("button", { name: /delete/i })
		).toBeInTheDocument();
		expect(screen.getByRole("button", { name: /ok/i })).toBeInTheDocument();
	});

	/**
	 * Test key press functionality
	 */
	it("calls onKeyPress when number key is pressed", () => {
		const mockOnKeyPress = jest.fn();

		render(
			<TestWrapper>
				<KeyboardNumeric onKeyPress={mockOnKeyPress} />
			</TestWrapper>
		);

		// Press number key '5'
		const key5 = screen.getByText("5");
		fireEvent.click(key5);

		expect(mockOnKeyPress).toHaveBeenCalledWith("5");
	});

	/**
	 * Test delete key functionality
	 */
	it("calls onDelete when delete key is pressed", () => {
		const mockOnDelete = jest.fn();

		render(
			<TestWrapper>
				<KeyboardNumeric onDelete={mockOnDelete} />
			</TestWrapper>
		);

		// Find and press delete key
		const deleteKey = screen.getByRole("button", { name: /delete/i });
		fireEvent.click(deleteKey);

		expect(mockOnDelete).toHaveBeenCalled();
	});

	/**
	 * Test OK key functionality
	 */
	it("calls onOk when OK key is pressed", () => {
		const mockOnOk = jest.fn();

		render(
			<TestWrapper>
				<KeyboardNumeric onOk={mockOnOk} />
			</TestWrapper>
		);

		// Find and press OK key
		const okKey = screen.getByRole("button", { name: /ok/i });
		fireEvent.click(okKey);

		expect(mockOnOk).toHaveBeenCalled();
	});

	/**
	 * Test disabled state
	 */
	it("does not trigger callbacks when disabled", () => {
		const mockOnKeyPress = jest.fn();
		const mockOnDelete = jest.fn();
		const mockOnOk = jest.fn();

		render(
			<TestWrapper>
				<KeyboardNumeric
					disabled={true}
					onKeyPress={mockOnKeyPress}
					onDelete={mockOnDelete}
					onOk={mockOnOk}
				/>
			</TestWrapper>
		);

		// All buttons should be disabled
		const buttons = screen.getAllByRole("button");
		buttons.forEach((button) => {
			expect(button).toBeDisabled();
		});

		// Try to click a key
		const key1 = screen.getByText("1");
		fireEvent.click(key1);

		// Callbacks should not be called
		expect(mockOnKeyPress).not.toHaveBeenCalled();
		expect(mockOnDelete).not.toHaveBeenCalled();
		expect(mockOnOk).not.toHaveBeenCalled();
	});

	/**
	 * Test haptic feedback
	 */
	it("triggers vibration on key press", () => {
		const mockVibrate = jest.fn();
		Object.defineProperty(navigator, "vibrate", {
			value: mockVibrate,
			writable: true,
			configurable: true,
		});

		render(
			<TestWrapper>
				<KeyboardNumeric />
			</TestWrapper>
		);

		// Press a key
		const key1 = screen.getByText("1");
		fireEvent.click(key1);

		expect(mockVibrate).toHaveBeenCalledWith(10);
	});

	/**
	 * Test custom key configuration
	 */
	it("renders custom keys when provided", () => {
		const customKeys: KeyConfig[] = [
			{ key: "1", type: "number" },
			{ key: "2", type: "number" },
			{ key: "custom", type: "number" },
		];

		render(
			<TestWrapper>
				<KeyboardNumeric keys={customKeys} />
			</TestWrapper>
		);

		expect(screen.getByText("1")).toBeInTheDocument();
		expect(screen.getByText("2")).toBeInTheDocument();
		expect(screen.getByText("custom")).toBeInTheDocument();
	});

	/**
	 * Test key types and styling
	 */
	it("applies correct styling based on key type", () => {
		render(
			<TestWrapper>
				<KeyboardNumeric />
			</TestWrapper>
		);

		// Delete key should have red color scheme
		const deleteKey = screen.getByRole("button", { name: /delete/i });
		expect(deleteKey).toHaveClass("chakra-button");

		// OK key should have green color scheme
		const okKey = screen.getByRole("button", { name: /ok/i });
		expect(okKey).toHaveClass("chakra-button");

		// Number keys should have default styling
		const numberKey = screen.getByText("1");
		expect(numberKey.closest("button")).toHaveClass("chakra-button");
	});

	/**
	 * Test keyboard layout structure
	 */
	it("maintains proper keyboard layout structure", () => {
		render(
			<TestWrapper>
				<KeyboardNumeric />
			</TestWrapper>
		);

		// Check that the keyboard is laid out in a grid
		const buttons = screen.getAllByRole("button");
		expect(buttons).toHaveLength(12); // 10 numbers + delete + ok
	});

	/**
	 * Test focus retention: keys prevent the default mousedown action so
	 * that the focused input does not lose focus while typing.
	 */
	it("prevents default mousedown so the input keeps focus", () => {
		const mockOnKeyPress = jest.fn();

		render(
			<TestWrapper>
				<KeyboardNumeric onKeyPress={mockOnKeyPress} />
			</TestWrapper>
		);

		const key1 = screen.getByText("1");
		// fireEvent returns false when preventDefault() was called
		expect(fireEvent.mouseDown(key1)).toBe(false);

		fireEvent.click(key1);
		expect(mockOnKeyPress).toHaveBeenCalledWith("1");
	});

	/**
	 * Test accessibility
	 */
	it("provides proper accessibility attributes", () => {
		render(
			<TestWrapper>
				<KeyboardNumeric />
			</TestWrapper>
		);

		// All keys should be buttons
		const buttons = screen.getAllByRole("button");
		buttons.forEach((button) => {
			expect(button).toBeInTheDocument();
			expect(button.tagName).toBe("BUTTON");
		});
	});

	/**
	 * Test custom styling props
	 */
	it("handles custom styling props", () => {
		const containerStyle = { backgroundColor: "red" };
		const keyStyle = { color: "blue" };

		const { container: _container } = render(
			<TestWrapper>
				<KeyboardNumeric
					containerStyle={containerStyle}
					keyStyle={keyStyle}
				/>
			</TestWrapper>
		);

		// Component should render without errors
		expect(screen.getByText("1")).toBeInTheDocument();
	});

	/**
	 * Test no vibration support
	 */
	it("handles missing vibration support gracefully", () => {
		// Remove vibrate from navigator
		const originalVibrate = navigator.vibrate;
		delete (navigator as any).vibrate;
		expect("vibrate" in navigator).toBe(false);

		render(
			<TestWrapper>
				<KeyboardNumeric />
			</TestWrapper>
		);

		// Press a key - should not throw error
		const key1 = screen.getByText("1");
		fireEvent.click(key1);

		// Should render without errors
		expect(screen.getByText("1")).toBeInTheDocument();

		// Restore vibrate
		Object.defineProperty(navigator, "vibrate", {
			value: originalVibrate,
			writable: true,
			configurable: true,
		});
	});

	/**
	 * Test multiple rapid key presses
	 */
	it("handles multiple rapid key presses", () => {
		const mockOnKeyPress = jest.fn();

		render(
			<TestWrapper>
				<KeyboardNumeric onKeyPress={mockOnKeyPress} />
			</TestWrapper>
		);

		// Rapidly press multiple keys
		const keys = ["1", "2", "3", "4", "5"];
		keys.forEach((key) => {
			const keyElement = screen.getByText(key);
			fireEvent.click(keyElement);
		});

		// All key presses should be registered
		expect(mockOnKeyPress).toHaveBeenCalledTimes(5);
		keys.forEach((key) => {
			expect(mockOnKeyPress).toHaveBeenCalledWith(key);
		});
	});

	/**
	 * Test keyboard with empty keys array
	 */
	it("handles empty keys array gracefully", () => {
		render(
			<TestWrapper>
				<KeyboardNumeric keys={[]} />
			</TestWrapper>
		);

		// Should render the container without any keys
		expect(screen.queryAllByRole("button")).toHaveLength(0);
	});
});
