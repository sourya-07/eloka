# ELOKA Frontend Transformation — UI/UX Plan

> Status: **APPROVED 2026-10-21** (design direction). Each screen batch still needs owner approval at its review gate (ST-8a…d in [TODO.md](./TODO.md)).
> Related: [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) · [FRONTEND_ARCHITECTURE.md](./FRONTEND_ARCHITECTURE.md) · [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md)
> Requirement sources: [P7_APPLICANT_API.md](../onboarding/P7_APPLICANT_API.md) (A-ids), [P8_STAFF_REVIEW.md](../onboarding/P8_STAFF_REVIEW.md) (S-ids), [ONBOARDING_STATE_MACHINE.md](../onboarding/ONBOARDING_STATE_MACHINE.md), server `StepData.java` / `StepHandlers.java` (validation).

## 1. Design principles

1. **Trust through calm.**
   - Warm-neutral surfaces and one decisive primary action per view.
   - No decorative noise: no glows, gradients or emojis.
2. **Say where the user is.**
   - Every onboarding screen shows the step name, its position and its status.
   - Every staff screen shows the case status, who has claimed it, and what blocks approval.
3. **Prevent errors before reporting them.**
   - Input masks and formats (uppercase PAN, 6-digit pincode, IFSC pattern).
   - Inline validation when the user leaves a field.
   - The server stays the authority: its `invalid_params` are mapped onto the matching fields.
4. **Recover, don't dead-end.**
   - Every error state names the next action: retry, re-auth, reload or contact support.
   - Stale data (412) reloads itself and tells the user.
5. **Plain words.**
   - Short sentences. Error codes appear only in a "Details" disclosure.
6. **Data honesty.**
   - No invented metrics.
   - Masked PII by default.
   - "Approved" never pretends the platform is live, because P9 is not built.
7. **Accessible by default.**
   - Labels above fields.
   - Errors linked with `aria-describedby`.
   - Visible focus.
   - Colour is never the only cue.
   - 44 px touch targets.
   - WCAG 2.2 AA.

## 2. Visual direction and design tokens

**Atmosphere:** "quiet authority".
- Density: balanced (5/10). Variance: low (3/10). Motion: restrained (3/10), only 150–200 ms opacity or transform.
- Platform hosts: neutral ink with a Connect Blue accent.
- Tenant app: the same neutrals, with the tenant's brand colour in the `brand.*` slot.

### 2.1 Colour tokens (Chakra semantic tokens in `client/src/styles/themes.tsx`)

| Token | Value | Role | Status |
|---|---|---|---|
| `bg.canvas` | `#FAFAF7` | Page background (warm paper) | exists |
| `bg.surface` | `#FFFFFF` | Cards, inputs, dialogs | exists |
| `bg.subtle` | `#F4F3EE` | Recessed areas, table header, sidebar (platform) | exists |
| `bg.inverse` | `#0A0A0A` | Inverse bars and toasts | new |
| `text.primary` | `#0A0A0A` | Body and headings | exists |
| `text.secondary` | `#5C5C57` | Labels, metadata (≥ 6.0:1 on all three surfaces). Was `#6B6B6B` | **changed** |
| `text.tertiary` | `#6F6F6A` | Hints, placeholders (≥ 4.55:1 on white, canvas and subtle). Was `#9A9A9A` (2.8:1) | **changed** |
| `text.inverse` | `#FFFFFF` | Text on `bg.inverse` | new |
| `text.link` | `#11299E` | Links on platform hosts; on tenants it maps to `brand.emphasis` | new |
| `border.default` | `#E5E5E0` | Hairlines (decorative) | exists |
| `border.strong` | `#D4D4CE` | Card and divider emphasis (decorative) | exists |
| `border.input` | `#85857F` | Input, select and checkbox boundaries (≥ 3.3:1, WCAG 1.4.11) | new |
| `focus.ring` | `#11299E` (platform) / `brand.emphasis` (tenant) | 2 px focus outline | new |
| `overlay` | `rgba(10,10,10,0.48)` | Modal scrim | new |
| `cta.bg` / `cta.bgHover` / `cta.fg` | `#0A0A0A` / `#1F1F1F` / `#FFFFFF` | Primary action (platform) | exists |
| `success.*` / `danger.*` / `warning.*` / `info.*` | existing `bg` / `fg` / `border` triads | Status fills | exists |
| `brand.solid` / `solidHover` / `fg` / `subtle` / `emphasis` | computed by `buildOrgTheme` | Tenant primary action and highlights | new |

**Legacy keys stay unchanged:** `primary.*`, `accent.*`, `navbar.*`, `sidebar.*`, `status.*`, `logo.*`. The EkoConnect widget, `manifest-proxy` and existing screens read them.

**How `brand.*` is computed:**
- `solid` = tenant `primary`.
- `fg` = white or `#0A0A0A`, whichever has the higher contrast against `solid`.
- If neither reaches 4.5:1, `solid` becomes `primary.dark`.
- `subtle` = `solid` at 10% over white.
- `emphasis` = whichever of `primary.dark` and `primary` has the better contrast on `bg.surface`.
- On platform hosts, `brand` maps to `cta` with the `#11299E` accent.

### 2.2 Typography

