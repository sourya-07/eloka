import nextJest from "next/jest.js";

const createJestConfig = nextJest({
	// Provide the path to your Next.js app to load next.config.js and .env files in your test environment
	dir: "./",
});

// Add any custom config to be passed to Jest
/** @type {import('jest').Config} */
const customJestConfig = {
	// Add more setup options before each test is run
	// Same time zone everywhere (local machines and UTC CI runners)
	globalSetup: "<rootDir>/jest.global-setup.js",
	setupFilesAfterEnv: ["<rootDir>/jest.setup.js"],
	// if using TypeScript with a baseUrl set to the root directory then you need the below for alias' to work
	moduleDirectories: ["node_modules", "__tests__/test-utils"],
	moduleNameMapper: {
		// App code is imported as "@/…" (same alias as tsconfig.json)
		"^@/(.*)$": "<rootDir>/src/$1",
		// ESM heavy deps stubbed for tests not needing real implementations (rules: tests, isolation)
		"^jose$": "<rootDir>/__tests__/test-utils/esmStub.js",
		"^jose/(.*)$": "<rootDir>/__tests__/test-utils/esmStub.js",
		"^@copilotkit/(.*)$": "<rootDir>/__tests__/test-utils/esmStub.js",
		"^@segment/(.*)$": "<rootDir>/__tests__/test-utils/esmStub.js",
		// jsdom resolves the "browser" export of @preact/signals-core (used by @dnd-kit via
		// @measured/puck), which is ESM-only; point it to the package's own CommonJS build
		"^@preact/signals-core$":
			"<rootDir>/node_modules/@preact/signals-core/dist/signals-core.js",
	},
	testEnvironment: "jsdom",
	fakeTimers: { enableGlobally: true },
	// Allow transformation of selected ESM modules; others remain ignored for performance
	transform: {
		"^.+\\.(js|jsx|ts|tsx)$": ["babel-jest", { presets: ["next/babel"] }],
	},
	transformIgnorePatterns: [
		"node_modules/(?!(remark-gfm|react-markdown|vfile|unist|unified|bail|is-plain-obj|trough|remark|micromark|markdown-table|decode-named-character-reference|character-entities|property-information|hast|space-separated-tokens|comma-separated-tokens|mdast|mdast-util-to-hast|remark-rehype|trim-lines|rehype|html-void-elements|ccount|escape-string-regexp|strip-markdown|react-is|estree-util|format|is-|mdast-util-|unist-util-|zwitch|hastscript|web-namespaces|jose|@copilotkit|@segment)/)",
	],
	collectCoverage: true,
	coverageProvider: "v8",
	coverageThreshold: {
		global: {
			// branches: 80,
			// functions: 80,
			lines: 60,
			// statements: -10,
		},
	},
	coverageReporters: [
		"json",
		[
			"text",
			{
				skipFull: true, // Set to false to see full list of components being tested
			},
		],
		"text-summary",
	],
	collectCoverageFrom: [
		// "<rootDir>/src/pages/**/{!(_app),}.{js,ts,jsx,tsx}",
		"<rootDir>/src/components/ui/**/{!(index),}.{js,ts,jsx,tsx}",
		// "<rootDir>/src/screens/**/{!(index),}.{js,ts,jsx,tsx}",
		"<rootDir>/src/components/form-engine/**/{!(index),}.{js,ts,jsx,tsx}",
		// "<rootDir>/src/components/layout/**/{!(index),}.{js,ts,jsx,tsx}",
		"<rootDir>/src/contexts/**/{!(index),}.{js,ts,jsx,tsx}",
		"<rootDir>/src/hooks/**/{!(index),}.{js,ts,jsx,tsx}",
		"<rootDir>/src/utils/**/{!(index),}.{js,ts,jsx,tsx}",
		"<rootDir>/src/lib/**/{!(index),}.{js,ts,jsx,tsx}",
		// "<rootDir>/src/features/**/{!(index),}.{js,ts,jsx,tsx}",
		"!<rootDir>/**/*.stories.{js,ts,jsx,tsx,mdx}",
		"!<rootDir>/**/*.mocks.{js,ts}",
		"!<rootDir>/**/*.mock.{js,ts}",
	],
	coveragePathIgnorePatterns: [
		"<rootDir>/__tests__/test-utils/",
		".stories.{js,ts,jsx,tsx,mdx}",
		".mocks.{js,ts}",
		".mock.{js,ts}",
	],
	testPathIgnorePatterns: [
		"<rootDir>/tools/mock-eps/", // tested with node:test (npm run test:mock)
		"<rootDir>/__tests__/test-utils/",
		"<rootDir>/__tests__/fixtures/",
		".stories.{js,ts,jsx,tsx,mdx}",
		".mocks.{js,ts}",
		".mock.{js,ts}",
	],
	verbose: true,
};

// createJestConfig is exported as a default export
export default createJestConfig(customJestConfig);
