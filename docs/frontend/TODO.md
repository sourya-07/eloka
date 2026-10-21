# ELOKA Frontend Transformation — TODO (single source of truth)

> Plan: [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) · Screens: [UI_UX_PLAN.md](./UI_UX_PLAN.md) · Files: [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md) · Questions: [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md)

**Statuses:** `[ ]` not started · `[~]` in progress · `[x]` done and verified · `[!]` blocked (the reason is written on the task)

**Counts** (update with every change): **total 78** · `[x]` 1 · `[~]` 0 · `[!]` 0 · `[ ]` 77

## How to use this file

1. Before starting, read the plan and architecture docs. Then run `git log` and `git status` to check for parallel sessions.
2. Pick a task whose dependencies are all `[x]`. Read its acceptance criteria. Change only what that task covers.
3. Run the checks listed under its **Verify**. Mark it `[x]` only once the work is both built and verified.
4. **Partial work** gets `[~]`. **Blocked work** gets `[!]` with the reason. **New work you discover** gets a new task with an ID.
5. If an interface or requirement changes, update the tasks that depend on it.
6. If the build differs from the plan, update the docs.

**Rules that apply to every task:**
- A generated Stitch screen is a spec, not a feature.
- A component counts only once it works inside the app.
- A visual review does not replace functional and regression tests.

**Regression gate** (for every code task; run one at a time on the 8 GB Mac):

```
cd eloka/client && npm run lint:error && npm test -- --ci && npm run test:mock && npm run build
```

Priority: **P0** blocks the release · **P1** needed for quality · **P2** later. Effort: **S** < ½ day · **M** ½–2 days · **L** 2–5 days · **XL** > 5 days.

---

## F0 — Setup

### [x] F0-1 Write the planning documents
- **P0 · S · deps:** none
- **Deliverable:**
  - `docs/frontend/{IMPLEMENTATION_PLAN,TODO,UI_UX_PLAN,FRONTEND_ARCHITECTURE,OPEN_QUESTIONS}.md`
  - a link in `docs/README.md`
  - a status line in the root `CLAUDE.md`
  - a cross-link at OQ-P2 in `docs/onboarding/OPEN_QUESTIONS.md`
- **Accept:** all five docs exist; links resolve; no application code changed.
- **Verify:** `git diff --stat` shows docs only; relative links checked.

### [ ] F0-2 Re-check the test baseline
- **P0 · S · deps:** F0-1
- **Deliverable:** a baseline note added under this task: suite count, results, build time.
- **Accept:** lint, Jest, mock tests and build are all green before any code change, or every failure is recorded as a new task.
- **Verify:** the regression gate, run sequentially.

---

## ST — Stitch design (no app code)

### [ ] ST-1 Write `client/.stitch/DESIGN.md`
- **P0 · M · deps:** F0-1
- **Deliverable:** DESIGN.md following the `design-md` structure (atmosphere, palette and roles, typography, components, layout), the token values from [UI_UX_PLAN.md](./UI_UX_PLAN.md) §2, and the anti-patterns from `taste-design`.
- **Accept:**
  - Every colour has a name, a hex value and a role.
  - The platform brand matches decision D3.
  - The rules for the tenant `brand.*` slot are written down.
  - No real data appears in it.
- **Verify:** compare it against `client/styles/themes.tsx` and UI_UX_PLAN §2.

### [ ] ST-2 Owner approval of the design system (skill checkpoint)
- **P0 · S · deps:** ST-1
- **Deliverable:** a summary to the owner (display name, key colours, fonts, roundness) and their explicit OK.
- **Accept:** written approval exists.
- **Verify:** the approval is noted here with its date.

### [ ] ST-3 Create the Stitch project and design system
- **P0 · S · deps:** ST-2
- **Deliverable:**
  1. `create_project` "ELOKA Platform"
  2. `upload_design_md`, or the upload script if the file is over 5 KB
  3. `create_design_system_from_design_md`
  4. `list_design_systems`
  5. `client/.stitch/metadata.json` holding the project id and asset id
