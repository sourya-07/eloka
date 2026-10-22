# ELOKA Frontend — Architecture (current state and planned changes)

> Status: **APPROVED 2026-10-21** · Paths are relative to `eloka/client/` unless stated.
> Related: [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) · [UI_UX_PLAN.md](./UI_UX_PLAN.md) · [TODO.md](./TODO.md) · client conventions: [`client/CLAUDE.md`](../../client/CLAUDE.md)

## 1. Stack

| Area | What is used |
|---|---|
| Framework | Next.js **15.5.28**, **Pages Router** (`src/pages/`). No `app/` directory and no `middleware.ts`. React 18.3. |
| Language | Mixed: 343 `.js`, 254 `.jsx`, 159 `.ts`, 255 `.tsx`. `tsconfig.json` has `strict:false` and `allowJs`; `jsconfig.json` mirrors its aliases. Bare imports resolve through `baseUrl` (`components`, `contexts`, `hooks`, …). |
| UI | Chakra UI **v2.8** + Emotion, framer-motion 11, react-icons 5, react-select 5, recharts 3 |
| Forms | react-hook-form 7, mostly through `src/components/form-engine/Form` (config-driven). There is no schema library; regexes live in `src/constants/validation.ts`. |
| Data | `fetcher` (`src/lib/apiHelper.js`), `useApiFetch`, SWR 2 with a localStorage provider |
| State | React Context + `useReducer`. No Redux, Zustand or React Query. |
| Tests | Jest 29 + React Testing Library 15 (jsdom, fake timers, coverage threshold 60%). 246 suites. `mock-eps` tests use `node:test`. |
| Storybook | 8.4, 159 stories. `preview.js` does not load the app theme (fixed in DS-4). |
| Tooling | npm (`legacy-peer-deps`), Node 20 (`.nvmrc`), ESLint 9 flat config, Prettier (tabs, width 4), Husky + lint-staged + commitlint |

## 2. Directory structure (current)

```
client/
├── src/                    all app code; imported as "@/…" (e.g. "@/components/ui/Button")
│   ├── pages/              thin route files (+ pageMeta), _app.tsx, _document.tsx, api/ proxies
│   ├── screens/            one screen per route (Admin/*, LoginPanel, Home, History, …)
│   ├── features/           self-contained features: onboarding, kyc-verification, digikhata, merchant-evalue, reverse-evalue
│   ├── components/
│   │   ├── ui/             ~73 reusable wrappers (Button, Input, Table, Stepper, Dropzone, …)
│   │   ├── layout/         Layout, LayoutLogin, LayoutGateway, DynamicPopupModuleLoader
│   │   └── form-engine/    transaction framework: Form, Pintwin, KeyboardNumeric, UidaiFingerprint, Value
│   ├── contexts/ hooks/ constants/
│   ├── lib/                API client, proxy rules, org loading, CMS, inventory, maps, face detector
│   ├── utils/              small pure functions
│   └── styles/             themes.tsx (the only theme), globals.ts, theme_system/theme/{components,foundations,breakpoints}
├── __tests__/              mirrors src/ (test-utils/, fixtures/)
├── tools/                  dev-only: mock-eps/ (EPS mock on :4010), plop-templates/, scripts/
├── public/  .storybook/
└── docs/                   client notes (+ docs/memory-bank/)
```

## 3. Routing and layouts

**Pages:**
- Pages declare `Page.pageMeta = { title, isSubPage, hideMenu, isFixedBottomAppBar }`, and can set `getLayout`.
- Route groups:
  - public: `/`, `/privacy`, `/delete_my_account`, `/gateway/[...id]`
  - onboarding: `/signup`, `/onboard`, `/agent-onboarding`
  - agent: `/home`, `/history`, `/my-network/**`, `/transaction/[...id]`, `/products/**`, …
  - admin: `/admin/**`

**Layouts:**
- `Layout` is the default. When logged in it renders NavBar, SideBar (lg and up), BottomAppBar (below lg), the kbar CommandBar and the Android bridge.
- `LayoutLogin` is used for `/`; `LayoutGateway` for the gateway.

**Quirk:** `pageExtensions` is not set, so sibling component files under `src/pages/` are reachable as routes (FQ-6).

## 4. State management

The provider stack in `src/pages/_app.tsx`, from outermost to innermost:

`ChakraProvider` › `AppSourceProvider` › `OrgDetailProvider` › `UserProvider` › `CopilotProvider` › `KBarLazyProvider` › `GlobalSearchProvider` › `MenuProvider` › `WalletProvider` › `DashboardDataProvider` › `RouteProtecter` › `SWRConfig` › `PubSubProvider` › `NotificationProvider` › `EarningSummaryProvider` › `CommissionSummaryProvider` › `NetworkUsersProvider` › `TodoProvider` › `ErrorBoundary` › layout

Features keep their own contexts (for example `src/features/onboarding/context/OnboardingContext.tsx`). Feature flags live in `src/constants/featureFlags.ts` and are read with `src/hooks/useFeatureFlag.tsx`.

## 5. Styling architecture (current)

**Theme:** `src/styles/themes.tsx` → `light = extendTheme({...})`.
- Breakpoints: sm 440, md 768, lg 1024, xl 1200, 2xl 1800.
- **Semantic tokens:** `bg.*`, `text.*`, `border.*`, `cta.*`, `success|danger|warning|info.*`. App code does not use them yet.
- **Legacy colours:** `primary` and `accent` (tenant-overridable, 3 stops each), plus flat keys (`light`, `dark`, `hint`, `divider`, …).
- **Fonts:** Inter is declared but not loaded.
- **Font weights:** all collapsed to 500.
- **Component themes** live in `src/styles/theme_system/theme/components/*` (Button, Badge, Table, Tabs, Menu, Checkbox, Radio, Switch).

**Tenant theme:**
- `_app.tsx` `getInitialProps` resolves the org server-side (interaction 677, in-memory cache, 1829 → 404).
- The component body then merges `org.metadata.theme` over `primary`/`accent` and derives `navbar.*`, `sidebar.*`, `status.*` and `logo.*` from `navstyle` (lines 84–151).

**Usage today:**

| Pattern | Count |
|---|---|
| Raw Chakra scale (`gray.500` etc.) | 884 |
| Legacy `primary`/`accent` | 401 |
| Flat legacy keys | 308 |
| Hex literals | ~400 |
| Semantic tokens | 0 |

## 6. API integration, authentication and route protection (current)

**API calls:**
- `src/lib/apiHelper.js` `fetcher`: POST JSON by default; adds `source`/`client_ref_id`; Bearer token; 2-minute timeout; multipart support; `ApiError` / `UnauthorizedError`.
- Calls go either directly to `NEXT_PUBLIC_API_BASE_URL` or through the proxies `src/pages/api/{transaction,transactionjson,upload}.js` (`src/lib/proxyHelper.js`).
- EPS envelope: requests carry `interaction_type_id`; responses carry `status` / `response_type_id` / `data`.

**Session:**
- `src/contexts/UserContext.js` + `UserReducer.js`.
- Tokens live in sessionStorage. Refresh is `src/hooks/useRefreshToken.js` → `POST /authentication/token`; revoke is `/authentication/revoke`.

**Guard:**
- `src/components/ui/RouteProtecter/RouteProtecter.jsx` runs client-side, with lists in `src/constants/validRoutes.js`. The only split is admin vs non-admin by URL prefix; while onboarding, the user is forced to `/signup`.
- Per-product access comes from the menu API (`/transactions/wlc`).

**FE-R2 uses different backend contracts.** The new screens call **REST** `/v1/onboarding/**` and `/v1/admin/**` (problem+json, ETag, Idempotency-Key), not EPS envelopes. They therefore get their own clients (§6.3), and the EPS `fetcher` stays untouched.

### 6.1 Planned: platform-host mode (DS-5)

```
getInitialProps(host)
  ├─ host ∈ WLC_PLATFORM_HOSTS (server env), or in dev the path starts with /partner or /staff
  │     → org = null, platform = true  (no 677 lookup, no 1829 → 404)
  └─ otherwise → existing tenant resolution (unchanged)
```

When `platform` is true, the theme is `buildOrgTheme(null, light, { platform: true })`: neutral layer, `brand` → `cta`, accent `#11299E`.

### 6.2 Planned: `src/lib/buildOrgTheme.ts` (DS-2)

- A pure function that takes the org and the base theme and returns a Chakra theme.
- It reproduces the current merge exactly for `primary`, `accent`, `navbar`, `sidebar`, `status` and `logo`. A parity test enforces this.
- It adds the `brand.*` contrast slot (UI_UX_PLAN §2.1).
- It is also used by the APP-13 live preview.