- **Font:** Inter, loaded with `next/font` (self-hosted at build time) with `system-ui` as the fallback.
- **Weights:** 400 body, 500 label, 600 title, 700 display. These are currently all 500 and get restored.
- **Numbers:** amounts, IDs and timestamps use `font-variant-numeric: tabular-nums`. Rupee amounts use the existing Indian-grouping helpers.

| Level | Size / line height | Weight | Use |
|---|---|---|---|
| display | 32 / 40 (24 / 32 on mobile) | 700 | Hub title, outcome screens |
| h1 | 24 / 32 | 600 | Page title |
| h2 | 20 / 28 | 600 | Section title |
| h3 | 16 / 24 | 600 | Card title |
| body | 16 / 24 (on mobile inputs, never under 16, so iOS does not zoom) | 400 | Body, inputs |
| body-sm | 14 / 20 | 400 | Table cells, help text |
| label | 12 / 16 | 500 | Field labels, overlines |

### 2.3 Spacing, radius and elevation

- **Spacing:** a 4 px base grid. Steps used: 4, 8, 12, 16, 24, 32, 48, 64.
  - Card padding: 24 on desktop, 16 on mobile.
  - Gaps between sections: 32 on desktop, 24 on mobile.
- **Radius:** sm 6 (chips), md 10 (inputs, buttons), lg 14 (cards), xl 18 (dialogs and sheets), full (status pills).
- **Elevation:**

| Level | Treatment | Use |
|---|---|---|
| e0 | flat | Canvas |
| e1 | 1 px `border.default` + `0 1px 2px rgba(10,10,10,0.04)` | Cards |
| e2 | `0 8px 24px rgba(10,10,10,0.08)` | Menus, popovers |
| e3 | `0 16px 48px rgba(10,10,10,0.12)` | Dialogs |

- **Breakpoints (unchanged):** base 0, sm 440, md 768, lg 1024, xl 1200, 2xl 1800.
- **Layout widths:**
  - Applicant content column: max 640 px on mobile and tablet. On ≥ lg, the step form (max 720) sits next to a 280 px progress rail.
  - Staff screens: fluid, max 1400 px.

## 3. Components and patterns

### 3.1 Inventory (DS-00 sheet)

| Component | Status | Notes |
|---|---|---|
| Button | extend | Variants `cta` (platform primary), `brand` (tenant primary), `secondary` (outline), `ghost`, `danger`, `link`. Sizes sm 32, md 40, lg 48 (mobile primary). Loading and disabled states. |
| FormField + Input / Select / Textarea | new + reuse | Label above, help text, error below, required marker. 48 px tall on mobile. |
| OtpInput | reuse + a11y | 6 digits; paste fills all; per-digit labels; resend timer next to it |
| StatusBadge | new | Pill shape. Tone from §3.2, a dot for colour-blind users, text always present. |
| Stepper / progress rail | reuse `src/components/ui/Stepper` | Vertical rail on desktop, compact "Step 4 of 13" bar on mobile |
| SectionCard | new | Header, body and footer slots |
| KeyValueList | new | Read-only summaries and the staff case view |
| DocumentTile | new | Type, file name, size, status, reviewer note, actions |
| Dropzone / Camera | reuse | Uploads, live photo |
| Table | reuse `src/components/ui/Table` | Staff queue; cards below 860 px |
| Dialog / ConfirmDialog | reuse `Modal` + new `ConfirmDialog` | Reason field when required |
| Toast | Chakra `useToast` | Only for asynchronous results; never the only error channel |
| Timeline | new | Applicant status page, staff history and audit |
| EmptyState / ErrorState | new | Explain what to do next |
| Skeleton | Chakra | Shaped like the real layout |
| MaskedValue | new | PAN `XXXXX1234X`, account `XXXXXX1234`, mobile `XXXXXX3210` |
| PageHeader | new | Title, subtitle, status, actions |

### 3.2 Status tone mapping (single source, `StatusBadge`)

| Tone | Values |
|---|---|
| neutral | `NOT_STARTED`, `SKIPPED`, `NOT_APPLICABLE`, `SUPERSEDED`, `RELEASED` |
| info | `IN_PROGRESS`, `SUBMITTED`, `UNDER_REVIEW`, `PENDING_EXTERNAL`, `PENDING_REVIEW`, `READY`, `SIGNING`, `RESERVED`, `PROVISIONING`, `GO_LIVE_PENDING` |
| warning | `CORRECTION_REQUIRED`, `REOPENED`, `ON_HOLD`, `MANUAL_REVIEW` |
| success | `COMPLETED`, `APPROVED`, `VERIFIED`, `SUCCEEDED`, `SIGNED`, `ACTIVE` |
| danger | `FAILED`, `BLOCKED`, `REJECTED`, `EXPIRED`, `WITHDRAWN`, `PROVISIONING_FAILED` |
| locked | `LOCKED` (neutral fill, lock icon) |

Applicant-facing labels use plain words, for example "Waiting for review" for `SUBMITTED`. Staff-facing labels use the enum in title case.

### 3.3 Interaction patterns

**Forms**
- Validate when the user leaves a field. Submitting focuses the first invalid field.
- Server `invalid_params` are mapped to the matching fields. Unknown keys go to a form-level error.

**Saving**
- Step forms autosave drafts through A7 `PUT …/steps/{key}`, debounced at 2 s. The draft can only hold the fields A7 allows, so PAN, account and mobile are never drafted.
- A small "Saved" or "Saving…" indicator sits near the title.

