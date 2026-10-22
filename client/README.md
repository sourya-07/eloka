# Eloka SaaS WebApp

> This is the `client/` (frontend) part of the ELOKA repo. Start with the [root README](../README.md).
Webapp for the Eloka white-labelled SaaS platform to run your business like agent-banking, micro-finance, payments, etc.

![Uptime](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2Fekoindia%2Fuptime%2Fmaster%2Fapi%2Feloka-webapp%2Fuptime.json)
<a href="https://developers.eko.in" target="_blank">![Eko.in](https://img.shields.io/badge/Develop%20with-Eko.in-brightgreen)</a>
<a href="https://twitter.com/intent/tweet?text=Wow:&url=https%3A%2F%2Fgithub.com%2Fekoindia%2Fwlc-webapp" target="_blank"><img alt="Twitter" src="https://img.shields.io/twitter/url?style=social&url=https%3A%2F%2Fgithub.com%2Fekoindia%2Fwlc-webapp"></a>
<a href="https://twitter.com/intent/follow?screen_name=ekospeaks" target="_blank">![Twitter Follow](https://img.shields.io/twitter/follow/ekospeaks?label=Follow&style=social)</a>
<a href="http://dsc.gg/ekodevs" target="_blank">![Discord Chat](https://img.shields.io/discord/1038006952573292574)</a>


## 🧑‍💻 Setup Development Environment:

- One-time Setup:
  - Run `npm i`
  - Run `npm run prepare`
  - Make sure the _.husky/pre-commit_ file is executable by running: `chmod +x pre-commit`
- Run project on your local device:
  - With dummy data (no backend needed): `npm run dev:mock`, then log in with `9000000001` and OTP `123456`. All dummy logins: [docs/LOCAL_DEVELOPMENT.md](../docs/LOCAL_DEVELOPMENT.md)
  - Against real EPS: `npm run dev` (personal settings in `.env.local`)
  - Open webpage: [http://localhost:3002](http://localhost:3002)


## 📜 Available Scripts

### Development
- `npm run dev` - Run development server on port 3002
- `npm run dev:mock` - Run the mock EPS server (:4010) and the dev server together, with dummy logins
- `npm run mock` - Run only the mock EPS server
- `npm run test:mock` - Contract tests for the mock EPS server
- `npm run dev.https` - Run development server with HTTPS on port 3004
- `npm run scan` - Run development server on port 3006 with react-scan to detects rerenders and other performance issues
- `npm run inspect` - Run development server with Node inspector
- `npm start` - Start production server
- `npm run build` - Build production bundle
- `npm run analyze` - Build with bundle-analyzer for size analysis

### Code Quality
- `npm run lint` - Run Next.js linting
- `npm run lint:error` - Run linting with fewer warnings
- `npm run lint:fix` - Fix linting issues automatically
- `npm run format` - Format code with Prettier
- `npm run format:check` - Check code formatting without changes

### Testing & Code Coverage
- `npm test` - Run all tests
- `npm run test:quick` - Run tests quickly without coverage for a single or all files (skips if no tests found)
- `npm run test:file` - Run tests for a single file in verbose mode (usage: `npm run test:file path/to/file`)
- `npm run test:file:pattern` - Run tests matching a pattern (usage: `npm run test:file:pattern pattern`)
- `npm run test:failed` - Run only the tests that failed in the last run
- `npm run test:watch` - Run tests in watch mode
- `npm run test:coverage` - Run tests with coverage report
- `npm run test:update:snapshots` - Update test snapshots to match current output

### Development Tools
- `npm run g` or `npm run generate` - Run plop generator for new components
- `npm run sb` or `npm run storybook` - Run Storybook on port 6006
- `npm run build:storybook` - Build static Storybook
- `npm run check-updates` - Check for outdated dependencies
- `npm run check-deps` - Run custom dependency checks

### Docker Commands
- `npm run docker:dev` - Run development container
- `npm run docker:prod` - Run production container
- `npm run docker:dev:build` - Build and run development container
- `npm run docker:prod:build` - Build and run production container
- `npm run docker:stop` - Stop all containers
- `npm run docker:clean` - Remove containers, images, and volumes

## 📦 Project Structure
All app code lives in `src/` and is imported with the `@/` alias (for example `import { Button } from "@/components/ui"`).

- **`src/pages/`**: Next.js page routes. They stay thin and render a screen.
- **`src/screens/`**: One folder per route screen (`Admin/*`, `LoginPanel`, `Home`, `History`, …).
- **`src/features/`**: Self-contained features with their own components, context, hooks and screens (onboarding, KYC, DigiKhata, …).
- **`src/components/ui/`**: Reusable UI building blocks used across the app.
- **`src/components/layout/`**: The app shell (layouts, popup loader).
- **`src/components/form-engine/`**: Low-code, config-driven form components of the transaction framework.
- **`src/contexts/`**: Context providers for global state and data.
- **`src/hooks/`**: Custom React hooks for reusable logic.
- **`src/constants/`**: Constant values, configurations and settings.
- **`src/lib/`**: The API client and proxy helpers, org loading, and wrappers around third-party libraries (CMS, inventory, maps, face detection, Copilot).
- **`src/utils/`**: Small, pure utility functions.
- **`src/styles/`**: Global styles and theme configuration.
- **`public/`**: Static assets such as images and icons.
- **`tools/`**: Dev-only tooling: `mock-eps/` (dummy backend), `plop-templates/` (code generator templates), `scripts/`.
- **`docs/`**: Client documentation, including `docs/memory-bank/` (project notes for AI agents).
- **`__tests__/`**: Contains unit and integration tests for the application. The tests are organized in a similar structure to the main codebase for easy navigation.


## 📒 Extended Documentation ([docs/](docs))

- **[`commit-message-instructions.md`](docs/commit-message-instructions.md)**: Guidelines for writing good commit messages.
- **[`docker-usage.md`](docs/docker-usage.md)**: Instructions for using Docker with the project.]
- **[`src/features/`](docs/features/)**: Contains detailed documentation for various features of the application.
  - **[`android.md`](docs/features/android.md)**: How does Eloka communicate with the Android wrapper app to provide a native experience?
  - **[`dashboard.md`](docs/features/dashboard.md)**: Admin dashboard feature documentation.
  - **[`dynamic-popup-module.md`](docs/features/dynamic-popup-module.md)**: How to use the dynamic popup module to show custom popups throughout the app?
  - **[`feature-flags.md`](docs/features/feature-flags.md)**: How to use feature flags to conditionally enable/disable features, or for a beta rollout?
  - **[`inventory-management.md`](docs/features/inventory-management.md)**: Inventory management feature documentation.
  - **[`pub-sub.md`](docs/features/pub-sub.md)**: How to enable cross-component communication using the Pub/Sub pattern?
  - **[`transaction-history.md`](docs/features/transaction-history.md)**: Transaction history feature documentation.


## 🎨 UI Features:

- **Global Styles**:
  - Global CSS styles are defined in [src/styles/globals.ts](src/styles/globals.ts).
- **Theme**:
  - Default color theme configuration (CHakraUI): [src/styles/themes.tsx](src/styles/themes.tsx)
  - A list of predefined color-themes for the organizations to choose from: [src/constants/colorThemes.js](src/constants/colorThemes.js)
  - Component to show theme colors to users to choose from: [src/components/ui/ColorPair/ColorPair.jsx](src/components/ui/ColorPair/ColorPair.jsx)
  -Organization's custom theme is loaded in [src/pages/_app.tsx](src/pages/_app.tsx).
- **Icon Library**:
  - To view available icons, open the webpage: http://localhost:3002/icons_demo
  - To add new icons, see the file: [src/constants/IconLibrary.ts](src/constants/IconLibrary.ts)
  - There are duplicate icon-names as well, same icons with different names (for backward compatibility). Such icons are shown in red background with the pointer to the actual icon name.
  - For optimizing individual SVG icons, use: [SVGOMG - SVGO's Missing GUI](https://jakearchibald.github.io/svgomg/)
- **Top Navbar** - [src/components/ui/NavBar/NavBar.jsx](src/components/ui/NavBar/NavBar.jsx)
  - Profile-menu (drop-down menu in top-right corner):
    - Rendered in the `MyAccountCard` component in NavBar.jsx.
    - Items configuration (for internal links, such as, Settings page): [src/constants/profileCardMenus.js](src/constants/profileCardMenus.js)
- **Left Sidebar** - [src/components/ui/SideBar/SideBar.jsx](src/components/ui/SideBar/SideBar.jsx)
  - Menu items configuration: [src/constants/SidebarMenu.ts](src/constants/SidebarMenu.ts)
  - Menu Context (data fetch after login, and filtered into transaction & other lists for the sidebar & bottom app bar): [src/contexts/MenuContext.tsx](src/contexts/MenuContext.tsx)
  - Menu data filtering & transformation using: [src/lib/processTransactionData.js](src/lib/processTransactionData.js)
- **Bottom App Bar** - The `BottomAppBar` is a flexible component that serves as the primary navigation bar at the bottom of the small screen. It adapts to various use cases, allowing for the inclusion of navigation links, actions, and custom components. [src/components/ui/BottomAppBar/BottomAppBar.tsx](src/components/ui/BottomAppBar/BottomAppBar.tsx)
  - _Bottom App Bar Items_ - The items in the Bottom App Bar are configured using the `useBottomAppBarItems` hook. This hook returns an array of objects, each representing an item in the bottom app bar. [src/components/ui/BottomAppBar/useBottomAppBar.ts](src/components/ui/BottomAppBar/useBottomAppBar.ts)
    - Each item can include the following properties:
        - `name`: A unique identifier for the item.
        - `label`: The text label displayed beneath the icon.
        - `icon`: The name of the icon displayed in the item.
        - `path`: The navigation path for the item. For admin users, paths can be prefixed with `/admin`.
        - `action`: A function that is executed when the item is clicked, such as toggling a search bar.
        - `component`: A custom JSX component that can be rendered within the item.
        - `visible`: A boolean indicating whether the item should be visible in the Bottom App Bar.


## 💼 Business Features:

### 📈 Business/Pricing Configuration for Admins:
To add pricing configuration page for a new product in the Admin portal under "Pricing & Commission" left-menu section, use the following steps:

#### For Pricing Configuration using File Upload:
1. Create a configuration for the product in [`src/constants/ProductBusinessConfigurations.ts`](src/constants/ProductBusinessConfigurations.ts) file under the `business_config_slug_map` object.
   1. Set a unique slug (for the page URL) and the name of the page-component to display.
   2. Set template="fileupload"
   3. Add set URL & parameters for file-upload and sample-file-download in the `meta` object.
   4. Add the slug to `product_pricing_categories` object in the order that you want to display on the product listing page. If not added here, the product will not be displayed on the product listing page.

#### For creating a custom Pricing (or, Business) Configuration page:
1. Create a page-component for the pricing configuration running the command `npm run g`
   1. Select "Component"
   2. Enter the name of the page-component (eg: `TrainTravel`)
   3. Select Path: `src/screens/Admin`
   4. Enter sub-folder: `PricingCommission`
2. Copy sub-components from other existing pricing page-component such as: `TrainTravelDistributor` and `TrainTravelRetailer`
3. Create a configuration for the product in [`src/constants/ProductBusinessConfigurations.ts`](src/constants/ProductBusinessConfigurations.ts) file under the `business_config_slug_map` object.
   1. Set a unique slug (for the page URL) and the name of the page-component to display (created in step #1).
   2. Add the slug to `product_pricing_categories` object in the order that you want to display on the product listing page. If not added here, the product will not be displayed on the product listing page.
4. Create a configuration for the product in [`src/constants/ProductDetails.js`](src/constants/ProductDetails.js) page. Set slabs, validations, etc for the pricing configuration.


## ✨ Other Features:

### 🚦 Routing (Public vs Private Pages):
- To add a public page/route (that can be accesseed by anyone, without logging in), add the route in [src/constants/validRoutes.js](src/constants/validRoutes.js) within the array: `publicLinks`.


## 🛠️ Common Utilities

### 🪝 Custom [**src/hooks/**](src/hooks/):
- `useAppLink`: For handling app links (deep-links) and redirecting to the appropriate page. Supports old `connect.eko.in` links as well as the `ekoconnect://` links.
- `useClipboard`: Copy text to clipboard.
- `useDailyCacheState`: Cache the state in the browser's localStorage. The cache state has a `valid` flag which is set to `false` when the cache is stale (older than 1 day).
- `useDebouncedState`: Debounce the state update to avoid unnecessary re-renders.
- `useDelayToggle`: A simple hook to toggle a boolean value after a delay. For example, it can be used to load/show a component after an initial delay.
- `useExternalResource`: Load an external resource (JS/CSS) dynamically. It returns a state variable (idle, loading, ready, error) and a reload function. The resource is loaded only once.
- `useFeatureFlag`: Check if a feature is enabled or not. Set the feature-flags with conditions in the [src/constants/featureFlags.ts](src/constants/featureFlags.ts) file.
- `useHotkey`: Register a hotkey (keyboard shortcut) and execute a callback function when the hotkey is pressed. Uses the tinykeys library.
- `useHslColor`: Get a dynamic color in HSL format based on the input value. Useful for creating a color scale or colored tags.
- `useLocalStorage`: Read/Write data to the browser's localStorage.
- `useSessionStorage`: Read/Write data to the browser's sessionStorage.
- `usePubSub`: Publish/Subscribe to events. Add new topics in the [src/contexts/PubSubContext.js](src/contexts/PubSubContext.js) file.

### 📦 Custom [**lib**](src/lib/):
These are wrappers around 3rd-party libraries or associated utility functions.
- [GoogleMap](src/lib/GoogleMap.jsx) - Google Maps API wrapper
- [Map View](src/lib/MapView.js) - View static maps using iFrame
- [dateFormat](src/lib/dateFormat.js) - Date formatting utility (wrapper around `date-fns`)
- [faceDetector](src/lib/faceDetector.ts) - Dynamically loads MediaPipe Face Detector model (uses wasm)
- [chakraKeyframes](src/lib/chakraKeyframes.js) - Common keyframes for ChakraUI animations


## 💽 Global [Contexts](src/contexts/) (Data Providers)
1.



## 💾 Browser Storage
The following data is stored in the browser (localStorage & sessionStorage):
### LocalStorage
1.

### SessionStorage
1. `org_detail`: Organization details
2. `user_detail`: User details
3. `access_token`: Access token
4. `access_token_lite`
5. `access_token_crm`
6. `token_timeout`: Token timeout
7. `refresh_token`: Refresh token


## 🧪 Experimental Features
### LLM Chatbot
- Component: [AiChatWidget.jsx](src/screens/Home/AiChatWidget.jsx)
- TODO: use [`react-chatbot-kit`](https://fredrikoseberg.github.io/react-chatbot-kit-docs/) library.


## 📄 [Pages](pages)

### [Admin](src/pages/admin) Pages:
- Dashboard
  - _Business Dashboard_: Divided into 4 part i.e. TopPanel, EarningOverview, SuccessRate & TopMerchants with there config in there respective file - [src/screens/Admin/Dashboard/BusinessDashboard](src/screens/Admin/Dashboard/BusinessDashboard)

### Other Pages
- Home
  - [_Know Your Commissions_](src/screens/Home/KnowYourCommissionWidget/KnowYourCommissionWidget.jsx):
	- Commission Data is fetched via [CommissionContext](src/contexts/CommissionContext.js) when the user signs-in.

