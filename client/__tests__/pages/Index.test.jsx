import Index from "@/pages";
import { loggedOutPageRender, screen } from "test-utils";

describe("Login (index) page", () => {
	it("should render properly with login form", () => {
		loggedOutPageRender(<Index />);

		// Check for the login prompt
		const loginLabel = screen.getByText(/login to continue/i);
		expect(loginLabel).toBeInTheDocument();

		// Verify that the app name is displayed
		const appName = screen.getByText("AbhiPay");
		expect(appName).toBeInTheDocument();

		// Verify the mobile number input and the continue button exist
		expect(
			screen.getByPlaceholderText("Mobile number")
		).toBeInTheDocument();
		const continueButton = screen.getByRole("button", {
			name: /continue/i,
		});
		expect(continueButton).toBeInTheDocument();
	});

	test.todo(
		"TODO: add test cases for the index (Login) page: render custom logo, mobile number input, conditional Google login button"
	);
});