### 6.3 Planned: FE-R2 API clients

**Applicant client:** `src/features/la-onboarding/api/client.ts`

```
request(method, path, { body, ifMatch?: 'auto' | string, idempotent?: boolean, reauthToken? })
  headers: Authorization: Bearer <la_access_token>
           If-Match: "v<version>"       (writes; taken from the last snapshot ETag)
           Idempotency-Key: <uuid>      (one per user action; reused if the same action is retried)
           X-Reauth-Token: <token>      (bank / e-sign / withdraw)
  401 SESSION_EXPIRED → single-flight POST /session/refresh, then retry once; on failure → sign-out (APP-21)
  412 → throw StaleError(current snapshot)
  problem+json → throw ApiProblem{ status, code, detail, invalid_params, missing_steps, missing_documents, attempts_left, current, … }
```

**Staff client:** `src/features/staff-console/api/client.ts` follows the same pattern, with the `STAFF` session and `/auth/refresh`.

**Sessions:**
- applicant: sessionStorage `la_access_token`, `la_refresh_token`, `la_expires_at`
- staff: `staff_access_token`, `staff_refresh_token`, `staff_expires_at`
- Neither ever touches `UserContext` keys.

**Transport:** decided in APP-A. See [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md) FQ-15. The recommended option is a new pass-through proxy `src/pages/api/v1/[...path].ts` that keeps HTTP statuses and headers, so the documented "go through the proxy" rule still holds. The alternative is direct calls to `NEXT_PUBLIC_API_BASE_URL`; the CSP already allows the API host.

### 6.4 Planned: route guard

`src/constants/validRoutes.js` gets `publicSections: ["gateway", "partner", "staff"]`. `RouteProtecter` then ignores these areas, and `LayoutPlatform` and `LayoutStaff` enforce their own sessions: no token → sign-in screen.

## 7. Component hierarchy and mapping (Stitch design → code)

### 7.1 Applicant

```
src/pages/partner/onboarding/index.tsx      → <ApplicantEntry/>   (APP-01/02, or redirect to hub)
src/pages/partner/onboarding/[step].tsx     → <StepPage stepKey/> (APP-04…15)
src/pages/partner/onboarding/status.tsx     → <StatusPage/>       (APP-16/17/18)
  getLayout → LayoutPlatform
     └─ ApplicantSessionProvider
          └─ ApplicationProvider (snapshot + ETag, useApplication)
               ├─ ProgressRail (Stepper) | MobileProgressBar
               ├─ PageHeader (step title, StatusBadge, save indicator)
               └─ <StepForm …> → FormField / Dropzone / Camera / OtpInput / DocumentTile / AppPreview
                    └─ sticky ActionBar (primary, secondary)
Dialogs: ReauthDialog (APP-19), WithdrawDialog (APP-20), ConfirmDialog
```

### 7.2 Staff

```
src/pages/staff/index.tsx                     → <StaffSignIn/> or redirect
src/pages/staff/applications/index.tsx        → <ReviewQueue/>  (Table + filters)
src/pages/staff/applications/[id].tsx         → <CaseView/>     (PageHeader + sections)
  getLayout → LayoutStaff
     └─ StaffSessionProvider (/me permissions)
Dialogs: DocumentReviewDialog, VerificationDecisionDialog, CorrectionsDialog,
         RecommendationDialog, DecisionDialog (approve/reject/hold), PiiRevealDialog
```

### 7.3 Mapping Stitch design elements to code

| Stitch element | Implemented with |
|---|---|
| Buttons (all variants) | `src/components/ui/Button` (CMP-12) |
| Text fields with label, help and error | `src/components/ui/FormField` (CMP-4) + `src/components/ui/Input` / `Select` / `Textarea` |
| OTP boxes | `src/components/ui/OtpInput` (CMP-13) |
| Status chips | `src/components/ui/StatusBadge` (CMP-1) |
| Step progress | `src/components/ui/Stepper` (reuse) |
| Cards and panels | `src/components/ui/SectionCard` (CMP-6); `src/components/ui/Card` stays for R1 |
| Definition lists and summaries | `src/components/ui/KeyValueList` (CMP-7) |
| Upload areas | `src/components/ui/Dropzone` (reuse) + `src/components/ui/DocumentTile` (CMP-9) |
| Camera capture | `src/components/ui/Camera` (reuse) |
| Data tables | `src/components/ui/Table` (reuse) |
| Modals | `src/components/ui/Modal` + `src/components/ui/ConfirmDialog` (CMP-10) |
| Timelines | `src/components/ui/Timeline` (CMP-8) |
| Empty and error panels | `src/components/ui/EmptyState` / `ErrorState` (CMP-2/3) |
| Masked values | `src/components/ui/MaskedValue` (CMP-11) |
| Page headers | `src/components/ui/PageHeader` (CMP-5) |
| Theme preview | `src/screens/Admin/Configurations/AppPreview` (reuse) + `buildOrgTheme` |
| Colour picker | `src/components/ui/ColorPickerWidget` (reuse) |