**Stale data (412)**
- Reload from `current`, keep the user's unsaved input where the step allows it, and show the toast "This application changed in another tab. We've loaded the latest version."

**Re-auth**
- The dialog opens when `REAUTH_REQUIRED` comes back, or before bank, e-sign and withdraw.
- After a successful OTP the action resumes automatically.

**Async work**
- `202` and `PENDING_EXTERNAL` show a pending card with "We'll keep checking". Polling backs off 3 → 6 → 12 s, capped at 2 minutes, then shows a manual "Check again".

**Destructive actions**
- Withdraw, reject and remove-document always go through `ConfirmDialog`.

## 4. Applicant screens (role: Local Admin applicant; host `partners.<root>`; mobile-first)

**Shared across every APP screen:**
- **Entry:** `/partner/onboarding/*`. The `LayoutPlatform` header shows the ELOKA wordmark, a Help link and Sign out.
- **States:**
  - loading: skeleton
  - empty: none (always populated)
  - error: `ErrorState` / `ApiProblem`
  - pending: async card
  - success: step completed, then go to the next step
- **Responsive:** single column below lg. A progress rail plus a 720 px form column at lg and up.
- **Every write sends** `If-Match` and `Idempotency-Key`.
- **APIs** are named with their A-ids.

### APP-01 Entry
- **Purpose:** explain what onboarding needs, then start.
- **Content:**
  - one-sentence value line
  - checklist of what to keep ready: business PAN, signatory's Aadhaar-linked mobile (DigiLocker), business documents, bank proof, logo (optional)
  - expected time
- **Actions:** primary "Start application" (→ APP-02); secondary "Continue an application" (→ APP-02).
- **Validation:** none.
- **Accept:** no marketing stats; works at 375 px; one primary CTA.

### APP-02 Mobile and OTP (A1, A2)
- **Fields:**
  - mobile: 10 digits, `^[6-9]\d{9}$`, `inputmode=numeric`
  - OTP: 6 digits, `otp_length` from A1
- **Transitions:**
  - A1 `202` → OTP view, with the resend timer from `resend_after`
  - A2 `200`, new application → APP-04
  - A2 `200` with `resumed: true` → APP-03
- **Errors:**

| Code | Message / behaviour |
|---|---|
| `OTP_INVALID` | "Incorrect code — N attempts left" (`attempts_left`) |
| `OTP_EXPIRED` | "Code expired" + resend |
| `OTP_LOCKED` 429 | "Too many attempts, try again later", with a timer if given |
| `DUPLICATE_APPLICATION` 409 | "This mobile already has an active ELOKA account", with a sign-in link to their tenant if known; otherwise a support message |

- **Session:** tokens stored in `la_*` keys.
- **Accept:** paste fills the OTP; resend is disabled until the timer ends; never says whether a mobile is known (A1 answers the same either way).

### APP-01B Choose how to apply (owner request 2026-10-21)
- **Purpose:** after the mobile OTP, the applicant picks one of two paths.
- **Option A, "Apply with DigiLocker" (faster):** DigiLocker supplies the signatory's identity (Aadhaar last 4 digits only), the PAN details and any documents it holds. The applicant then adds only the bank account, the platform setup, and the declaration plus agreement. The card shows "about 8 minutes · 6 steps".
- **Option B, "Apply step by step":** the full 13-step flow (APP-04…APP-15).
- **Actions:** "Continue"; "What is DigiLocker?" (help).
- **Rules:** the applicant can switch to step by step at any time, and anything fetched from DigiLocker is kept.
- **Note:** the backend has no DigiLocker-only path today (see [OPEN_QUESTIONS.md](./OPEN_QUESTIONS.md) FQ-16), so this is a design only.

### APP-22 DigiLocker quick path (owner request 2026-10-21)
A 6-step flow:
1. Mobile verification
2. DigiLocker
3. Check details
4. Bank account
5. Platform setup
6. Declaration and agreement

Frames:
- **Connect DigiLocker:** shows what will be fetched, a consent checkbox, then a redirect. Also a pending state, and an error state with retry and "Apply step by step instead".
- **Check your details:** fetched data in summary cards (signatory, business PAN, business details, documents), each with "Edit". Each document shows "Fetched" or "Not found"; a missing document gets "Upload".
- **Almost done:** a hub with the 6-step list, the next step, and "Switch to step-by-step application".

**Acceptance:**
- Aadhaar never appears beyond its last 4 digits.
- Fetched values are clearly labelled with the date fetched.
- A missing document can be uploaded without leaving the flow.

### APP-03 Application hub (A5)
- **Purpose:** the home page while the application is live.
- **Content:**
  - application id `onb_…` (copyable)
  - status badge
  - "Step N of 13" progress
  - step list showing each step's status, `last_error_code` hint, `attempt_count / max_attempts` when failed, and `reviewer_message` when reopened
  - "Next: \<step\>" card from `next_recommended_step`
  - `expires_at` notice when less than 7 days remain
  - correction banner when `CORRECTION_REQUIRED` (→ APP-17)
- **Actions:** "Continue" goes to the next step. Each step row links to its step. "Withdraw application" (→ APP-20) shows only when `WITHDRAW` is in `available_actions`.
- **States:**
  - `SUBMITTED`, `UNDER_REVIEW` and `ON_HOLD` → redirect to APP-16
  - terminal states → APP-18
