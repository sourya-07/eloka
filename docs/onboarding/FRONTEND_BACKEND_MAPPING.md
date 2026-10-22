# ELOKA Local Admin Onboarding — Frontend → Backend Mapping

> Status: **DRAFT for architecture review** · **Revised 14 October 2026:** Java 25 + Spring Boot 4, MySQL 8.4 · Phase 0 (design only; the frontend was **not** modified)
> Related: [ELOKA_ONBOARDING_JOURNEY.md](./ELOKA_ONBOARDING_JOURNEY.md) · [API_SPECIFICATION.md](./API_SPECIFICATION.md)

Labels: **CONFIRMED** · **EXISTING FRONTEND BEHAVIOUR** · **PROPOSED / RECOMMENDED** · **ASSUMPTION** · **OPEN QUESTION**

All paths are relative to `/Users/souryagupta/Eko/wlc-webapp`.

---

## 1. Read this first

1. **There are no Local Admin onboarding screens in this frontend.** It contains:
   - **agent** onboarding into an existing tenant: `/signup` (self) and `/agent-onboarding` + `/admin/agent-onboarding` (assisted), in `src/features/onboarding`,
   - post-tenant admin screens that the current process re-uses. The main one is **Business Settings → Agreement Signing** (doc step 11).

   The mapping therefore has three parts:
   - §3: existing screens that can be **reused** for Local Admin onboarding,
   - §4: screens that exist only in other apps (Eko Connect, Go-Live tool),
   - §5: **new** screens required.
2. **Two API contracts exist in this working tree.** The analysed checkout is branch `fix/auto-trigger-balance-api-post-login` with uncommitted changes.

   | | Committed code (`HEAD`) | Uncommitted local work (WIP) |
   |---|---|---|
   | Transport | Browser → `NEXT_PUBLIC_API_BASE_URL` + `/transactions/do`, `/transactions/dojson`, `/transactions/upload`, `/transactions/customupload`, `/authentication/*` | Browser → Next.js proxy `src/pages/api/transaction.js`, `src/pages/api/upload.js`, `src/pages/api/transactionjson.js` (untracked files) → backend |
   | Envelope | `{ interaction_type_id, source:"WLC", client_ref_id, … }` + Bearer token; or the "tf-req" tunnel headers `tf-req-uri-root-path` (`/ekoicici/v1/v2/v3`), `tf-req-uri`, `tf-req-method` | Same, plus hard-coded test fields (`user_code:"99029899"`, `initiator_id:"1234567891"`, `bc:"26"`, `org_id:"26"` override, `realsourceip:"106.192.197.190"`) |
   | Profile refresh in onboarding | `POST /authentication/refresh-profile` | interaction `151` → `response_type_id 369` |

   The untracked `CLAUDE.md` says every call goes through `/api/transaction`. In committed code that is not true: about 133 call sites use `NEXT_PUBLIC_API_BASE_URL` directly from the browser. This blueprint treats **HEAD** as the existing behaviour and calls out WIP where it matters.
3. **Response envelope** the frontend understands (EXISTING FRONTEND BEHAVIOUR):
   - success = `status == 0` or `response_status_id == 0`, or a step-specific `response_type_id`,
   - errors in `message` + `invalid_params{field: message}`,
   - data in `data`.

   The proxy always returns HTTP 200 and the real outcome is inside the body. The new onboarding API (§6) uses proper HTTP status codes. The frontend adapter must translate (PROPOSED).

---

## 2. Existing frontend architecture relevant to onboarding