**Porting rules** (adapted from `stitch-build/react-components`):
- Every reusable pattern becomes its own component.
- Logic lives in hooks.
- Data comes from APIs, never `mockData`.
- Tailwind classes become Chakra props and tokens; no hex values.
- `href="#"` becomes a real `next/link`.
- No dark mode, because the app is light-only.
- No license headers.

## 8. Reuse, refactor and replace decisions, and file-level changes

**Classes:** Reuse = use without changes · Extend = add, compatible · Refactor = restructure, same behaviour · Replace = new implementation · Add = new file · **No change**.

| Path | Current responsibility | Class | Proposed change | Reason / screen | Risk | Tests |
|---|---|---|---|---|---|---|
| `src/styles/themes.tsx` | Only theme | Extend | Semantic and status tokens, weights, tertiary contrast, component themes | DS-00, all | M | Contrast unit test; regression gate |
| `src/styles/theme_system/theme/foundations/*` | Empty files | Extend | Colour, typography and shadow modules | DS-1 | L | covered by themes tests |
| `src/styles/theme_system/theme/components/*` | Component themes | Extend | Input, Select, Card, Modal, Alert themes | DS-1 | L | stories |
| `src/lib/buildOrgTheme.ts` | — | Add | Pure theme builder + `brand.*` | DS-2, APP-13 | L | parity + contrast tests |
| `src/pages/_app.tsx` | Tenant resolution, theme, providers | Refactor (narrow) | Call `buildOrgTheme`; `next/font` Inter; platform-host branch | DS-2, DS-3, DS-5 | **H** | host classifier tests; manual tenant / unknown / platform check |
| `src/constants/validRoutes.js` | Guard lists | Extend | `publicSections += partner, staff` | APP-B | M | RouteProtecter tests |
| `next.config.js` | CSP, rewrites | Extend | Google Identity origins in the CSP (flag-gated) | STF-A | L | build |
| `.env.example` | Env docs | Extend | `WLC_PLATFORM_HOSTS` | DS-5 | L | — |
| `.storybook/preview.js` | Story parameters | Refactor | Wrap in `ChakraProvider theme={light}` | DS-4 | L | run Storybook |
| `src/components/ui/{StatusBadge,EmptyState,ErrorState,FormField,PageHeader,SectionCard,KeyValueList,Timeline,DocumentTile,ConfirmDialog,MaskedValue}/` | — | Add | New primitives | CMP-1…11 | L | unit + story each |
| `src/components/ui/Button/` | Wrapper, default `accent` | Extend | Union + `brand` variant; default unchanged until R1-01 | CMP-12 | M | existing + new tests |
| `src/components/ui/Switch/` | div toggle | Refactor | `role="switch"`, keyboard | CMP-13 | M | keyboard tests |
| `src/components/ui/IcoButton/` | `Center as=button` | Extend | Required `aria-label` | CMP-13 | M (callers) | tests |
| `src/components/ui/Icon/` | SVG with slug aria-label | Refactor | `aria-hidden` unless `title` | CMP-13 | L | tests |
| `src/components/ui/OtpInput/` | PinInput | Extend | Per-digit labels | CMP-13 | L | tests |
| `src/components/ui/Card/` | Card, md height 600 | Extend | `variant` prop; legacy default | CMP-14 | L | tests |
| `src/components/ui/Stepper/`, `Dropzone/`, `Camera/`, `LocationCapture/`, `Table/`, `Modal/`, `ColorPickerWidget/`, `OrgLogo/` | as named | Reuse | — | APP, STF | L | existing |
| `src/screens/Admin/Configurations/AppPreview` | Theme preview | Reuse (maybe Extend: accept a theme prop) | APP-13 | L | test if changed |
| `src/features/la-onboarding/**` | — | Add | api, types, context, hooks, steps, page-components, constants | APP-A…T | M | feature tests |
| `src/pages/partner/onboarding/{index,[step],status}.tsx` | — | Add | Thin routes | APP-B | L | page tests |
| `src/components/layout/LayoutPlatform/` | — | Add | Applicant shell | APP-B | L | tests |
| `src/features/staff-console/**` | — | Add | api, context, hooks, page-components | STF-A…H | M | feature tests |
| `src/pages/staff/{index,applications/index,applications/[id]}.tsx` | — | Add | Thin routes | STF-A | L | page tests |
| `src/components/layout/LayoutStaff/` | — | Add | Staff shell | STF-A | L | tests |
| `src/components/ui/{NavBar,SideBar,BottomAppBar,PageTitle}/` | App shell | Refactor (visual) | Tokens, brand slot, focus, labels | R1-01 | M–H | existing + visual check |
| `src/screens/**` (per R1 group) | Screens | Refactor (visual) | Tokens, shared states | R1-02…11 | M per group | existing + visual check |
| `client/.stitch/**` | — | Add | DESIGN.md, SITE.md, metadata, prompts, designs | ST-* | L | — |
| `docs/frontend/*`, `docs/README.md`, root `CLAUDE.md`, `client/CLAUDE.md`, `docs/LOCAL_DEVELOPMENT.md` | Docs | Add / Extend | Plan and how-to | F0-1, QA-4 | L | — |