- **Accept:** the design system is visible in Stitch; the ids are recorded.
- **Verify:** `get_project` shows the design system instance.

### [ ] ST-4 Write `client/.stitch/SITE.md`
- **P1 · S · deps:** ST-3
- **Deliverable:** SITE.md following the `site-md` structure. The sitemap is the screen IDs; the roadmap follows this file's order.
- **Accept:** all sections present; every APP, STF and R1 ID is listed.
- **Verify:** cross-check against UI_UX_PLAN §4–6.

### [ ] ST-5 Write the screen prompts
- **P0 · M · deps:** ST-3
- **Deliverable:** `client/.stitch/prompts/<ID>.md` for DS-00, APP-01…21 and STF-01…11, written with `enhance-prompt` in the `generate-design` template.
- **Accept:**
  - Prompts describe only layout, content, states and interactions. No hex values, fonts or theme words.
  - The data is fictional.
  - Each prompt lists its fields and validation hints from UI_UX_PLAN.
- **Verify:** grep the prompts for `#[0-9a-fA-F]{3,6}` and font names; expect none.

### [ ] ST-6a Generate the DS-00 component sheet
- **P0 · M · deps:** ST-5
- **Deliverable:**
  - DS-00 generated with `generate_screen_from_text` (DESKTOP), using the design system id
  - `designs/DS-00.{html,png}` saved through `download_file`
- **Accept:** the sheet shows buttons (all variants and states), inputs with label, help and error text, select, OTP, badges in every status tone, cards, table, dialog, stepper, empty, error and skeleton.
- **Verify:** look at the screenshot and record the Stitch output text and suggestions here.

### [ ] ST-8a Owner review: DS-00
- **P0 · S · deps:** ST-6a
- **Deliverable:** the review checklist (UI_UX_PLAN §7.3) filled in, plus fixes made with `edit_screens`.
- **Accept:** the owner approves DS-00.
- **Verify:** the approval is noted here with its date.

### [ ] ST-6b Generate the applicant screens
- **P0 · L · deps:** ST-8a
- **Deliverable:** APP-01…21 at MOBILE, plus DESKTOP for APP-02, APP-03, APP-07, APP-11, APP-13 and APP-15. Saved to `designs/`.
- **Accept:** every screen exists, and every state named in UI_UX_PLAN is shown, either as a separate screen or as a variant.
- **Verify:** screenshots viewed; `metadata.json` updated.

### [ ] ST-6c Generate the staff screens
- **P0 · L · deps:** ST-8a
- **Deliverable:** STF-01…11 at DESKTOP. Saved to `designs/`.
- **Accept:** as ST-6b.
- **Verify:** as ST-6b.

### [ ] ST-7 Refine the designs
- **P1 · M · deps:** ST-6b, ST-6c
- **Deliverable:** fixes with `edit_screens`; `generate_variants` (REFINE) only where the layout is in doubt.
- **Accept:** every checklist failure from ST-8b and ST-8c is resolved.
- **Verify:** fresh downloads replace the old files.

### [ ] ST-8b Owner review: applicant screens
- **P0 · S · deps:** ST-6b
- **Deliverable:** the checklist for every APP screen.
- **Accept:** the owner approves.
- **Verify:** approval date noted.

### [ ] ST-8c Owner review: staff screens
- **P0 · S · deps:** ST-6c
- **Deliverable:** the checklist for every STF screen.
- **Accept:** the owner approves.
- **Verify:** approval date noted.

### [ ] ST-6d Generate the reskin screens, one group at a time
- **P2 · L · deps:** ST-8a, CMP-12
- **Deliverable:** prompts and designs for R1-01…11. Optionally a `code-to-design` baseline taken from `dev:mock`, which needs FQ-1 approved for puppeteer.
- **Accept:** one approved design per group before that group is coded.
- **Verify:** screenshots viewed.

