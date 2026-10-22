# ELOKA Frontend Transformation — Implementation Plan

> Status: **APPROVED 2026-10-21** · Scope: `eloka/client` only (no server changes) · Owner of the checklist: [TODO.md](./TODO.md)
> Related: [UI_UX_PLAN.md](./UI_UX_PLAN.md) · [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md) · [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md) · backend blueprint [../onboarding/](../onboarding/ARCHITECTURE.md)

## 1. Executive summary

The ELOKA web client (Next.js 15.5 Pages Router, React 18, Chakra UI v2, mixed JS/TS) works, but it looks dated and uneven, and the Release 2 Local Admin onboarding has no UI.

**Visual problems today:**
- **Two token systems.** A newer black-CTA semantic layer exists in `client/src/styles/themes.tsx` but app code uses it 0 times. Instead, app code uses the raw Chakra palette (884 uses), 400+ hex literals, and font weights that all collapse to 500.
- **Contrast failures.** White on the default accent button measures 2.06:1.
- **Inter is declared but never loaded.**
- **Weak accessibility.** 79 clickable divs, 27 aria-labels in the whole UI, and a Switch the keyboard cannot reach.

**Release 2 onboarding:** the applicant API P7 (A1–A30) and the staff review API P8 (S1–S13, S21) are built and tested on the server, but there is no UI for either.

**Goal:** use Google Stitch to set a premium, trustworthy fintech design language and screen designs. Build them in the existing stack in three stages:
1. a token + component foundation
2. the new Local Admin **applicant flow** and **staff console**
3. a reskin of the existing app, one page group at a time

The stack, APIs, authentication, routing, tenant theming and business logic stay as they are.

### Decisions (2026-10-21)

| # | Decision |
|---|---|
| D1 | Order: foundation → onboarding (applicant + staff) → Release 1 reskin |
| D2 | Stitch output becomes code by **hand-porting to Chakra**. Stitch HTML and screenshots are the visual spec. No Tailwind or shadcn is added. |
| D3 | Platform screens (`partners.<root>`, `staff.<root>`) use the existing neutral layer: black CTA `#0A0A0A`, warm canvas `#FAFAF7`, and Connect Blue `#11299E` as the accent. Tenant pages keep each org's colours. |
| D4 | FE-R2 lives in the same Next.js client as route groups, as agreed in [BACKEND_PLAN.md](../BACKEND_PLAN.md) (P7–P9 table). See [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md) FQ-3. |

## 2. Stitch MCP and skills: availability (verified 2026-10-21)

All three plugins are installed at user and project scope from `google-labs-code-stitch-skills` @ `0337446dadde` (v1.0.0). Each plugin is a **bundle of skills**, not a single skill.

| Plugin | Skills | Use in this project |
|---|---|---|
| `stitch-design` | `generate-design`, `manage-design-system`, `code-to-design`, `extract-design-md`, `extract-static-html`, `upload-to-stitch` | **Primary**: generate, edit and vary screens; turn DESIGN.md into a design system; uploads |
| `stitch-build` | `react-components`, `react-native`, `react-vite-dashboard`, `shadcn-ui`, `remotion` | **Reference only.** See the notes below. |
| `stitch-utilities` | `design-md`, `enhance-prompt`, `taste-design`, `site-md`, `stitch-loop` | **Used:** `design-md` (structure), `enhance-prompt` (prompts), `taste-design` (anti-patterns only), `site-md`. **Not used:** `stitch-loop`, which writes raw HTML into `site/public/` and does not fit this app. |

Why `stitch-build` is reference only:
- `react-components` targets Vite + React Router + Tailwind, expects `src/data/mockData.ts`, and wants `dark:` on every class.
- `shadcn-ui` needs Tailwind plus a shadcn MCP server, which is **not connected**.
- `react-native` is excluded from scope.
- `remotion` is not relevant.