| Area | Finding | Evidence |
|---|---|---|
| Framework | Next.js 14 (pages router), React 18, TypeScript + JS, Chakra UI 2, react-hook-form, SWR, Jest + RTL, Storybook | `package.json` |
| Onboarding module | Self-contained feature with a dependency-injection contract (`OnboardingServices`: `accessToken`, `generateNewToken`, `isAndroid`, `pubsub`), a "fat context" (`OnboardingProvider`), a reducer (`useOnboardingState`), and a **config-driven API pipeline** (`masterOnboardingSteps[].api.pipeline` executed by `executePipeline`) | `src/features/onboarding/contracts.ts`, `context/OnboardingContext.tsx`, `constants.ts`, `utils/executePipeline.ts` |
| Step selection | Steps filtered by: visibility → roles returned by the backend (`onboarding_steps[].role`) → org-metadata `hide` → org-metadata `optional` → resume from `role_list` | `utils/stepGenerator.ts` `generateInitialSteps` |
| Step rendering | `ContentRenderer` → `LocalStepForm` (declarative `formFields`, `form-engine` `Form`) or a custom component by name | `components/ContentRenderer.tsx` |
| Retry | "Smart retry": re-runs only the failed API inside a step pipeline. Held **in memory** only (`pipelineResults`). | `OnboardingContext.handleSubmit` |
| Skip | `handleSkip` marks a step `SKIPPED` **client-side only**. Nothing is sent to the backend. | `OnboardingContext.handleSkip` |
| Routing guard | Logged-in user with `onboarding == 1` or `userId == "1"` is forced to `/signup` | `src/components/ui/RouteProtecter/RouteProtecter.jsx:120-134` |
| Self-onboarding kill switches | `orgDetail.metadata.disable_self_onboarding.value`, env `NEXT_PUBLIC_DISABLE_SELF_ONBOARDING` | `SelfOnboarding/Onboarding.tsx`, `OnboardingWidget.tsx` |
| Tenant resolution | Host → `sub_domain` if it ends with `.${WLC_SUBDOMAIN_ROOT}` (`elokademo.in` in `.env.example`), else custom `domain` → `POST ORG_META_SERVICE` → `org_detail`; `1829` = not found → HTTP 404 page; cached in memory (`ORG_CACHE_EXPIRY_SEC`), `/clear_org_cache` refreshes | `src/lib/fetchOrgDetailsHelper.js` |
| Org write | Only `774 UPDATE_ORG_DETAILS` with `orgDetails.metadata[{name,type,value}]` (theme) | `src/screens/Admin/Configurations/ThemeConfig.jsx` |
| Auth storage | Tokens + profile in `sessionStorage`; `localStorage` only for `inf-last-login`, `inf-last-route`, SWR `app-cache`, CMS drafts | `src/lib/loginHelper.js`, `src/contexts/UserContext.js` |
| Android | e-sign through the bridge (`LEEGALITY_ESIGN_OPEN` / `LEEGALITY_ESIGN_RESPONSE`) | `src/utils/AndroidUtils.ts`, `hooks/useSignAgreement.ts` |
| Tests | `src/features/onboarding/__tests__/*` (widget, layout, content renderer, assisted flow, step filtering). No tests for `/signup` as a page. | — |

---

## 3. Existing screens → Local Admin backend responsibilities

The **Backend Responsibility / API / DB Entity / State** columns describe the **proposed** onboarding service (see API_SPECIFICATION.md). The **existing call** column shows what the screen calls today.