### [ ] ST-8d Owner review: each reskin group
- **P1 · S · deps:** ST-6d
- **Deliverable:** the checklist for each group.
- **Accept:** each group approved.
- **Verify:** approval dates noted.

---

## DS — Tokens and theme (code foundation)

### [ ] DS-1 Semantic tokens, status tokens, weights and contrast
- **P0 · M · deps:** ST-8a
- **Deliverable:** `client/styles/themes.tsx` and `styles/theme_system/theme/foundations/*`:
  - new `bg.inverse`, `text.inverse`, `text.link`, `focus.ring`, `overlay` and `status.*` tokens
  - `fontWeights` restored (400 / 500 / 600 / 700)
  - `text.tertiary` darkened to `#767676`
  - Input, Select, Card, Modal and Alert component themes
- **Accept:**
  - The legacy colour keys are unchanged.
  - Every text token passes 4.5:1 on `bg.surface` and `bg.canvas`.
- **Verify:** a new contrast unit test; the regression gate; a look at login, home and network on `dev:mock`.

### [ ] DS-2 `helpers/buildOrgTheme.ts` and the `_app.tsx` refactor
- **P0 · M · deps:** DS-1
- **Deliverable:**
  - a pure `buildOrgTheme(org, base)` built from the code in `_app.tsx` lines 84–151
  - new `brand.{solid,solidHover,fg,subtle,emphasis}` tokens, with `fg` picked by contrast and a fallback to `primary.dark`
  - `_app.tsx` calls the new helper
- **Accept:** for all 6 presets in `constants/colorThemes.js`, plus no theme and both `navstyle` values, the legacy keys (`primary`, `accent`, `navbar`, `sidebar`, `status`, `logo`) deep-equal the old output, and `brand.fg` passes 4.5:1.
- **Verify:** a new parity test; the regression gate; a manual check of 2 presets on `dev:mock`.

### [ ] DS-3 Load Inter with `next/font`
- **P1 · S · deps:** DS-1
- **Deliverable:** `_app.tsx` uses `next/font/google` Inter with the variable font and `display: swap`, and the theme font is set to that variable.
- **Accept:** Inter is actually loaded (seen in DevTools); no layout shift on login; the Docker build still works.
- **Verify:** the regression gate; `npm run build`; a look at the result.

### [ ] DS-4 Theme Storybook
- **P1 · S · deps:** DS-1
- **Deliverable:** `.storybook/preview.js` wraps stories in `ChakraProvider theme={light}`.
- **Accept:** stories render with the app tokens.
- **Verify:** `npm run sb` and look at 3 stories.

### [ ] DS-5 Platform-host mode
- **P0 · M · deps:** DS-2
- **Deliverable:**
  - In `_app.tsx`, a host listed in the server env `WLC_PLATFORM_HOSTS` skips the 677 lookup and the 1829 404 and uses the platform theme (D3).
  - In development, path prefixes `/partner` and `/staff` act the same way.
  - `.env.example` documents the variable.
- **Accept:**
  - A tenant host is unchanged.
  - An unknown host still returns 404.
  - A platform host renders with no org.
- **Verify:** unit tests on the host classifier; manual check on `dev:mock` for `/`, `/partner/onboarding` and an unknown host.

---

## CMP — Shared components

Every new component lives in its own folder under `client/components/<Name>/` with `index`, `.tsx`, `.stories.tsx` and a test in `client/__tests__/components/<Name>/`.

### [ ] CMP-1 StatusBadge
- **P0 · S · deps:** DS-1
- **Deliverable:** maps every application, step, document and verification status to a tone (UI_UX_PLAN §3.2), shows a readable label, and supports a dot or icon.
- **Accept:** every status value maps to a tone, with a fallback for unknown values; the colour is never the only cue.
- **Verify:** unit tests over all enum values; story.

### [ ] CMP-2 EmptyState
- **P0 · S · deps:** DS-1
- **Deliverable:** icon, title, description and an optional action.
- **Accept:** a heading level can be set; the action is a real button.
- **Verify:** test and story.