**Stitch MCP server:** `https://stitch.googleapis.com/mcp` (http), authenticated with an `X-Goog-Api-Key` in `~/.claude.json`.
- `list_projects` works and shows no ELOKA project yet.
- Tools: `create_project`, `get_project`, `list_projects`, `delete_project`, `list_screens`, `get_screen`, `generate_screen_from_text`, `edit_screens`, `generate_variants`, `download_file`, `create_design_system`, `update_design_system`, `list_design_systems`, `apply_design_system`, `upload_design_md`, `create_design_system_from_design_md`.

**What Stitch can and cannot do:**

| Capability | Status |
|---|---|
| Reading this repo | No. Designs come back to us as HTML + screenshots through `download_file`. Rendered pages can be sent to Stitch through `code-to-design`. |
| Code export to Chakra or Next | No. Its HTML is Tailwind, so we hand-port (D2). |
| Design-system reuse | Yes: the `designSystem` parameter on generation, and `apply_design_system`. |
| Iteration | Yes: `edit_screens`, `generate_variants` (REFINE / EXPLORE / REIMAGINE). |
| Device types | MOBILE, DESKTOP, TABLET, AGNOSTIC |

**Blocked or optional pieces:**
- `extract-static-html` and `code-to-design` need `puppeteer` (**not installed**) and a running dev server. They are only needed for an optional baseline upload of the current pages, so installing them waits for approval (FQ-1).
- The `react-components` validator needs `@swc/core` in the skill folder. It is not needed, because we hand-port.
- The Chrome DevTools MCP is not connected. We use the `run` skill and screenshots instead.

**Skill checkpoints**, each of which pauses for the owner's OK:
- before the DESIGN.md is uploaded
- before any upload-script run
- the extract strategy choice
- before dev servers or validators run inside the build skills

**Data rule:** Stitch is an external Google service. Prompts and uploads use **fictional sample data only**: no real PAN, mobile, account numbers, partner names or source code. Any `code-to-design` capture comes from `dev:mock` only.

## 3. Current frontend assessment

The full architecture is in [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md). The headlines:

**What exists and works:**
- Pages Router. `_app.tsx` resolves the tenant on the server from the Host header (interaction 677, cached) and builds the Chakra theme from `org.metadata.theme`.
- About 18 nested context providers.
- A client-side `RouteProtecter`. The only access split is admin vs non-admin by URL prefix, plus a forced `/signup` while the user is onboarding.
- Tokens in sessionStorage.
- `fetcher` / `useApiFetch` calls with EPS envelopes, sent either directly or through `/api/transaction`.
- `mock-eps` as a dev backend.
- 246 Jest suites, 159 stories, and no E2E tests.

**Must remain unchanged:**
- interaction contracts and EPS envelopes
- login 515/518/151, refresh and revoke
- tenant lookup and theming, including the `primary`/`accent` key names, which the EkoConnect widget and `manifest-proxy` read
- `disabled_features`, the menu from `/transactions/wlc`, feature flags
- the Android bridge in `Layout`
- print rules for receipts
- the `src/components/form-engine/Form` engine
- the e-sign provider adapters

**Reusable:**
- `src/components/ui/Stepper`, `src/features/onboarding/components/OnboardingLayout.tsx`
- `src/components/ui/{OtpInput,Dropzone,Camera,LocationCapture,Table,Modal,Drawer,Breadcrumb,PageTitle,OrgLogo,ColorPickerWidget}`
- `src/screens/Admin/Configurations/{ThemeConfig,AppPreview}`
- `src/features/onboarding/services/esign/*`
- `src/constants/validation.ts`

**Needs refactoring** (visual layer only):

| Item | Problem |
|---|---|
| `Button` | default `accent` fails contrast; raw and wrapper imports render differently |
| `Card` | forces 600px height at md |
| `Switch` | a div that the keyboard cannot reach |
| `IcoButton` | no required label |
| `Icon` | aria-label on decorative SVGs |
| `OtpInput` | unlabeled fields |
| global `Text` | `userSelect:none` |
| theme `fontWeights` | all set to 500 |
| `theme_system/foundations/*` | empty files |
| Storybook | does not load the app theme |