| Frontend Screen | Component (actual) | User Action | Existing call today | Backend Responsibility (proposed) | API (proposed) | DB Entity | State | External Dependency |
|---|---|---|---|---|---|---|---|---|
| Login: mobile entry | `src/screens/LoginPanel/LoginWidget` → `Login` | Enter mobile, request OTP | HEAD `POST /authentication/sendotp`; WIP interaction `515` | Pattern reused for applicant OTP; rate-limit; no account oracle | `POST /v1/onboarding/otp` | `otp_challenge` | — | SMS (EPS 553/554 or gateway) |
| Login: OTP verify | `LoginPanel/VerifyOtp` | Enter OTP | HEAD `POST /authentication/login`; WIP `518` → `151` | Verify, create/resume application, issue the applicant session | `POST /v1/onboarding/otp/verify` | `applicant`, `onboarding_application`, `applicant_session` | → `IN_PROGRESS` (T1) | — |
| Role selection "Tell us who you are?" | `src/features/onboarding/components/RoleSelection.tsx` (`RoleCard`) | Pick Retailer / Distributor (Enterprise card **hidden**) | `CREATE_PARTIAL_ACCOUNT 521` (`source:"NEWCONNECT"`); creates an EPS account immediately | Program selection (Enterprise + White Label). **No** EPS account at this point. | `PUT/POST …/steps/PROGRAM_SELECTION` | `onboarding_application.entity_type/product_type` | step `PROGRAM_SELECTION` | — |
| Step shell / stepper | `components/OnboardingSteps.tsx`, `OnboardingLayout.tsx`, `ContentRenderer.tsx` | Navigate steps | Profile refresh (HEAD `/authentication/refresh-profile`) → `onboarding_steps`, `role_list` | Server snapshot of all steps + `next_recommended_step` + `available_actions` | `GET /v1/onboarding/applications/current` | `onboarding_step` | all | — |
| PAN verification (agent) | `masterOnboardingSteps` `PAN_VERIFICATION` via `LocalStepForm` (fields `doc_id` regex `^([A-Z]){5}([0-9]){4}([A-Z]){1}$`, `pan_image`) | Enter PAN + upload the card | `UPLOAD_DOCUMENT 523` with `doc_type 2` (applicable role `12300`/`13000`) | Business PAN verify + dedup + name-as-per-PAN | `POST …/pan-verifications` | `business_profile`, `verification` | step `BUSINESS_PAN` | PAN provider (via EPS 205 or v3 `/tools/kyc/pan-*`) |
| Aadhaar upload (agent) | `masterOnboardingSteps` `AADHAAR_VERIFICATION` (`aadhaar_front_image`, `aadhaar_back_image`) | Upload Aadhaar front/back | `523` with `doc_type 1` | **Not recommended** for Local Admin. DigiLocker instead (minimisation). | — | — | — | — |
| DigiLocker | `components/custom/DigilockerRedirectionStep.tsx` | Open DigiLocker, return, Proceed | Get link: tf tunnel `POST /karza/digilocker-redirection-url` with root `/ekoicici/v1/marketuat` (**hard-coded UAT path**); submit: `USER_AADHAR_OTP_CONFIRM 552` with `token_id`, `is_consent:"Y"`; errors `2443` mismatch, `1709` incomplete | Server-side DigiLocker session + callback/poll; no Aadhaar number stored | `POST …/kyc-sessions`, `GET …/kyc-sessions/{id}` | `verification` (AADHAAR) | step `SIGNATORY_KYC` (`PENDING_EXTERNAL`) | Karza DigiLocker |
| Selfie / "Video KYC" | `components/custom/VideoKycStep.tsx` | Capture selfie (+ location) | `298` geo-capture (`captured_latlong` → `latlong`) then `523` `doc_type 3` | Live photo + optional face match; store `location_source` | Document flow + `POST …/steps/LIVE_PHOTO/submit` | `document`, `verification` (LIVENESS) | step `LIVE_PHOTO` | Liveness provider (TBD) |
| Location capture | `masterOnboardingSteps` `LOCATION_CAPTURE` (**`isVisible:false`**) + `OnboardingWidget` geolocation | Allow location | `298` | Store as metadata on the live photo / declaration; never as proof when the fallback is used | (part of the steps above) | `onboarding_step.draft_data` | — | Browser geolocation |
| Business details | `components/custom/BusinessDetailsStep.tsx` | Fill company/firm name, company type (Pvt Ltd / LLP / Partnership / Sole Prop), signatory, contact, alternate mobile, address, city, state, pincode | `USER_ONBOARDING_BUSINESS 522` (+ injected `latlong`) | Validate, name-match with PAN, store | `PUT/POST …/steps/BUSINESS_DETAILS` | `business_profile` | step `BUSINESS_DETAILS` | Name match (optional) |
| Bank account | `components/custom/AddBankAccountStep.tsx` | Choose bank, account, IFSC, passbook image | Bank list via `useBankList` (`BANK_LIST 155`); `ADD_BANK_ACCOUNT 454` (success `1445`) then `523` `doc_type 7` | Penny-drop + name match + encryption + dedup | `POST …/bank-accounts` | `bank_account`, `verification` (BANK), `document` | step `BANK_ACCOUNT` | Penny-drop (via EPS 454 or direct) |
| Secret PIN | `components/custom/SecretPinStep.tsx` | Set 4-digit transaction PIN (Pintwin encoding) | Booklet `170` (`GET_BOOKLET_NUMBER`), submit `5` | **Out of onboarding scope** for Local Admin. Admins set the PIN in-app after activation (existing `SET_PIN 736` / `show_set_pin`). **ASSUMPTION**, OQ-P3. | — | — | — | — |
| Sign agreement (agent) | `components/custom/SignAgreementStep.tsx` + `hooks/useSignAgreement.ts` + `services/esign/*` | Sign Agreement → provider popup / Android → Proceed | `USER_ONBOARDING_GET_AGREEMENT_URL 287` (`agreement_id ?? 5`, `latlong` default) → `short_url`, `document_id`, `pipe`; already signed `1615`/`1069`; submit `293` with `esign_completed:true` | Generate agreement, create provider session, **verify signing server-side**, store the signed PDF | `POST …/agreements`, `POST …/agreements/{id}/esign-sessions`, `GET …/agreements/{id}` + webhooks | `agreement`, `esign_request`, `document` | step `AGREEMENT_ESIGN`, then T3 | Leegality / Karza / Signzy / Digio |
| e-sign popup return | `src/pages/redirect` (posts `{type:"STATUS_UPDATE"}` to `window.opener`) | (automatic) | — | Backend status is the source of truth; the UI re-fetches status on this event | `GET …/agreements/{id}` | — | — | — |
| **Admin: Agreement Signing** (doc step 11) | `src/screens/Admin/PricingCommission/AgreementSigning.jsx` (route `/admin/business/agreement-signing`) | Verify business email (OTP), set business address, upload signature + signatory name, request org-name change | Email OTP `553`/`554`, verify `555` (`intent_id 16`); address: tf `/network/agents/profile/address/update`; signature: `customupload` tf `/network/agreement/upload-doc-sign-config` (`doc_type 19`, `intent_id 4`); recent signature tf `/network/agreement/recent-signature`; org name = **support ticket** | Before activation: these move into onboarding steps `EMAIL_VERIFICATION`, `BUSINESS_DETAILS` (address + signature). After activation the screen keeps its current role (the agent-agreement signature config). | `…/email-verifications`, `…/steps/BUSINESS_DETAILS`, document upload | `applicant`, `business_profile`, `document` | — | EPS (post-activation only) |
| Admin: Theme | `src/screens/Admin/Configurations/ThemeConfig.jsx` (`/admin/configurations`) | Pick a preset / custom colours | `774 UPDATE_ORG_DETAILS` (`metadata.theme`) → `/clear_org_cache` | During onboarding: colours go into `branding_draft`. At activation: published via `774`. After activation: unchanged. | `PUT …/steps/WHITE_LABEL_SETUP`; outbox → EPS 774 | `branding_draft`, `tenant_branding` | `WHITE_LABEL_SETUP`; branding `PUBLISHED` | EPS 774, org-meta cache |
| Admin: Landing page editor | `src/screens/Admin/Configurations/LandingPageEditor.tsx` (flag `CMS_LANDING_PAGE`, dev only) | Build a landing page | Saves **only to localStorage** (TODO: backend) | Out of scope for onboarding (future branding feature) | — | — | — | — |
| Admin: Onboard Agents | `src/screens/Admin/OnboardAgents/*` | Add agents | tf `/network/agent/multiple_onboarding`, `/bulk_onboarding` | Post-activation feature. Confirms the Local Admin can only use it once the tenant is `ACTIVE`. | — | — | — | — |
| Assisted agent onboarding | `src/features/onboarding/screens/AssistedOnboarding/*` | Admin onboards an agent by mobile + OTP | `194` add agent, `197` verify OTP (`874/862/873/876/1566/302`) | Not part of Local Admin onboarding. Pattern reference for staff-assisted onboarding (OQ-P11). | — | — | — | — |