### [ ] CMP-3 ErrorState
- **P0 · S · deps:** DS-1
- **Deliverable:** title, message, optional code, retry. Uses `role="alert"` when it is inline.
- **Accept:** it never shows raw stack traces or tokens.
- **Verify:** test and story.

### [ ] CMP-4 FormField
- **P0 · M · deps:** DS-1
- **Deliverable:** label above the control, optional help text, an error below linked with `aria-describedby`, a required marker, and a react-hook-form-friendly API.
- **Accept:** RTL `getByLabelText` works; the error is announced; it works with Input, Select and Textarea.
- **Verify:** test and story.

### [ ] CMP-5 PageHeader
- **P0 · S · deps:** DS-1
- **Deliverable:** title, subtitle, status slot, actions slot and an optional back link.
- **Accept:** a single `h1`; it wraps cleanly at 375 px.
- **Verify:** test and story.

### [ ] CMP-6 SectionCard
- **P0 · S · deps:** DS-1
- **Deliverable:** a card with header, body and footer slots, and an e1 elevation.
- **Accept:** no fixed height.
- **Verify:** test and story.

### [ ] CMP-7 KeyValueList
- **P0 · S · deps:** DS-1
- **Deliverable:** a label / value list built on `dl`, `dt` and `dd`, with tabular numbers.
- **Accept:** semantic markup; long values wrap.
- **Verify:** test and story.

### [ ] CMP-8 Timeline
- **P0 · S · deps:** CMP-1
- **Deliverable:** an ordered list of events with time, title, status and an optional note.
- **Accept:** `ol` semantics; times use the existing date helpers (IST).
- **Verify:** test and story.

### [ ] CMP-9 DocumentTile
- **P0 · M · deps:** CMP-1
- **Deliverable:** document type, file name, size, status, reviewer note, and replace, view and remove actions.
- **Accept:** it handles PENDING_REVIEW, VERIFIED, REJECTED and SUPERSEDED; every action has a label.
- **Verify:** test and story.

### [ ] CMP-10 ConfirmDialog
- **P0 · S · deps:** DS-1
- **Deliverable:** a dialog with title, body, an optional required reason field, a danger or normal tone, and a loading state.
- **Accept:** focus is trapped and returned to the trigger; Esc closes it; the confirm button is disabled until a required reason is entered.
- **Verify:** test and story.

### [ ] CMP-11 MaskedValue
- **P0 · S · deps:** DS-1
- **Deliverable:** shows a masked value as given (it never unmasks on the client), with an optional "Reveal" callback slot and an optional copy action.
- **Accept:** the full value never appears in the DOM unless the parent passes in a revealed value.
- **Verify:** test and story.

### [ ] CMP-12 Button: variants and types
- **P0 · M · deps:** DS-2
- **Deliverable:** the TS union gains `cta`, `secondary`, `danger` and `brand`. The new `brand` variant uses the `brand.*` tokens.
- **Accept:** **the default variant is unchanged**; all 120 call sites render as before; the new variants pass contrast.
- **Verify:** the regression gate; stories for all variants.

### [ ] CMP-13 Accessibility fixes: Switch, IcoButton, Icon, OtpInput
- **P0 · M · deps:** DS-1
- **Deliverable:**
  - `Switch`: `role="switch"`, `aria-checked`, works with Space and Enter.
  - `IcoButton`: an `aria-label` prop that TypeScript requires; existing callers are updated.
  - `Icon`: `aria-hidden` unless `title` is given.
  - `OtpInput`: an `aria-label` on each digit ("Digit 1 of 6").
- **Accept:** behaviour and looks unchanged; the existing tests stay green.
- **Verify:** new keyboard and role tests; the regression gate.

### [ ] CMP-14 Card variant
- **P1 · S · deps:** DS-1
- **Deliverable:** a `variant` prop (`legacy` default, `surface`); the `surface` variant has no fixed height.
- **Accept:** the legacy default looks the same.
- **Verify:** test and story.