- **Accept:** the step list order matches `StepKey`; `BLOCKED` steps show "Contact support" and no Continue.

### APP-04 Consent (`CONSENT`, A8)
- **Content:** consent and terms text (versioned; source in FQ-9), the purposes the applicant agrees to, and a checkbox "I have read and agree".
- **Submits:** `consent_version`, `terms_version`, `purposes[]`, `accepted: true`.
- **Errors:** `CONSENT_VERSION_STALE` 422 → reload the text using the versions in the problem body, then ask again.
- **Accept:** cannot submit without the checkbox; text is scrollable and readable at 375 px.

### APP-05 Program and entity type (`PROGRAM_SELECTION`)
- **Fields:**
  - `applicant_category = ENTERPRISE` and `product_type = WHITE_LABEL`, both fixed and shown as an info row
  - `entity_type`, a radio card: 1 Private Limited · 2 LLP · 3 Partnership · 4 Sole Proprietorship
- **Note:** changing the entity type later resets PAN, documents, declaration and agreement (the `StepKey.invalidates` rule). A confirm dialog appears when the step is already completed.
- **Accept:** the "Partner APIs" option is not offered (the server refuses it).

### APP-06 Business PAN (`BUSINESS_PAN`, A12)
- **Field:** PAN, uppercase, matching `^[A-Z]{5}\d{4}[A-Z]$`. The 4th character must match the entity type: C for Pvt Ltd, F for LLP and Partnership, P for Sole Prop. A hint explains this.
- **Outcomes:**

| Response | Behaviour |
|---|---|
| `200` | Show `pan_masked`, `name_as_per_pan`, `holder_type`, then Continue |
| `202` | Pending card with polling |
| `422 VERIFICATION_FAILED` | "We couldn't verify this PAN", with retry; show attempts left |
| `422 PAN_HOLDER_TYPE_NOT_ALLOWED` | Explain the required holder type |
| `409 PAN_ALREADY_REGISTERED` | "This PAN is already registered with ELOKA", plus support |
| `423 STEP_BLOCKED` | APP-21 |

- **Accept:** the full PAN is never shown back after submit.

### APP-07 Business details (`BUSINESS_DETAILS`)

| Field | Rule (server `StepData`) | Required |
|---|---|---|
| Business name `name` | `^[-a-zA-Z0-9 ,./:&']{2,100}$` | yes |
| Company type `company_type` | 1–40 characters | yes |
| Authorised signatory `authorized_signatory_name` | `^(?:[A-Za-z]+\.? ){0,2}[A-Za-z]+$` | yes |
| Contact mobile `contact_person_cell` | `^[6-9]\d{9}$` | yes |
| Alternate mobile `alternate_mobile` | `^[6-9]\d{9}$` | no |
| Address line 1 `current_address_line1` | 10–200 | yes |
| Address line 2 `current_address_line2` | 1–200 | no |
| District `current_address_district` | 2–100 | yes |
| State `current_address_state` | from A20 list | yes |
| Pincode `current_address_pincode` | `^[1-9]\d{5}$` | yes |
| GSTIN `gstin` | `^\d{2}[A-Z]{5}\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$` | no |
| CIN `cin` | `^[LU]\d{5}[A-Z]{2}\d{4}[A-Z]{3}\d{6}$` | no |
| Signature `signature_document_id` | upload, type `SIGNATURE`, JPEG / PNG / PDF ≤ 10 MB | yes |

- **Notices:** if `name_matches_pan:false` comes back, show an info note: "Your business name differs from the PAN record. Our team will check this." The step still completes.
- **Accept:** fields are grouped into "Business", "Signatory and contact" and "Address"; tab order follows the visual order.

### APP-08 Email verification (A13, A14)
- **Flow:**
  1. The applicant enters an email (`[^@\s]+@[^@\s]+\.[^@\s]+`, ≤ 254). A13 returns `202` with a `verification_id` (expires in 10 minutes).
  2. A 6-digit OTP view; A14 confirms.
- **Errors:** wrong or expired code, same patterns as APP-02. "Change email" is available.
- **Accept:** shows the email the code went to; resend is limited by the timer.

### APP-09 Signatory KYC with DigiLocker (A15, A16)
- **Content:** what DigiLocker shares, and a consent line.
- **Flow:**
  1. A15 (`method: DIGILOCKER`) → `redirect_url`
  2. Full-page redirect
  3. The return URL lands on this screen, which finalises with A16 (pending polling)
- **Results:**
  - success: shows the masked Aadhaar `XXXX-XXXX-1234` and the name
  - failed: retry, showing attempts left
  - name mismatch: info note
- **Accept:** the client never asks for or stores an Aadhaar number; the flow survives a page reload on return.

### APP-10 Live photo (`LIVE_PHOTO`)
- **Content:** framing guide, then capture → preview → retake or use photo (reuses `Camera`). Location capture is optional (`captured_latlong`, 3–60 characters).
- **Behaviour:** upload as `LIVE_PHOTO` (A21), then A8 with `document_id`.
- **Errors:** camera permission denied → steps to fix it, plus "Use another device".
- **Accept:** works on mobile with the front camera; no photo is stored before upload.

### APP-11 Documents checklist (A21–A25, `DOCUMENTS`)

