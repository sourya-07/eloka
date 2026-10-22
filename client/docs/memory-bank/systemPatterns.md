# System Patterns: Eloka Web App

## Architecture Overview
-   **Framework:** Next.js (SSR, Dynamic Routes) built on React.
-   **Language:** TypeScript.
-   **Architecture Style:** Modular, component-based architecture favoring functional and declarative patterns. Avoids OOP/classes.
-   **Multi-Tenant SaaS:** Designed to support multiple organizations (tenants) with white-labeling capabilities.
-   **State Management:** Primarily uses React Context API (`src/contexts/`) and custom hooks (`src/hooks/`). Local/Session storage is used for specific data persistence (`useLocalStorage`, `useSessionStorage`, `useDailyCacheState`).
-   **UI:** Chakra UI serves as the base design system, but the application prioritizes custom reusable components (`src/components/ui/`) built on top of or replacing Chakra UI elements.
-   **Routing:** Managed by Next.js. Differentiates between public (`src/constants/validRoutes.js`) and private routes (requiring authentication).

## Key Technical Decisions & Patterns
-   **Functional Programming:** Emphasis on pure functions, immutability, and avoiding side effects where possible. Heavy use of React Hooks.
-   **Modularity:** Code is organized into distinct directories based on function (`components`, `hooks`, `pages`, `utils`, etc.). Reusability is key.
-   **Custom Components:** Preference for building custom UI components (`src/components/ui/Button`, `src/components/ui/Input`) over directly using Chakra UI components to ensure consistency and control.
-   **Feature Flags:** Centralized system (`src/constants/featureFlags.ts`, `src/hooks/useFeatureFlag.tsx`) for toggling features based on environment, user type, etc.
-   **Pub/Sub System:** Internal event bus (`src/contexts/PubSubContext.js`, `src/constants/PubSubTopics.ts`) for decoupled communication between components.
-   **Configuration Driven UI:** Certain features, like Admin Pricing Configuration (`src/constants/ProductBusinessConfigurations.ts`) and Sidebar/Navbar menus (`src/constants/SidebarMenu.ts`, `src/constants/profileCardMenus.js`), are driven by configuration files.
-   **Android Communication:** Specific mechanism using `JavascriptInterface` and `postMessage` for interaction with an Android WebView wrapper (`src/utils/AndroidUtils.ts`, `src/components/layout/Layout/Layout.tsx`).
-   **Dockerization:** Separate Docker configurations for development (`Dockerfile.dev`) and production (`Dockerfile`) managed via `docker-compose.yaml` for consistent environments.

## Component Relationships & UI Structure
-   **Core Layout:** Managed by `src/components/layout/Layout/Layout.tsx`.
-   **Navigation:**
    -   Top: `src/components/ui/NavBar/NavBar.jsx` (includes `ProfileCard` with menu from `src/constants/profileCardMenus.js`).
    -   Left: `src/components/ui/SideBar/SideBar.jsx` (menu items from `src/constants/SidebarMenu.ts`, data processed via `contexts/MenuContext.tsx`).
    -   Bottom (Small Screens): `src/components/ui/BottomAppBar/BottomAppBar.tsx` (menu items from `src/constants/SidebarMenu.ts`, data processed via `contexts/MenuContext.tsx`).
-   **Page Structure:** Next.js pages reside in `src/pages/`, often utilizing specific components from `src/screens/`.
-   **Low-Code Forms:** Dedicated components under `src/components/form-engine/` for the transaction framework.

## Critical Implementation Paths
-   **Authentication & Authorization:** Login flow (`src/lib/loginHelper.js`), token management (SessionStorage), route protection (`src/components/ui/RouteProtecter/`).
-   **Admin Configuration:** Setting up pricing/business rules via file upload or custom pages (`src/constants/ProductBusinessConfigurations.ts`, `src/screens/Admin/PricingCommission/`).
-   **Agent Transactions:** Core financial operations flow (details likely within specific product page components and contexts).
-   **Data Fetching:** Primarily through custom hooks (`src/hooks/useApiFetch.js`, `src/hooks/useRequest.js`) interacting with backend APIs (`src/lib/apiHelper.js`). Contexts (`src/contexts/`) often manage fetched global state.
-   **Testing:** Standardized setup using `__tests__/test-utils/test-utils.js` wrappers (`pageRender`, `adminRender`, etc.) for rendering components in tests.