---

## 4. Screens from the current process that live outside this repo

These appear in the onboarding document's screenshots. Their code is **not available**. Listed so the backend covers them.

| Screen (doc step) | App | User action | Backend responsibility (proposed) | API (proposed) | DB entity | State |
|---|---|---|---|---|---|---|
| "Hey, Login Now" mobile / Google (1) | Eko Connect | Mobile + OTP (or Google) | Applicant OTP. Google sign-in is **OPEN** (OQ-P12). | `/v1/onboarding/otp*` | `applicant` | T1 |
| "Tell us who you are" Seller / Distributor / Enterprise (2) | Eko Connect | Select Enterprise | Program selection | `…/steps/PROGRAM_SELECTION` | `onboarding_application` | — |
| "Verify PAN Details", Step 1 of 3 (3) | Eko Connect | Company PAN | PAN verification | `…/pan-verifications` | `business_profile`, `verification` | `BUSINESS_PAN` |
| "What are you looking for?" Partner APIs / White Label portal; Company App Name; Company Sub Domain → `https://<x>.finstore.app` (4) | Eko Connect | Choose White Label, enter name + sub-domain | Program product + sub-domain reservation + branding draft | `…/subdomains/availability`, `…/steps/WHITE_LABEL_SETUP` | `domain_reservation`, `branding_draft` | `WHITE_LABEL_SETUP` |
| "Legal Agreement: Sign Agreement" (5) + Protean/NSDL Aadhaar e-sign via Leegality (6) | Eko Connect + provider | Sign with Aadhaar OTP | Agreement + e-sign | `…/agreements*` | `agreement`, `esign_request` | `AGREEMENT_ESIGN` |
| Go-Live Admin login, "Request New ID" (7) | Go-Live tool | Staff sign-in | Staff console sign-in (SSO) | IdP | `staff_user` | — |
| Go-Live 4-step wizard: mobile (8), email (9), PAN + "Update Name" (10), "Final Confirmation / Onboarding Complete" → emails to CS (12) | Go-Live tool | Staff re-verifies and confirms | **Replaced** by applicant-side steps + the staff review queue | `/v1/admin/applications/*` | `review_action` | `SUBMITTED → UNDER_REVIEW` |
| CS email inbox + CRM upload (13) | Gmail + CRM | Manual verification + upload | Review console + async CRM sync | `/v1/admin/applications/{id}/documents/*` | `document`, `outbox_event` | `UNDER_REVIEW` |