---

## APP — Applicant (Local Admin) flow: `features/la-onboarding/`

### [ ] APP-A API client, types and session
- **P0 · L · deps:** DS-5
- **Deliverable:**
  - `api/client.ts`:
    - bearer token
    - problem+json mapped to `ApiProblem`
    - stores the ETag from the last snapshot and sends `If-Match` automatically
    - a 412 hands back `current`
    - an `Idempotency-Key` UUID per user action
    - an optional `X-Reauth-Token`
    - a single-flight refresh on 401 through A3
  - `api/types.ts`: the snapshot, steps, enums and error codes, taken from P7 and the integration tests.
  - `context/ApplicantSessionContext.tsx`: the `la_*` sessionStorage keys.
- **Accept:** never touches `UserContext` keys; unit tests cover every header and error path.
- **Verify:** Jest with mocked `fetch`; the regression gate.

### [ ] APP-B LayoutPlatform, routes and guard entry
- **P0 · S · deps:** APP-A, CMP-5
- **Deliverable:**
  - `layout-components/LayoutPlatform/` (header with the ELOKA wordmark, help link, sign-out)
  - `pages/partner/onboarding/{index,[step],status}.tsx`
  - `constants/validRoutes.js` gains `publicSections += "partner", "staff"`
- **Accept:** `RouteProtecter` does not redirect `/partner/*`; Release 1 routes behave as before.
- **Verify:** a `RouteProtecter` test for the new sections; the existing guard tests stay green.

### [ ] APP-C APP-01 / APP-02: entry and mobile + OTP
- **P0 · M · deps:** APP-B, CMP-4, CMP-13
- **Deliverable:** entry screen; mobile → A1 → OTP → A2; resend timer; all error codes handled; resumes an existing application.
- **Accept:** the UI_UX_PLAN acceptance criteria for APP-01 and APP-02.
- **Verify:** feature tests; manual run on `dev:server` (OTP `123456`).

### [ ] APP-D APP-03: application hub
- **P0 · M · deps:** APP-C, CMP-1, CMP-6
- **Deliverable:** progress from `steps[]`, `next_recommended_step`, `available_actions`, expiry notice, correction banner.
- **Accept:** as UI_UX_PLAN APP-03.
- **Verify:** feature tests using the snapshot fixtures.

### [ ] APP-E APP-04 Consent and APP-05 Program
- **P0 · M · deps:** APP-D
- **Deliverable:** consent text and versions (`CONSENT_VERSION_STALE` triggers a reload); entity type picker (4 types).
- **Accept:** as UI_UX_PLAN.
- **Verify:** feature tests; manual run.

### [ ] APP-F APP-06 Business PAN
- **P0 · M · deps:** APP-E
- **Deliverable:** PAN input checked against the regex and the entity's 4th character; A12 with 200, 202 (pending poll), 422 and 409 outcomes.
- **Accept:** as UI_UX_PLAN.
- **Verify:** feature tests (fakes: `…0000` fails, `…9999` pending); manual run.

### [ ] APP-G APP-07 Business details and signature upload
- **P0 · L · deps:** APP-F, CMP-9
- **Deliverable:** every field in UI_UX_PLAN with the client-side regexes, state list from A20, pincode, signature upload through A21, name-mismatch notice.
- **Accept:** as UI_UX_PLAN.
- **Verify:** feature tests; manual run.

### [ ] APP-H APP-08 Email verification
- **P0 · S · deps:** APP-D
- **Deliverable:** A13 sends the OTP; A14 confirms it; resend.
- **Accept:** as UI_UX_PLAN.
- **Verify:** feature tests; manual run.

### [ ] APP-I APP-09 Signatory KYC (DigiLocker)
- **P0 · M · deps:** APP-G
- **Deliverable:** A15 → redirect → return → finalise and poll A16; pending, failed and mismatch states.
- **Accept:** the Aadhaar number is never typed or stored on the client; only the masked value is shown.
- **Verify:** feature tests; manual run using the local fake.