### 8.1 No change permitted (in this programme)

- `server/**`. Backend gaps are recorded in [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md), not fixed here.
- `tools/mock-eps/**`. New fixtures only if the owner asks.
- The logic in `src/lib/{apiHelper,loginHelper,proxyHelper,fetchOrgDetailsHelper}.js`.
- `src/contexts/{UserContext,UserReducer,MenuContext,OrgDetailContext}.js`.
- `src/hooks/{useLogin,useRefreshToken,useApiFetch}.js`.
- `src/pages/api/**`.
- The logic in `src/components/form-engine/**`.
- `src/features/onboarding/services/**` (the e-sign adapters are reused as they are).
- `src/constants/EpsTransactions.js` and every interaction id and payload.

## 9. Proposed new structure (additions only)

```
client/
├── .stitch/                         DESIGN.md, SITE.md, metadata.json, prompts/, designs/
├── src/lib/buildOrgTheme.ts
├── src/components/ui/{StatusBadge,EmptyState,ErrorState,FormField,PageHeader,SectionCard,
│               KeyValueList,Timeline,DocumentTile,ConfirmDialog,MaskedValue}/
├── src/components/layout/{LayoutPlatform,LayoutStaff}/
├── src/features/
│   ├── la-onboarding/
│   │   ├── api/{client.ts,types.ts,endpoints.ts}
│   │   ├── context/{ApplicantSessionContext.tsx,ApplicationContext.tsx}
│   │   ├── src/hooks/{useApplication,useStepAction,useReauth,useSubdomainAvailability,useDocuments}.ts
│   │   ├── steps/{Consent,Program,BusinessPan,BusinessDetails,Email,SignatoryKyc,LivePhoto,
│   │   │          Documents,BankAccount,WhiteLabel,Declaration,Agreement}.tsx
│   │   ├── src/screens/{ApplicantEntry,Hub,StepPage,StatusPage}.tsx
│   │   ├── src/components/ui/{ProgressRail,ActionBar,ReauthDialog,WithdrawDialog}.tsx
│   │   └── constants.ts             step order, labels, copy, document rules mirror
│   └── staff-console/
│       ├── api/{client.ts,types.ts}
│       ├── context/StaffSessionContext.tsx
│       ├── src/hooks/{useQueue,useCase,useCaseAction}.ts
│       ├── src/screens/{StaffSignIn,ReviewQueue,CaseView}.tsx
│       ├── src/components/ui/{DocumentReviewDialog,VerificationDecisionDialog,CorrectionsDialog,
│       │               RecommendationDialog,DecisionDialog,PiiRevealDialog,AuditTrail}.tsx
│       └── constants.ts
└── src/pages/{partner/onboarding/*, staff/*}
```

Tests mirror these paths under `__tests__/`, for example `__tests__/features/la-onboarding/**`. This follows the existing `__tests__/features` folder.