Observed defects in those apps, from the screenshots:
- the Go-Live tool shows raw API JSON (Gmail message ids) to the operator,
- one "Onboarding Complete" produced **7 identical emails** ("New Admin onboarding details and Agreement code : 38023001"),
- the confirmation text names one specific CS employee.

---

## 5. New screens required (PROPOSED / RECOMMENDED)

| New screen | Who | Purpose | Reuse |
|---|---|---|---|
| Onboarding landing (`onboard.{zone}` or a route in `wlc-webapp`) | Applicant | Start / resume, invite link handling | `LayoutLogin`, `LoginWidget` patterns |
| Consent & Terms | Applicant | DPDP / Aadhaar consent, T&C version | New |
| Program selection | Applicant | Enterprise + White Label + entity type | `RoleSelection` cards |
| Email verification | Applicant | OTP | `AgreementSigning` email OTP block |
| Documents checklist | Applicant | Upload required docs per entity type | `form-engine` FILE field |
| White-label setup | Applicant | App name, sub-domain availability, logo, theme preview | `ThemeConfig` presets (`src/constants/colorThemes.js`) |
| Summary + declaration | Applicant | Final check | New |
| Application status / corrections | Applicant | See review status, reopened steps, reviewer messages, go-live progress | New |
| Staff: queue, case view, document review, corrections, approve/reject, provisioning, go-live checklist, leads | Eko staff | Replaces the Go-Live tool + email | New staff console (separate app recommended, OQ-P2) |

Frontend changes the existing onboarding module would need to serve Local Admins (PROPOSED; **not done in this task**):

1. A new `OnboardingServices` implementation that talks to the onboarding API (REST + `If-Match` + `Idempotency-Key`) instead of the EPS interaction envelope. Alternatively, a thin adapter that maps REST responses to the `PipelineResult` shape (`status`, `list[]`) the step components already consume.
2. Initialise steps from `GET /applications/current` (`steps[].status`). Do not use `onboarding_steps` / `role_list` positions.
3. Persist skips (`POST …/steps/{key}/skip`).
4. Remove the client-side success shortcuts (`esign_completed: true`; trusting any `STATUS_UPDATE` postMessage). Re-fetch status instead.
5. Show the Enterprise role card only on the platform onboarding host.
6. Platform host without a tenant: `_app.tsx` currently 404s any host whose org lookup returns `1829`. A platform-org (or onboarding-only) mode is needed (OQ-P2).