### [ ] APP-J APP-10 Live photo
- **P0 · M · deps:** APP-I
- **Deliverable:** reuse `Camera`; upload as `LIVE_PHOTO`; optional location capture; submit.
- **Accept:** clear handling when camera permission is denied.
- **Verify:** feature tests; manual run.

### [ ] APP-K APP-11 Documents checklist
- **P0 · L · deps:** APP-G, CMP-9
- **Deliverable:** required documents per entity type; upload, replace and remove (A21–A25); JPEG, PNG or PDF up to 10 MB; completion check through A8.
- **Accept:** `PREREQUISITE_INCOMPLETE` lists the missing documents by name.
- **Verify:** feature tests; manual run.

### [ ] APP-L APP-12 Bank account
- **P0 · M · deps:** APP-G, APP-R
- **Deliverable:** bank list (A19), IFSC, account, bank proof upload, re-auth, then A17 and A18; failure and name-mismatch states.
- **Accept:** the account number is masked after submit.
- **Verify:** feature tests (fakes: `…0000` fails, `…1111` mismatch); manual run.

### [ ] APP-M APP-13 White-label setup
- **P0 · L · deps:** APP-E
- **Deliverable:** app name, sub-domain with debounced A26 availability and suggestions, logo upload, theme (reusing `ColorPickerWidget` and the presets), `navstyle`, live preview (reusing `AppPreview`).
- **Accept:** `SUBDOMAIN_TAKEN` on submit is handled; the preview uses `buildOrgTheme`.
- **Verify:** feature tests; manual run.

### [ ] APP-N APP-14 Review summary and declaration
- **P0 · M · deps:** APP-F … APP-M
- **Deliverable:** read-only summary per step with "Edit" links, declaration checkbox and versions, A8.
- **Accept:** `PREREQUISITE_INCOMPLETE` lists the missing steps.
- **Verify:** feature tests; manual run.

### [ ] APP-O APP-15 Agreement and e-sign
- **P0 · L · deps:** APP-N, APP-R
- **Deliverable:** A27 creates and previews the agreement (PDF link); re-auth; A28 opens `signing_url`; the return page finalises through A29; handles `already_signed` and `SUPERSEDED`.
- **Accept:** the client never marks anything as signed; it trusts only A29.
- **Verify:** feature tests; manual run using the fake e-sign.

### [ ] APP-P Auto-submit and A10 resubmit wiring
- **P0 · S · deps:** APP-O
- **Deliverable:** after signing, show the submitted state; A10 handles resubmission after corrections.
- **Accept:** as UI_UX_PLAN APP-16 and APP-17.
- **Verify:** feature tests.

### [ ] APP-Q APP-16, APP-17, APP-18: status, corrections and outcomes
- **P0 · M · deps:** APP-D, CMP-8
- **Deliverable:** status page with the timeline (A30); correction page (reopened steps, reviewer messages, due date); approved ("we are setting up your platform"), rejected, withdrawn and expired screens.
- **Accept:** no fake activation UI (P9 is not built).
- **Verify:** feature tests per status.

### [ ] APP-R APP-19 Re-auth and APP-20 Withdraw
- **P0 · M · deps:** APP-C, CMP-10
- **Deliverable:** `useReauth` (OTP dialog that returns a single-use token); withdraw dialog with reason and re-auth, calling A11.
- **Accept:** the token is never stored after use; `REAUTH_REQUIRED` triggers the dialog.
- **Verify:** feature tests.

### [ ] APP-S APP-21 System states
- **P1 · S · deps:** APP-A, CMP-3
- **Deliverable:** stale tab 412 (reload the snapshot, then show a toast), `STEP_BLOCKED` 423, session expired, `PROVIDER_NOT_CONFIGURED`.
- **Accept:** as UI_UX_PLAN APP-21.
- **Verify:** feature tests.