**New components needed:** `StatusBadge`, `EmptyState`, `ErrorState`, `FormField`, `PageHeader`, `SectionCard`, `KeyValueList`, `Timeline`, `DocumentTile`, `ConfirmDialog`, `MaskedValue`, plus all of FE-R2.

**Blocker:** `_app.tsx` (around lines 388–395) returns 404 when the org lookup answers `1829`. Platform hosts need a "platform mode".

**Found, not fixed here** (logged in [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md) FQ-6):
- the refresh-token result may be lost in `useRefreshToken`
- sibling files under `src/pages/` are reachable as routes
- hard-coded identity fields in the 518/151/677 payloads
- security headers are off unless an env flag is set

## 4. Product requirements (Local Admin onboarding)

Sources: [P7_APPLICANT_API.md](../onboarding/P7_APPLICANT_API.md), [P8_STAFF_REVIEW.md](../onboarding/P8_STAFF_REVIEW.md), [ONBOARDING_STATE_MACHINE.md](../onboarding/ONBOARDING_STATE_MACHINE.md), [ELOKA_ONBOARDING_JOURNEY.md](../onboarding/ELOKA_ONBOARDING_JOURNEY.md), server code `server/src/main/java/in/eko/eloka/onboarding/**`, and the integration tests `OnboardingApplicantIntegrationTest` and `StaffReviewIntegrationTest`.

**Applicant API**

13 steps in recommended order (the backend only enforces prerequisites):
1. `MOBILE_VERIFICATION`
2. `CONSENT`
3. `PROGRAM_SELECTION`
4. `BUSINESS_PAN`
5. `BUSINESS_DETAILS`
6. `EMAIL_VERIFICATION`
7. `SIGNATORY_KYC` (DigiLocker)
8. `LIVE_PHOTO`
9. `DOCUMENTS`
10. `BANK_ACCOUNT`
11. `WHITE_LABEL_SETUP`
12. `DECLARATION`
13. `AGREEMENT_ESIGN`

**Application states:** `IN_PROGRESS, SUBMITTED, UNDER_REVIEW, CORRECTION_REQUIRED, ON_HOLD, APPROVED, REJECTED, PROVISIONING, PROVISIONING_FAILED, GO_LIVE_PENDING, ACTIVE, WITHDRAWN, EXPIRED`

**Step states:** `NOT_STARTED, IN_PROGRESS, PENDING_EXTERNAL, COMPLETED, FAILED, BLOCKED, SKIPPED, LOCKED, REOPENED, NOT_APPLICABLE`

**Rules every request must follow:**
- `If-Match`/ETag on writes (412 returns the `current` snapshot)
- `Idempotency-Key` on side-effect POSTs
- problem+json errors with `code` and `invalid_params`
- step-up re-auth (`X-Reauth-Token`) for bank, e-sign and withdraw
- dependency invalidation, corrections, 30-day idle expiry

**Staff API:**
- Google sign-in, with local OTP for seeded staff in dev
- roles REVIEWER / APPROVER / AUDITOR / ADMIN
- claim and release; document and verification decisions; corrections
- maker-checker approve, plus reject and hold
- audited PII reveal and audit trail

**Not built on the backend, so no screens:** P9 provisioning, go-live and activation (S14–S20), custom domains, leads and invites, springdoc OpenAPI.

## 5. Recommended design direction

The full direction is in [UI_UX_PLAN.md](./UI_UX_PLAN.md) §1–3. In short: "quiet authority".
- **Look:** warm-neutral surfaces, one decisive CTA, clear status language.
- **Feel:** balanced density, symmetric layouts, restrained motion.
- **Font:** Inter loaded with `next/font`, with weights restored (400 / 500 / 600 / 700).
- **Tenant brand:** a computed `brand.*` slot with WCAG-safe foreground colours.
- **Accessibility target:** WCAG 2.2 AA.

## 6. Proposed UI architecture

The full detail is in [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md) §6–8.

**Foundation:**
- extend `src/styles/themes.tsx`
- new pure `src/lib/buildOrgTheme.ts`, called by `_app.tsx`; a test locks the legacy output
- platform-host mode in `_app.tsx`
- Storybook themed

