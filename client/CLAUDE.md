# CLAUDE.md — client/

Guidance for the **frontend** of the ELOKA repo. Read the root [`CLAUDE.md`](../CLAUDE.md) first (repo map, SDLC, rules shared with `server/`). All paths below are relative to `client/`.

## Commands

```bash
npm run dev:mock      # Dev server on :3002 + mock EPS on :4010, dummy logins (OTP 123456)
npm run dev           # Dev server on :3002 (HTTP) against the EPS in .env.local
npm run dev.https     # Dev server on :3004 (HTTPS — needed for some APIs)
npm run build         # Production build
npm run lint          # ESLint check
npm run lint:error    # ESLint errors only (faster pre-commit check)
npm run lint:fix      # Auto-fix lint issues
npm run test          # Full test suite
npm run test:quick    # Run specific test file: npm run test:quick path/to/test
npm run test:watch    # Watch mode
npm run test:coverage # Coverage report (threshold: 60% lines)
npm run analyze       # Bundle analyzer (set ANALYZE=true)
npm run sb            # Storybook on :6006
npm run g             # Plop code generator (creates component scaffolding)
npm run mock          # Mock EPS server only (:4010)
npm run test:mock     # Contract tests for the mock EPS server (node:test)
```

**Production image:** `docker build --build-arg NEXT_PUBLIC_API_BASE_URL=https://api.<root> -t eloka-client .` (standalone output, non-root, `/api/health`). `NEXT_PUBLIC_*` values are baked in at build time; server-side settings are runtime env. Runs behind the Caddy edge in `../deploy/` ([runbook](../docs/deploy/RUNBOOK.md)).

**Security pins:** `package.json` `overrides` force patched versions of `browserslist`, `lodash`, `nanoid@3`, `postcss`, `serialize-javascript` and `source-map-js` (flagged by Trivy in the production image, 2026-10-16). Remove an entry once the packages that pull it in ship the fix themselves. Install with Node 20's npm (`.nvmrc`); `.npmrc` sets `legacy-peer-deps`. CI runs Node 20, so check scripts there too (`npx -y node@20 ...`): for example `node --test "dir/*.js"` with a quoted glob only works on Node 21+.

**Git hooks:** husky lives in `client/.husky` but runs from the repo root (`npm run prepare` sets `core.hooksPath`). On commit: lint-staged (prettier + eslint on staged client files) and commitlint. Before a PR: `npm run lint:error && npm run test:mock && npm test`, or `make ci-client` from the repo root to run the CI job exactly as GitHub does (Node 20). Tests must not depend on the machine: the time zone is pinned to Asia/Kolkata (`jest.global-setup.js`); mock `navigator`, `window` sizes and dates instead of reading the real ones.

## Local dummy backend (`tools/mock-eps/`)

`tools/mock-eps/` is a zero-dependency Node server that imitates EPS for local work (ADR: `../docs/adr/0003-local-mock-eps.md`). It is a dev tool: never import it from app code.

- `lib/MockEpsServer.js`: routing (`/__mock/*` debug → tf-req URI → fixed path → `interaction_type_id`), CORS, logging.
- `lib/UserStore.js`: in-memory users and onboarding progress (`pendingRoles` = steps still to do, sent as `role_list`).
- `data/users.js`: dummy logins and the KYC plan per role. `data/catalog.js`: menu, lists, notifications.
- `handlers/*.js`: one map per area (`auth`, `onboarding`, `dashboard`), keyed by interaction id or tf-req URI.
- `test/`: contract tests that replay the frontend's request shapes.

When the app starts calling a new EPS interaction, add its handler and a test here, and update `../docs/LOCAL_DEVELOPMENT.md` if a dummy user changes. Unknown calls answer `status: 1` with "has no mock yet" and log `(NOT MOCKED)`.

## Folder layout

```
client/
├── src/                 all app code
│   ├── pages/           Next.js routes (thin: import a screen)
│   ├── screens/         one folder per route screen
│   ├── features/        self-contained flows (components/, context/, hooks/, screens/, utils/)
│   ├── components/
│   │   ├── ui/          shared building blocks (Button, Input, Table, …)
│   │   ├── layout/      app shell (Layout, LayoutLogin, LayoutGateway, DynamicPopupModuleLoader)
│   │   └── form-engine/ config-driven forms (Form, Pintwin, …)
│   ├── contexts/  hooks/  constants/  styles/
│   ├── lib/             API client, proxy rules, org loading, third-party wrappers
│   └── utils/           small pure functions
├── __tests__/           Jest tests, mirroring src/ (plus test-utils/, fixtures/)
├── tools/               dev-only: mock-eps/, plop-templates/, scripts/
├── public/  .storybook/  docs/
└── config files (next, jest, eslint, tsconfig, …)
```