| Entity type | Required documents |
|---|---|
| Private Limited, LLP | `ADDRESS_PROOF`, `INCORPORATION_CERTIFICATE` |
| Partnership | `ADDRESS_PROOF`, `PARTNERSHIP_DEED` |
| Sole Proprietorship | `ADDRESS_PROOF` |

The list comes from the server-side `required-documents` config (FQ-10: expose it to the client). Optional uploads: `PAN_CARD`, `GST_CERTIFICATE`, `BOARD_RESOLUTION`, `OTHER`.

- **Each document is a `DocumentTile`:**
  - upload, replace (any type except `OTHER` replaces the old file) and remove (A25 soft delete)
  - statuses `PENDING_REVIEW`, `VERIFIED`, `REJECTED` (with reviewer note), `SUPERSEDED`
- **Limits:** JPEG, PNG or PDF, ≤ 10 MB. These are checked on the client before upload; the server checks magic bytes.
- **Submit:** A8 with `{}`. `409 PREREQUISITE_INCOMPLETE` lists `missing_documents` by name.
- **Accept:** upload progress is shown; a failed upload can be retried without losing the others.

### APP-12 Bank account (A17, A18, A19; re-auth)
- **Fields:**
  - bank, searchable select from A19 (`bank_code`, `bank_id`)
  - IFSC `^[A-Z]{4}0[A-Z0-9]{6}$`, uppercase
  - account number `^\d{9,18}$`, entered twice to confirm, masked
  - proof upload, type `BANK_PROOF`
- **Flow:** submit → re-auth dialog (APP-19) → A17.
- **Results:**
  - success: shows the masked account and the holder name
  - name mismatch: the step completes and an info note says it goes to review
  - failure: retry, showing attempts left
- **Accept:** the account number is never put in a draft or in logs; after submit only the masked value is shown.

### APP-13 White-label setup (`WHITE_LABEL_SETUP`, A26)
- **Fields:**
  - App name: company-name regex, ≤ 60
  - Sub-domain label: `^[a-z0-9]([a-z0-9-]{1,38}[a-z0-9])$`, 3–40 characters. A26 runs with a 400 ms debounce. Results: available ✓ with `default_url`; or `INVALID_FORMAT`, `RESERVED_WORD` or `TAKEN`, each with `suggestions[]` as chips.
  - Logo: optional upload, type `LOGO`
  - Theme: six `#RRGGBB` values (`primary`, `primary_light`, `primary_dark`, `accent`, `accent_light`, `accent_dark`). Presets come from `src/constants/colorThemes.js`, with custom values through `ColorPickerWidget`.
  - `navstyle`: dark / light
- **Live preview:** the reused `AppPreview`, themed through `buildOrgTheme`. A contrast warning appears when the tenant's primary colour falls back.
- **Errors:** `409 SUBDOMAIN_TAKEN` on submit → refresh the suggestions.
- **Accept:** the preview updates without lag; availability results are announced to screen readers (`aria-live=polite`).

### APP-14 Review summary and declaration (`DECLARATION`)
- **Content:** one `SectionCard` per completed step, using `KeyValueList` with masked values, plus an "Edit" link. A note warns that editing invalidates the declaration and agreement.
- **Declaration:** text and version (source in FQ-9), a checkbox, then A8 (`declaration_version`, `accepted: true`).
- **Errors:** `409 PREREQUISITE_INCOMPLETE` → list `missing_steps` with links.
- **Accept:** nothing editable inline; every section is reachable by keyboard.

### APP-15 Agreement and e-sign (A27–A29; re-auth)
- **Flow:**
  1. A27 creates or returns the agreement. Show the version and a preview (PDF link, `GET …/pdf`) plus `content_sha256` in "Details".
  2. "Sign agreement" → re-auth → A28 → open `signing_url` (`provider_pipe` decides the provider adapter in `src/features/onboarding/services/esign/*`).
  3. On return, finalise with A29.
- **States:** `READY`, `SIGNING` (pending), `SIGNED` (success, then auto-submit → APP-16), `SUPERSEDED` (show "Your details changed; review the new agreement"), and `already_signed`.
- **Accept:** the client never sets "signed" itself; the result comes only from A29. No reliance on `postMessage`.

### APP-16 Submitted / under review (A5, A30)
- **Content:**
  - status badge (`SUBMITTED`, `UNDER_REVIEW` or `ON_HOLD` in plain words)
  - what happens next
  - `submitted_at`
  - timeline from A30 `{event, step_key, status, occurred_at}`
- **Actions:** "Withdraw", only when `available_actions` contains it, which means before a reviewer claims the case.
- **Empty timeline:** "Nothing yet. We'll update this page as your application moves."
- **Accept:** auto-refreshes every 60 s when visible; no PII on the page.

### APP-17 Corrections required
- **Content:** a warning banner with `correction.message` and the due date (`due_at`, countdown when less than 3 days), then the reopened steps (`step_keys`), each with its `reviewer_message` and a "Fix" link. Steps that were invalidated as a consequence are listed under "Also needs a quick re-check".
- **Action:** "Resubmit" (A10), enabled once the reopened steps are satisfied. Re-signing the agreement, if required, auto-resubmits.
- **Accept:** the due date is shown in IST; an overdue correction shows the expired outcome.