**Shared components:** 11 new primitives in `src/components/ui/`. Six existing components get compatible accessibility and variant upgrades.

**Applicant:**
- code in `src/features/la-onboarding/`
- routes `src/pages/partner/onboarding/*`, layout `LayoutPlatform`
- its own REST client covering problem+json, ETag, Idempotency-Key, re-auth and refresh
- its own session (`la_*` keys)

**Staff:**
- code in `src/features/staff-console/`
- routes `src/pages/staff/*`, layout `LayoutStaff`
- `STAFF` session; actions shown or hidden from `/me` permissions

**Route guard:** `publicSections += partner, staff`. These areas are guarded by their own layouts.

**Reskin:** tokens first, then the shell, then one page group per commit. No logic edits.

## 7. Screen inventory

All 21 applicant screens (APP-01…21), 11 staff screens (STF-01…11), 11 reskin groups (R1-01…11) and the DS-00 component sheet are specified in [UI_UX_PLAN.md](./UI_UX_PLAN.md) §4–6, with traceability in §8.

## 8. Component mapping and file-level change plan

See [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md) §7 (component mapping) and §8 (file table, including the **no change permitted** list).

## 9. API and business-logic preservation

- **No change** to `server/**`, interaction ids, EPS payloads, `src/lib/{apiHelper,loginHelper,proxyHelper,fetchOrgDetailsHelper}.js` logic, `src/contexts/{UserContext,UserReducer,MenuContext,OrgDetailContext}.js`, `src/hooks/{useLogin,useRefreshToken,useApiFetch}.js`, `src/pages/api/**`, `src/components/form-engine/**` logic, `src/features/onboarding/services/**` or `src/constants/EpsTransactions.js`.
- **FE-R2 uses only endpoints that exist.** Server-side validation stays the authority; the client mirrors the regexes only for early feedback. Any missing endpoint is written down as a gap, never faked.
- **Tenant theme parity:** a test asserts that `buildOrgTheme` produces exactly the legacy `primary`, `accent`, `navbar`, `sidebar`, `status` and `logo` values for every preset.
- **Session isolation:** applicant and staff tokens never touch `UserContext` storage keys.

## 10. Stitch generation and review sequence

See [UI_UX_PLAN.md](./UI_UX_PLAN.md) §7:
1. ST-1 DESIGN.md
2. ST-2 approval
3. ST-3 project and design system
4. ST-4 SITE.md
5. ST-5 prompts
6. ST-6 generation in dependency order: DS-00, then APP, then STF, then R1
7. ST-7 refinement
8. ST-8 review gates, one per batch

A generated Stitch screen is **a spec, not a feature**.

## 11. Testing plan

**Unit and component (Jest + RTL):**
- every new component: roles, labels, keyboard, states
- `buildOrgTheme` parity and contrast across the 6 presets
- the API client: ETag, 412, idempotency, problem+json, re-auth, refresh

**Feature tests:**
- applicant: OTP errors, steps rendered from snapshot fixtures built from P7 integration-test bodies, PAN outcomes, sub-domain availability, documents, corrections, terminal states
- staff: queue filters, permission-gated actions, maker-checker, `APPROVAL_BLOCKED`, PII reveal

**Regression gates on every task**, one heavy job at a time on the 8 GB Mac:
- `npm run lint:error`
- `npm test -- --ci --maxWorkers=2`
- `npm run test:mock`
- `npm run build`

**Accessibility:**
- RTL role and label queries, tab-order tests, a token contrast test
- `jest-axe` and Playwright + axe are pending approval (FQ-1)

**Visual and responsive:**
- themed Storybook
- `run`-skill screenshots at 375 / 768 / 1280, compared with `client/.stitch/designs/<ID>.png`

**End to end:**
- applicant journey and staff approval clicked through locally against `npm run dev:server`
- Playwright is pending approval (FQ-1)

## 12. Risks and dependencies