### [ ] APP-T Applicant end-to-end run (local)
- **P0 · M · deps:** APP-C … APP-S
- **Deliverable:** a scripted manual run against `npm run dev:server`, with screenshots at 375 and 1280 px saved to the run notes (or Playwright if FQ-1 is approved).
- **Accept:** the whole journey reaches SUBMITTED; a stale-tab 412 recovers.
- **Verify:** run notes linked here.

---

## STF — Staff console: `features/staff-console/`

### [ ] STF-A Client, session and sign-in (STF-01)
- **P0 · L · deps:** APP-A (shared client pieces), DS-5
- **Deliverable:** the staff API client (`STAFF` session); `StaffSessionContext` loaded from `/me`; Google sign-in through `@react-oauth/google`; local OTP sign-in in development; `LayoutStaff`; `pages/staff/index.tsx`; CSP origins for Google.
- **Accept:** `PROVIDER_NOT_CONFIGURED` and `STAFF_NOT_ALLOWED` are shown clearly; actions are hidden when the permission is missing.
- **Verify:** feature tests; manual run with `9000000006` and OTP `123456`.

### [ ] STF-B STF-02 Review queue
- **P0 · M · deps:** STF-A, CMP-1, CMP-2
- **Deliverable:** S1 with status filter, "assigned to me", search, cursor pagination; table on desktop, cards below 860 px (reusing `components/Table`).
- **Accept:** as UI_UX_PLAN STF-02.
- **Verify:** feature tests.

### [ ] STF-C STF-03 Case view
- **P0 · L · deps:** STF-B, CMP-7, CMP-8, CMP-9, CMP-11
- **Deliverable:** S2 sections (overview, flags, approval blockers, masked business profile, verifications, documents, history); claim (S3) and release (S4).
- **Accept:** `NOT_ASSIGNED` and a stale ETag are handled.
- **Verify:** feature tests.

### [ ] STF-D STF-04 / STF-05 Document and verification decisions
- **P0 · M · deps:** STF-C, CMP-10
- **Deliverable:** document viewer (S-download), verify and reject with reason (S5, S6), verification accept and reject (S7).
- **Accept:** as UI_UX_PLAN.
- **Verify:** feature tests.

### [ ] STF-E STF-06 / STF-07 Corrections and recommendation
- **P0 · M · deps:** STF-C, CMP-10
- **Deliverable:** corrections dialog (step picker, reason code, message, due days 1–90, default 15) through S8; recommend approve or reject through S9.
- **Accept:** as UI_UX_PLAN.
- **Verify:** feature tests.

### [ ] STF-F STF-08 Approve, reject, hold
- **P0 · M · deps:** STF-E
- **Deliverable:** S10, S11 and S12 (place and release hold); `APPROVAL_BLOCKED` reasons listed; `MAKER_CHECKER_VIOLATION` explained.
- **Accept:** the approve button is disabled, with the reasons shown, while there are blockers.
- **Verify:** feature tests.

### [ ] STF-G STF-09 PII reveal and STF-10 Audit trail
- **P1 · M · deps:** STF-C, CMP-11
- **Deliverable:** reveal dialog (field and required purpose) through S13; the revealed value lives only in memory and clears when the dialog closes; audit list from S21.
- **Accept:** the value is never logged or cached.
- **Verify:** feature tests.

### [ ] STF-H Staff end-to-end run (local)
- **P0 · M · deps:** STF-A … STF-G, APP-T
- **Deliverable:**
  1. reviewer `9000000006` claims, verifies documents and recommends
  2. approver `9000000007` approves
  3. a correction round-trip works with the applicant UI
- **Accept:** maker-checker is enforced; the applicant sees APP-17 and can resubmit.
- **Verify:** run notes linked here.

---

## R1 — Reskin the existing app (visual only; each group needs its ST-8d approval)