---

## 6. Hard-coded values, TODOs and risks found in the frontend

| # | Finding | Where | Committed? | Backend implication |
|---|---|---|---|---|
| F1 | DigiLocker link fetched with `tf-req-uri-root-path: "/ekoicici/v1/marketuat"` (UAT market path) from the browser | `DigilockerRedirectionStep.tsx:70` | **HEAD** | Provider calls must be server-side, with environment config |
| F2 | e-sign success taken from any `window` `message` with `type === "STATUS_UPDATE"`; **no `event.origin` check** | `hooks/useSignAgreement.ts` (~:237) | **HEAD** | Backend verifies signing with the provider; the UI only re-fetches |
| F3 | Step submit sends `esign_completed: true` + client `completion_timestamp` | `SignAgreementStep.tsx:168` | **HEAD** | Never trusted; the server timestamps |
| F4 | `agreement_id` defaults to `5` when missing | `services/esign/esignService.ts:70` | **HEAD** | Agreement template chosen server-side by program and version |
| F5 | Geolocation fallback `27.176670,78.008075` sent as if real | `OnboardingWidget.tsx:53` (`DEFAULT_LATLONG`), also `…,7787` in e-sign / PIN calls | **HEAD** | Store `location_source`; never use the fallback as evidence |
| F6 | Skips not persisted; retry state lost on refresh | `OnboardingContext.tsx` | **HEAD** | Server-side step status |
| F7 | Enterprise role defined but hidden (`visibleAgentTypes` excludes `ENTERPRISE`) | `utils/roleSelection.ts:44-47` | **HEAD** | Local Admin flow is genuinely new |
| F8 | Test identities hard-coded in requests: `user_code "99029899"`, `initiator_id "1234567891"`, `realsourceip "106.192.197.190"`, `org_id "26"` overriding the real org | `utils/roleSelection.ts`, `SelfOnboarding/Onboarding.tsx`, `executePipeline.ts`, `LoginPanel/VerifyOtp.jsx`, `src/lib/loginHelper.js`, `fetchOrgDetailsHelper.js` | **WIP only** | Must not ship. The backend must derive identity from the session, never from body fields. |
| F9 | `client_ref_id = Date.now()` (+ random in `fetcher`). A retry produces a new id, so this is **not** an idempotency key. | `src/lib/apiHelper.js:104-107`, `executePipeline.ts` | HEAD + WIP | Real `Idempotency-Key` header |
| F10 | `fetcher` multipart drops body fields (guard `typeof body !== "object"` is always false for objects) | `src/lib/apiHelper.js:207` | HEAD | Uploads should use presigned URLs instead |
| F11 | Token refresh returns `undefined` on success, so every 401 logs the user out; `.finally(setIsTokenUpdating(false))` runs immediately | `src/lib/loginHelper.js:364-408` | HEAD | Not onboarding-blocking; noted for the platform team |
| F12 | Inventory GraphQL client never created (inverted ternary) and a hard-coded `Authorization: "11205980"` | `src/lib/inventory/gql.ts:9,17` | HEAD | Unrelated to onboarding; security note |
| F13 | Landing page editor saves to localStorage only (TODO) | `LandingPageEditor.tsx:27` | HEAD | Branding beyond logo/colours is future scope |
| F14 | Org-name change, landing image and service toggles are support tickets | `AgreementSigning.jsx`, `ThemeConfig.jsx`, `ToggleServices` | HEAD | Post-activation change workflows are a gap (OQ-P9) |
| F15 | `.env.development` (WIP) points `NEXT_PUBLIC_API_BASE_URL` to `http://localhost:3004/ekoicici/v1/request`, the same port as `npm run dev.https` | `.env.development` | WIP | Local dev topology to settle for the new service (OQ-T6) |
| F16 | Mock admin uses `user_type "22"`, which is not in `UserTypes` | `__tests__/fixtures/session/admin-user.mock.ts` | HEAD | Clarify which user type a Local Admin gets (OQ-B2) |