| Risk | Impact | Likelihood | Mitigation | Blocks? |
|---|---|---|---|---|
| Stitch Tailwind HTML does not map 1:1 to Chakra | M | High | Treat as visual spec; DS-00 sheet first; hand port | No |
| Stitch quota, latency or timeouts | M | Med | No retries (tool rule); poll `get_screen`; batch by flow | No |
| Tenant colours break look or contrast | H | Med | Computed `brand.fg` + fallback; tests on 6 presets + 2 tenants | No |
| `_app.tsx` platform mode regresses tenant 404 or lookup | H | Low–Med | Additive branch behind env; tests for tenant / unknown / platform | No |
| Global token or weight changes shift 100+ screens | M | High | Legacy keys kept; weight restore checked on R1-01 | No |
| Button or Card default change breaks callers | M | Med | Defaults unchanged until the R1 phase; per-caller review | No |
| No OpenAPI, so hand-written types drift | M | Med | Types from integration tests; FQ-5 (springdoc) | No |
| P9 not built, so "approved" is a dead end | M | Certain | Honest "setting up" state; no fake activation | No |
| PII leaks into Stitch or logs | H | Low | Fictional data only; `MaskedValue`; no payload logging | No |
| Parallel sessions commit in eloka | M | Med | Run `git log` / `git status` before each task | No |
| No E2E or axe tooling | M | Med | RTL + manual runs; FQ-1 | No |

## 13. Implementation phases

| Phase | Tasks ([TODO.md](./TODO.md)) | Exit gate |
|---|---|---|
| 0 Setup | F0-1, F0-2 | Docs merged; baseline green |
| 1 Design system in Stitch | ST-1 … ST-3, ST-4, ST-5, ST-6a, ST-8a | Owner approves DESIGN.md and the DS-00 sheet |
| 2 Code foundation | DS-1 … DS-5, CMP-1 … CMP-14 | Tokens + primitives with stories and tests; legacy parity test green |
| 3 Applicant | ST-6b, ST-8b, APP-A … APP-T | Full applicant journey clickable on `dev:server` |
| 4 Staff | ST-6c, ST-8c, STF-A … STF-H | Reviewer → approver flow clickable; maker-checker enforced in UI |
| 5 Reskin | ST-6d, ST-8d, R1-01 … R1-11 | Each group reviewed and regression-clean |
| 6 QA | QA-1 … QA-4 | a11y and responsive sweeps; full regression; docs synced |

## 14. Acceptance criteria (whole programme)

- Every screen in [UI_UX_PLAN.md](./UI_UX_PLAN.md) is implemented, matches its approved Stitch design within the review checklist, and shows its loading / empty / error / pending / success states.
- The applicant can finish all 13 steps, submit, see the status and timeline, handle a correction and resubmit, against the local server.
- Staff can sign in, claim, decide documents and verifications, request corrections, recommend, and approve, reject or hold. Maker-checker and `APPROVAL_BLOCKED` reasons are shown, not bypassed.
- Release 1 flows (login, home, menu, network, history, profile, business settings, agent onboarding, query center) behave the same as before, with the tenant's own colours.
- Lint, all Jest suites, the mock tests and the production build are green. New code has tests.
- New and changed screens meet WCAG 2.2 AA contrast and are fully keyboard-operable.
- `TODO.md` is accurate: no task is `[x]` without verification.

## 15. Working rules

- Commit on the checked-out branch; never push; no assistant attribution lines.
- Docs change in the same commit as the code they describe.
- Run `git log` and `git status` before each task, because other sessions may commit in eloka.
- One heavy job at a time: build, full Jest run, or dev server.
- **Keep the pipeline green.** Before every commit, run the CI checks for the folder you touched:
  - `client/**` → `make ci-client` (Node 20, steps one at a time).
  - `server/**` → `cd server && ./gradlew test --no-daemon --max-workers=1`.
- **Watch the Security workflow.** It runs on every push, docs included, and fails when Trivy finds a new fixable HIGH/CRITICAL CVE in an image. After the owner pushes, check `gh run list`. A new base-image CVE in a binary we never run goes into `server/.trivyignore` (or the client one) with a reason and an expiry date. Anything else gets fixed.