### APP-18 Outcomes
| Status | Message | Actions |
|---|---|---|
| `APPROVED` | "Approved. We're setting up your platform at `{label}.{root}`. We'll contact you when it's ready." | Contact support |
| `REJECTED` | The `applicant_message` from staff (never the internal note) | Contact support (re-applying is FQ: OQ-B7) |
| `WITHDRAWN` | "You withdrew this application." | Start a new application, if allowed |
| `EXPIRED` | "This application expired after 30 days of inactivity." | Start again |

- **Accept:** no fake activation; for `PROVISIONING`, `GO_LIVE_PENDING` and `ACTIVE`, show "setting up" until P9 exists.

### APP-19 Re-auth dialog (`/reauth`, `/reauth/verify`)
- **Flow:** "For your security, confirm it's you" → OTP sent to the verified mobile (masked) → verify → returns a `reauth_token` (single use, 300 s) to the caller.
- **Accept:** focus is trapped; the token is held only in memory and dropped after one use.

### APP-20 Withdraw dialog (A11)
- **Content:** what withdrawing means (it releases the sub-domain); a required reason select whose values are agreed in FQ-11. Then re-auth → A11.
- **Accept:** a `ConfirmDialog` with danger tone; success → APP-18 WITHDRAWN.

### APP-21 System states

| Case | UI |
|---|---|
| 412 stale | Reload the snapshot, then show a toast (see §3.3) |
| 423 `STEP_BLOCKED` | "This step is locked after too many attempts. Contact support." Shows the application id |
| `SESSION_EXPIRED` / refresh fails | "Your session ended" → sign in again (APP-02); the application resumes |
| 409 `PROVIDER_NOT_CONFIGURED` | "This check isn't available right now. Try later." |
| Network / timeout | Retry |

## 5. Staff console screens (roles REVIEWER / APPROVER / AUDITOR / ADMIN; host `staff.<root>`; desktop-first)

**Shared across every STF screen:**
- `LayoutStaff`: a left navigation ("Queue", "Assigned to me") and a top bar with the staff name, roles and sign-out.
- Actions are shown or hidden by the permissions from `/me`.
- Writes send `If-Match` (except claim) and `Idempotency-Key`.
- At ≥ 768 px the layout works fully; below that it is read-only friendly.

### STF-01 Sign-in
- **Content:** "Sign in with Google" (`/v1/admin/auth/google` with an `id_token`). In local/test only, the OTP form (`/auth/otp`, `/auth/otp/verify`).
- **Errors:** `STAFF_NOT_ALLOWED` 403 → "Your account isn't enabled for the console"; `PROVIDER_NOT_CONFIGURED` → "Google sign-in isn't configured".
- **Accept:** no token in the URL; after sign-in → STF-02.

### STF-02 Review queue (S1)
- **Filters:** status (multi-select, default `SUBMITTED, UNDER_REVIEW, ON_HOLD`), "Assigned to me" toggle, search box (company name, `onb_` id, or exact PAN).
- **Columns:** application id, company, entity type, status, submitted, assigned to, flags count.
- **Paging:** `next_cursor` drives "Load more".
- **Empty:** "No applications match these filters".
- **Accept:** the filters are kept in the URL query; rows are keyboard-navigable; below 860 px it switches to the card view.