Every R1 task follows the same rules:
- Change only tokens and presentation. No change to hooks, contexts, interaction ids or payloads.
- Replace hex values and `gray.*` with semantic tokens.
- Replace ad hoc empty and error UI with CMP-2 and CMP-3.

**Accept:** behaviour identical; existing tests green; tenant colours still applied; WCAG AA.
**Verify:** the regression gate, plus a manual run on `dev:mock` with 2 presets.

| ID | Group | Priority | Effort | Deps |
|---|---|---|---|---|
| [ ] R1-01 | App shell: NavBar, SideBar, BottomAppBar, PageTitle; decide the Button default (FQ-4); `Text` user-select | P1 | L | ST-8d, CMP-12, CMP-14 |
| [ ] R1-02 | Login and OTP (`page-components/LoginPanel/**`), removing the "Secured by DemoPortal" literal per FQ-8 | P1 | M | R1-01 |
| [ ] R1-03 | Agent home (`page-components/Home/**`) | P1 | M | R1-01 |
| [ ] R1-04 | Admin dashboards (`page-components/Admin/Dashboard/**`), chart colours from tokens | P2 | L | R1-01 |
| [ ] R1-05 | Network (`page-components/Admin/Network/**`, profile sub-pages) | P1 | L | R1-01 |
| [ ] R1-06 | History and statements (`page-components/History/**`, `Admin/TransactionHistory`, `AccountStatement`, `DetailedStatement`) | P1 | L | R1-01 |
| [ ] R1-07 | Profile (`page-components/Profile/**`, `Admin/ProfilePanel/**`, `Admin/Update*`) | P2 | M | R1-01 |
| [ ] R1-08 | Business settings and pricing form shell (`Admin/PricingConfig`, `Admin/PricingCommission` layout only) | P2 | L | R1-01 |
| [ ] R1-09 | Agent onboarding (`features/onboarding/**` presentation only) | P1 | L | R1-01 |
| [ ] R1-10 | Query center and raise issue | P2 | M | R1-01 |
| [ ] R1-11 | Shared 404, error boundary, empty states | P1 | S | R1-01, CMP-2, CMP-3 |

---

## QA — Final checks

### [ ] QA-1 Accessibility sweep
- **P0 · M · deps:** APP-T, STF-H
- **Deliverable:** keyboard-only pass and contrast check on every new screen; findings filed as tasks.
- **Accept:** WCAG 2.2 AA on new and changed screens.
- **Verify:** checklist in UI_UX_PLAN §9 (`jest-axe` if FQ-1 is approved).

### [ ] QA-2 Responsive sweep
- **P0 · M · deps:** APP-T, STF-H
- **Deliverable:** screenshots at 375, 768 and 1280 px of every new screen.
- **Accept:** no horizontal scroll; touch targets ≥ 44 px on mobile.
- **Verify:** screenshots stored with the run notes.

### [ ] QA-3 Full regression
- **P0 · S · deps:** every task due for the release
- **Deliverable:** the regression gate plus the tenant regression from IMPLEMENTATION_PLAN §14.
- **Accept:** everything green.
- **Verify:** command output recorded.

### [ ] QA-4 Docs sync
- **P0 · S · deps:** QA-3
- **Deliverable:** these five docs, `client/CLAUDE.md` (new folders and conventions), the root `CLAUDE.md` status, and `docs/LOCAL_DEVELOPMENT.md` ("Try the applicant UI" / "Try the staff console").
- **Accept:** the docs match the code.
- **Verify:** review.

---

## Count check

| Group | Tasks |
|---|---|
| F0 | 2 |
| ST | 14 (ST-1, ST-2, ST-3, ST-4, ST-5, ST-6a, ST-6b, ST-6c, ST-6d, ST-7, ST-8a, ST-8b, ST-8c, ST-8d) |
| DS | 5 |
| CMP | 14 |
| APP | 20 (APP-A … APP-T) |
| STF | 8 (STF-A … STF-H) |
| R1 | 11 |
| QA | 4 |
| **Total** | **78** |
