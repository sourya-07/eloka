# ELOKA Frontend Transformation — Open Questions

> Status: open unless marked **CLOSED**. Business and backend questions for onboarding stay in [../onboarding/OPEN_QUESTIONS.md](../onboarding/OPEN_QUESTIONS.md); this file covers the frontend programme only.
> Related: [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) · [TODO.md](./TODO.md)

## Closed (2026-10-21, owner)

| ID | Question | Answer |
|---|---|---|
| FQ-C1 | What should the first build cover? | Foundation first, then onboarding (applicant + staff), then the Release 1 reskin |
| FQ-C2 | How should Stitch designs become code? | Hand-port to Chakra. No Tailwind or shadcn. |
| FQ-C3 | Which brand do the platform screens use? | The existing neutral layer (`#0A0A0A` CTA, `#FAFAF7` canvas) with Connect Blue `#11299E` as the accent |

## Needs owner approval

| ID | Question | Why it matters | Recommendation | Blocks |
|---|---|---|---|---|
| FQ-1 | New dev dependencies: `jest-axe`; Playwright (`@playwright/test` + `@axe-core/playwright`) for E2E; optionally `puppeteer` for a `code-to-design` baseline of current pages | Automated a11y and E2E. Without them, these checks are manual. | Approve `jest-axe` and Playwright; skip puppeteer unless the reskin needs a baseline | QA-1, APP-T / STF-H automation, ST-6d baseline |
| FQ-2 | Platform-host config `WLC_PLATFORM_HOSTS=partners.<root>,staff.<root>`. Also: the production zone (OQ-T4: `finstore.app` vs `elokademo.in`), and whether staff is VPN-only (OQ-S1) | DS-5 platform mode, DNS, TLS allow-list (`/v1/system/tls-allowed`) | Env-driven list; zone decided by ops | Production rollout, not development |
| FQ-3 | OQ-P2: where does the onboarding UI live? This programme follows BACKEND_PLAN: the same Next app, as route groups `pages/partner/*` and `pages/staff/*`. Mark OQ-P2 as decided? | Avoids a second app; reuses components | Mark decided | none (we proceed per BACKEND_PLAN) |
| FQ-4 | After the reskin, should the tenant CTA be the tenant primary (contrast-safe `brand.solid`) or the platform black CTA? This also decides the `Button` default variant. | The white-label look of every tenant | Tenant primary through `brand.*` | R1-01 |
| FQ-5 | Add springdoc OpenAPI to the server so FE-R2 types can be generated (BACKEND_PLAN mentions it) | Hand-written types can drift. This is a backend change, so it needs separate approval. | Yes, later; hand-written types until then | none |
| FQ-6 | Bugs found but not in scope: (a) `hooks/useRefreshToken.js` may drop the async result of `generateNewAccessToken`, so a forced refresh may log the user out (needs verifying); (b) sibling files under `pages/` are routable (`pageExtensions` not set); (c) hard-coded `org_id "26"`, `user_code`, `initiator_id` in the 518/151/677 payloads; (d) security headers off unless `NEXT_PUBLIC_ENABLE_SECURITY_HEADERS=true` | Correctness and security | Fix in separate, focused changes | none |
| FQ-7 | Languages (OQ-P13): English only for now? | Copy structure | English now; copy kept in constants | none |
| FQ-15 | How should the FE-R2 REST calls (`/v1/onboarding`, `/v1/admin`) reach the server? `client/CLAUDE.md` says browser code must go through `pages/api/transaction.js`, but that proxy returns 200 for every error except 401 (EPS style), and FE-R2 needs real 412/409/422/423/428 statuses and headers (`ETag`, `If-Match`, `Idempotency-Key`, `X-Reauth-Token`). About 78 existing files already call the API host directly. | Decides APP-A and STF-A | Either a new pass-through proxy `pages/api/v1/[...path].ts` that keeps status codes and headers and forwards `X-Forwarded-For` / `X-Request-Id` (same rules as `proxyHelper`), or direct calls to `NEXT_PUBLIC_API_BASE_URL` with CORS. Recommended: the pass-through proxy, which keeps the documented rule. | APP-A |
| FQ-8 | Login shows a hard-coded "Secured by DemoPortal" (`page-components/LoginPanel/Login/Login.jsx`). What should it say? | Tenant branding | Remove it, or use the org's name | R1-02 |

## Backend or contract gaps found while planning (not frontend work)

| ID | Gap | Frontend workaround | Owner |
|---|---|---|---|
| FQ-9 | There is no endpoint that returns the current consent, terms and declaration **text and versions**. The versions are only in server config (`eloka.onboarding.*-version`) and in the `CONSENT_VERSION_STALE` problem body. The text owner is OQ-C5. | Keep the text and versions in `features/la-onboarding/constants.ts` (env-overridable). On `CONSENT_VERSION_STALE`, read the versions from the problem body and ask again. | Backend + compliance |
| FQ-10 | The `required-documents` list per entity type is not exposed by the API (only `missing_documents` on a 409) | Mirror the current config in constants and treat the 409 list as the authority | Backend |
| FQ-11 | Reason codes are free strings (withdraw `reason_code`; staff correction / reject / hold `reason_code` ≤ 40). There is no agreed list. | Propose lists in constants, e.g. withdraw: `CHANGED_MIND`, `CHOSE_ANOTHER_PROVIDER`, `NOT_READY`, `OTHER`. The owner confirms. | Product + ops |
| FQ-12 | P9 provisioning is not built, so an approved application has no next step | APP-18 shows "We're setting up your platform"; no activation UI | Backend (P9) |
| FQ-13 | `purposes[]` for consent is free-form (the tests use `"KYC"`) | Use a fixed list from constants pending compliance (OQ-C5) | Compliance |
| FQ-14 | S21 audit is served at `/v1/admin/applications/{id}/audit`, while the spec says `/v1/admin/audit?entity_type=&entity_id=` | Use the implemented path | Backend (doc fix) |
| FQ-16 | **DigiLocker-only path (owner request 2026-10-21).** The backend requires all 13 steps and has no "path" field. A DigiLocker-only path needs answers to four questions: (1) can business PAN, business details and documents be taken from DigiLocker data instead of PAN verification (A12), the business-details form and uploads; (2) which documents DigiLocker can supply for each entity type (an incorporation certificate is not always in the signatory's DigiLocker); (3) is a live photo still needed; (4) does compliance (OQ-C1, OQ-C5) accept DigiLocker as the only KYC source. | Designs only (APP-01B, APP-22). No build until answered. | Product + compliance + backend |

## Assumptions in use (replace when answered)

| ID | Assumption |
|---|---|
| FA-1 | Applicants mostly use phones, so applicant screens are mobile-first. Staff use desktops, so staff screens are desktop-first and only usable read-only on tablets. |
| FA-2 | Approved Stitch designs (HTML + PNG) are committed under `client/.stitch/designs/` as the visual spec. They are small, but if they get large we switch to keeping only PNGs. |
| FA-3 | Staff sign-in uses Google Identity through the existing `@react-oauth/google`. The client id comes from env and the server checks the domain. |
| FA-4 | Applicant tokens live in sessionStorage (like the main app), not cookies. ADR-0005 deviation noted in P7. |