### STF-03 Case view (S2, S3, S4)
- **Header:** id, company, status, assigned staff, Claim (S3) or Release (S4, reason required when releasing someone else's case with `onboarding.reassign`).
- **Sections:**
  - **Overview:** program, dates.
  - **Flags:** `BUSINESS_NAME_DIFFERS_FROM_PAN`, `KYC_NAME_DIFFERS_FROM_SIGNATORY`, `BANK_HOLDER_DIFFERS`, `BANK_ACCOUNT_USED_BY_ANOTHER_APPLICATION`, each with a short explanation.
  - **Approval blockers:** the reasons from `approval_blockers`.
  - **Business profile:** masked, with Reveal for PII when permitted (STF-09).
  - **Verifications:** kind, status, `failure_code`, `name_match_score`, decide (STF-05).
  - **Documents:** `DocumentTile` list with view, verify and reject (STF-04).
  - **Corrections**
  - **Review history**
- **Errors:** `NOT_ASSIGNED` 403 → "Claim this case to act on it"; 412 → reload.
- **Accept:** review actions are disabled until the case is claimed by the viewer; AUDITOR sees read-only.

### STF-04 Document viewer and decision (download, S5, S6)
- **Content:** an inline viewer (image or PDF) from the audited download; Verify (optional note); Reject (`reason_code` + `message`, both required).
- **Accept:** the download is not cached (`no-store`); AUDITOR cannot open documents.

### STF-05 Verification decision (S7)
- **Content:** shown for `MANUAL_REVIEW` items: Accept or Reject with a `reason_code`.
- **Accept:** the decision updates the blockers right away.

### STF-06 Request corrections (S8)
- **Fields:** a `step_keys[]` multi-select (excluding `MOBILE_VERIFICATION`), `reason_code` ≤ 40, `message` ≤ 1000 (character counter), `due_in_days` 1–90 (default 15).
- **Note:** this clears earlier recommendations.
- **Accept:** cannot submit without at least one step.

### STF-07 Recommendation, the maker step (S9)
- **Content:** Recommend approve or reject, with a note.
- **Accept:** after it is sent, the UI says "A different approver must make the final decision".

### STF-08 Approve, reject, hold: the checker step (S10–S12)
- **Approve:** note. Disabled with the blocker list while `approval_blockers` is non-empty.
- **Reject:** `reason_code`, `applicant_message` (shown to the applicant) and `internal_note` (never shown to the applicant), clearly labelled.
- **Hold:** place with a reason code, or release.
- **Errors:** `MAKER_CHECKER_VIOLATION` → "You claimed or recommended this case, so another approver must decide"; `APPROVAL_BLOCKED` lists its `reasons`.
- **Accept:** the dialogs keep the two messages visually distinct.

### STF-09 PII reveal (S13)
- **Fields:** field (PAN / ACCOUNT / MOBILE / EMAIL) and a required purpose.
- **Behaviour:** shows the value in the dialog only; it clears when the dialog closes. `VALUE_NOT_AVAILABLE` → "Not provided".
- **Accept:** the value is never in global state, logs or the URL.

### STF-10 Audit trail (S21)
- **Content:** a Timeline of the latest 500 events, oldest first, with time (IST), actor, action and entity.
- **Accept:** readable at 1280 px; no raw PII.

### STF-11 Permission states
- **Content:** `NOT_ASSIGNED`, missing permission and `STAFF_NOT_ALLOWED`, each with an `ErrorState` that explains who can act.

## 6. Release 1 reskin groups (tenant-branded; visual only)

| ID | Group | Main files | Key changes |
|---|---|---|---|
| R1-01 | App shell | `src/components/ui/{NavBar,SideBar,BottomAppBar,PageTitle}` | Tokens; brand slot; focus states; label icon buttons |
| R1-02 | Login + OTP | `src/screens/LoginPanel/**` | Remove 43 hex values; labelled mobile field; OTP labels |
| R1-03 | Agent home | `src/screens/Home/**` | Skeletons; EmptyState |
| R1-04 | Admin dashboards | `src/screens/Admin/Dashboard/**` | Chart palette from tokens (64 hex values) |
| R1-05 | Network | `src/screens/Admin/Network/**` | Table/card tokens; StatusBadge |
| R1-06 | History and statements | `src/screens/History/**` and related | Merge the duplicate table styling |
| R1-07 | Profile | `src/screens/Profile/**`, `Admin/ProfilePanel/**` | KeyValueList |
| R1-08 | Business settings / pricing | `Admin/PricingConfig`, `Admin/PricingCommission` (layout) | SectionCard; FormField look |
| R1-09 | Agent onboarding | `src/features/onboarding/**` (presentation) | Align with the APP visual language |
| R1-10 | Query center / raise issue | `Admin/QueryCenter`, `RaiseIssueCard` | EmptyState; StatusBadge |
| R1-11 | Shared states | `src/pages/404`, `ErrorBoundary` | ErrorState / EmptyState |

**Token-only, no redesign:** `products/*` transaction framework, the EkoConnect iframe, CMS landing pages.

## 7. Stitch design workflow

### 7.1 Files

`client/.stitch/` holds:
- `DESIGN.md`
- `SITE.md`
- `metadata.json` (project id, design-system asset id, screens)
- `prompts/<ID>.md`
- `designs/<ID>.{html,png}`. Approved screens are committed as the visual spec.

### 7.2 Sequence

| Step | Skill / tool | Gate |
|---|---|---|
| ST-1 | `extract-design-md` (reads the current theme; no build) + `design-md` structure + `taste-design` anti-patterns | — |
| ST-2 | Show the summary (name, colours, fonts, roundness) | **owner OK** (skill checkpoint) |
| ST-3 | `create_project` → `upload_design_md` (or the upload script if > 5 KB) → `create_design_system_from_design_md` → `list_design_systems` | ids recorded |
| ST-4 | `site-md` | — |
| ST-5 | `enhance-prompt` in the `generate-design` template: layout, content, states and interactions only. **No hex values, fonts or theme words.** Fictional data. | — |
| ST-6 | `generate_screen_from_text` with the design system id. Order: DS-00 → APP (MOBILE, plus DESKTOP for 02/03/07/11/13/15) → STF (DESKTOP) → R1. Assets fetched through `download_file`; screenshots viewed. | — |
| ST-7 | `edit_screens` for targeted fixes; `generate_variants` (REFINE) only when the layout is in doubt | — |
| ST-8 | Review checklist (§7.3), batch by batch | **owner OK per batch** |

**Tool rules:**
- Generation can take minutes. **Don't retry.** On a timeout, poll `get_screen` every 30 s, up to 10 times.
- Always surface the Stitch `output_components` text and suggestions in the task notes.

### 7.3 Design review checklist

- [ ] Uses only DS-00 components and tokens (no new colours or fonts)
- [ ] Every field, validation hint and action from §4 / §5 is present; nothing extra
- [ ] Loading, empty, error, pending and success states are all shown or specified
- [ ] Works at 375 / 768 / 1280; single column below lg for applicant screens
- [ ] Contrast meets AA; focus order follows the visual order; touch targets ≥ 44 px
- [ ] One primary action; destructive actions use confirm dialogs
- [ ] No invented data, metrics or real-looking PII
- [ ] Every element maps to an existing or planned component (FRONTEND_ARCHITECTURE §7)
- [ ] Every action maps to an existing endpoint (no screen for P9 or other unbuilt APIs)

## 8. Traceability matrix

| Screen | Requirement | API | Build task | Design task |
|---|---|---|---|---|
| DS-00 | Design system | — | DS-1, CMP-1…14 | ST-6a / ST-8a |
| APP-01 | Journey step 0 | — | APP-C | ST-6b |
| APP-02 | `MOBILE_VERIFICATION` | A1, A2 | APP-C | ST-6b |
| APP-01B | Choose application path (owner request) | none yet (FQ-16) | — | ST-6e |
| APP-22 | DigiLocker quick path (owner request) | none yet (FQ-16); reuses A15/A16, A17, A26, A27–A29 | — | ST-6e |
| APP-03 | Snapshot / resume | A5 | APP-D | ST-6b |
| APP-04 | `CONSENT` | A8 | APP-E | ST-6b |
| APP-05 | `PROGRAM_SELECTION` | A8 | APP-E | ST-6b |
| APP-06 | `BUSINESS_PAN` | A12 | APP-F | ST-6b |
| APP-07 | `BUSINESS_DETAILS` | A8, A20, A21 | APP-G | ST-6b |
| APP-08 | `EMAIL_VERIFICATION` | A13, A14 | APP-H | ST-6b |
| APP-09 | `SIGNATORY_KYC` | A15, A16 | APP-I | ST-6b |
| APP-10 | `LIVE_PHOTO` | A21, A8 | APP-J | ST-6b |
| APP-11 | `DOCUMENTS` | A21–A25, A8 | APP-K | ST-6b |
| APP-12 | `BANK_ACCOUNT` | A17–A19, re-auth | APP-L | ST-6b |
| APP-13 | `WHITE_LABEL_SETUP` | A26, A21, A8 | APP-M | ST-6b |
| APP-14 | `DECLARATION` | A8 | APP-N | ST-6b |
| APP-15 | `AGREEMENT_ESIGN` | A27–A29, re-auth | APP-O, APP-P | ST-6b |
| APP-16 | Submitted / review status | A5, A30 | APP-Q | ST-6b |
| APP-17 | Corrections | A5, A10 | APP-Q, APP-P | ST-6b |
| APP-18 | Terminal and approved states | A5 | APP-Q | ST-6b |
| APP-19 | Step-up re-auth | `/reauth*` | APP-R | ST-6b |
| APP-20 | Withdraw | A11 | APP-R | ST-6b |
| APP-21 | Error recovery | problem+json | APP-S | ST-6b |
| STF-01 | Staff sign-in | `/auth/*`, `/me` | STF-A | ST-6c |
| STF-02 | Queue | S1 | STF-B | ST-6c |
| STF-03 | Case view, claim / release | S2–S4 | STF-C | ST-6c |
| STF-04 | Document decisions | S5, S6, download | STF-D | ST-6c |
| STF-05 | Verification decisions | S7 | STF-D | ST-6c |
| STF-06 | Corrections | S8 | STF-E | ST-6c |
| STF-07 | Recommendation (maker) | S9 | STF-E | ST-6c |
| STF-08 | Approve / reject / hold (checker) | S10–S12 | STF-F | ST-6c |
| STF-09 | PII reveal | S13 | STF-G | ST-6c |
| STF-10 | Audit trail | S21 | STF-G | ST-6c |
| STF-11 | Permission states | 403 codes | STF-A…G | ST-6c |
| R1-01…11 | Release 1 parity + quality | existing EPS interactions (unchanged) | R1-01…11 | ST-6d / ST-8d |

## 9. Journeys and navigation map

**Applicant (hub and spoke):**
1. `/partner/onboarding` → APP-01 → APP-02.
2. A new applicant goes to APP-04 → APP-05 → … → APP-15. Each step is followed by "Continue" to `next_recommended_step`; the user can jump back to the hub at any time.
3. A returning applicant goes to APP-03 (hub) and then to whichever step they choose.
4. Signing the agreement auto-submits → APP-16. A reviewer's correction → APP-17 → fixed steps → resubmit → APP-16. A final decision → APP-18.

**Staff:**
1. `/staff` → STF-01 → STF-02 queue.
2. Opening a row → STF-03 case. Claim → act through dialogs STF-04…09 → back to the queue.
3. STF-10 is a tab inside STF-03.

**Release 1:** unchanged routes and menus. Only the presentation changes.

## 10. Responsive and accessibility requirements

**Breakpoints tested:** 375 (mobile), 768 (tablet), 1280 (desktop). No horizontal page scroll at any of them.

**Mobile:**
- The primary action is full width and sits in a sticky footer on step forms, with padding for the safe area.
- Inputs are ≥ 48 px tall with 16 px text.

**Keyboard:**
- Everything is reachable and operable.
- Dialogs trap focus and return it to the trigger.
- Esc closes dialogs that are not destructive.
- Skip-to-content link in `LayoutPlatform` and `LayoutStaff`.

**Screen readers:**
- Every input has a label.
- Errors use `aria-describedby`. A form-level error summary uses `role="alert"` and focus moves to it on submit.
- Live regions for availability checks and async status changes.
- Decorative icons are `aria-hidden`.

**Contrast:**
- Text ≥ 4.5:1, large text ≥ 3:1, UI boundaries and focus ≥ 3:1.
- Status is never shown by colour alone.

**Motion:** respect `prefers-reduced-motion`; no infinite animations.

**Language:** English for now (OQ-P13). Copy lives in feature constants so it can be translated later.