**Imports:** app code is imported with the `@/` alias, which points at `src/` (`tsconfig.json`, `jest.config.js` and `.storybook/main.js` all define it). Write `import { Button } from "@/components/ui"`, not a long `../../` path. Relative imports are fine inside one folder (a component and its parts, a feature's own files). Tests import render helpers from `test-utils`.

## Component-based structure (how to add UI)

- Reusable UI → `src/components/ui/<Name>/` with `<Name>.tsx`, `index.ts`, a story and a test in `__tests__/components/ui/<Name>/`. Generate with `npm run g`.
- A screen for a route → `src/screens/<Area>/<Screen>/`; the file in `src/pages/` only imports it.
- A self-contained feature (own flow, state, hooks) → `src/features/<feature>/` with `components/`, `context/`, `hooks/`, `screens/`, `utils/`, `constants.ts`.
- Business logic in hooks or `src/utils/`, not inside JSX. Components receive data through props; global state through the contexts in `src/contexts/`.
- New code is TypeScript, functional components, explicit prop interfaces.

---

## Architecture Overview

**What this is:** A multi-tenant, white-labeled fintech SaaS platform. Two personas — Admin (configures agents, products, pricing, branding) and Agent (executes financial transactions, manages wallet). The same codebase serves hundreds of organizations via subdomain routing with per-org branding and feature exposure.

### Request → Response path

Every API call from the browser goes through a Next.js server-side proxy at **`src/pages/api/transaction.js`** (also `transactionjson.js`, `upload.js`), which forwards POST requests to the backend. Shared proxy rules live in `src/lib/proxyHelper.js`: the target is `API_INTERNAL_BASE_URL` when set (inside Docker, e.g. `http://server:8080`) else `NEXT_PUBLIC_API_BASE_URL`; the token, `X-Request-Id` and the browser IP (`X-Forwarded-For`) are forwarded; a backend 401 is passed through so `fetcher` refreshes the token, everything else stays 200. Never call the backend URL directly from client code — always use `fetcher('/api/transaction', { body: { ... } })` from `src/lib/apiHelper.js`. The `fetcher` helper adds default fields (`source`, `client_ref_id`), handles token rotation, enforces a 2-minute timeout, and throws `UnauthorizedError` on 401s.

### How multi-tenancy works

1. A request arrives at a subdomain (e.g. `ekoshield.elokademo.in`).
2. `src/pages/_app.tsx` calls `fetchOrgDetailsHelper.js` server-side to load org metadata (`org_id`, `app_name`, `logo`, color theme, feature flags) from the org metadata service.
3. The result is stored in `SessionStorage["org_detail"]` and surfaced via `useOrgDetailContext()`.
4. The Chakra UI theme is dynamically built from org colors (defined in `src/constants/colorThemes.js`). Custom theme tokens override defaults in `src/styles/themes.tsx`.

### Auth and session

Tokens are in `SessionStorage` — keys: `access_token`, `refresh_token`, `access_token_lite`, `token_timeout`. The `UserContext` (`src/contexts/UserContext.js`) owns login state. Route protection is in `src/components/ui/RouteProtecter/`. The login flow lives in `src/screens/LoginPanel/` (see `LoginWidget` → `Login` → `VerifyOtp`). `useRestoreLastLoginOrRoute.ts` resumes an interrupted OTP step from `localStorage` on reload.

### Layout shell

`src/components/layout/Layout/Layout.tsx` composes:
- `src/components/ui/NavBar` — top bar with search and user chip
- `src/components/ui/SideBar` — desktop left nav (menu from `src/constants/SidebarMenu.ts`, wallet balance, org logo)
- `src/components/ui/BottomAppBar` — mobile bottom nav

Pages in `src/pages/` are thin: they import the matching component from `src/screens/**` and pass route params. Heavy or rarely-hit components use `dynamic(() => import(...), { ssr: false })`.

### Menus and navigation

`src/constants/SidebarMenu.ts` defines the static menu shape. The dynamic transaction menu (financial product items) is fetched and merged by `src/contexts/MenuContext.tsx` using `src/lib/processTransactionData.js`. Adding a new sidebar item means editing `SidebarMenu.ts` only.

### Transaction execution (EkoConnect Widget)

The actual financial transaction UI (money transfer, bill pay, etc.) is NOT rendered as React components. It runs inside the **EkoConnect Widget** (`src/components/ui/EkoConnectWidget/EkoConnectWidget.jsx`), which mounts a shadow-DOM Google Polymer v1 web component. The React layer passes metadata (product config, user tokens, theme, language) to the widget and listens for events back via the PubSub system. This is intentional isolation — do not attempt to convert widget flows to React.

### `src/features/` vs `src/screens/`

- `src/screens/` — components tied to main app routes, may depend on global contexts.
- `src/features/` — self-contained feature modules (digikhata wallet, KYC verification, onboarding) with their own sub-contexts, hooks, and multi-step flows. Each feature folder is independently navigable.

### `src/components/form-engine/` (Transaction Framework)

Low-code form/table primitives used to build transaction screens declaratively from config. If you see a form driven by a JSON config in `ProductBusinessConfigurations.ts`, it's using the form engine. Don't manually build what this already handles.

### Feature flags

```ts
const [isEnabled] = useFeatureFlag('FLAG_KEY');
```

Flags are defined in `src/constants/featureFlags.ts` with optional constraints (env, org ID, role). New flags go there. Never inline env checks for feature decisions — use the flag system.

### Pub/Sub

Cross-component events (show dialog, Android response, refresh encryption key) go through `src/contexts/PubSubContext.js`. Topics are in `src/constants/PubSubTopics.ts`. Don't add new `window.dispatchEvent` calls — extend PubSubTopics instead.

### Android/WebView bridge

All `window.Android.*` calls are centralized in `src/utils/AndroidUtils.ts`. Never call the Android bridge directly from feature components.

### Styling

Use custom wrappers in `src/components/ui/` (`Button`, `Input`, `Select`, etc.) over raw Chakra primitives. Theme tokens are in `src/styles/themes.tsx` — reference them by name (e.g. `color="primary.light"`) rather than hardcoding hex values. Global CSS is in `src/styles/globals.ts`.

### Testing

Tests live in `__tests__/` mirroring the source tree. Use render helpers from `__tests__/test-utils/`:
- `render` — standalone components
- `pageRender` — components that need logged-in user context
- `adminRender` — admin-context components
- `loggedOutPageRender` — public pages

Minimum per PR: smoke render + one behavioral assertion on any logic touched.

### Data storage rules

- `SessionStorage` — auth tokens, org metadata, user metadata (short-lived, clears on tab close)
- `useDailyCacheState` hook — data that's expensive to re-fetch but can be stale for one day
- Never store sensitive auth data in `localStorage`
