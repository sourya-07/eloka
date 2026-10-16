# EPS wire-format contract (what `server/` must answer)

The client (`client/`) was written against EPS. `server/` answers the same requests so the client works unchanged (ADR-0004). This file is the checklist. The executable version is `client/mock-eps/test/` (run against the server with `EPS_CONTRACT_BASE_URL`).

Status: ✅ implemented in `server/` · ⬜ not yet · ➖ out of scope (runs in the EkoConnect widget or a later release)

## Transport rules

| Rule | Detail |
|---|---|
| Entry points | `POST /` (proxied by `client/pages/api/transaction.js`), `POST /transactions/do`, `/transactions/dojson`, `/transactions/wlc`, `/transactions/upload`, `/transactions/customupload` (POST/PUT), `/transactions/getip`, `/authentication/{revoke,token,refresh-profile}`, any method under `/ekoicici/**`, `POST /reports/agent-subnetwork` |
| Envelope | `{status, response_status_id, response_type_id, message, data, param_attributes?, invalid_params?}`. Success = `status: 0` and `response_status_id: 0` |
| Interaction | body field `interaction_type_id` (string or number) |
| tf-req tunnel | headers `tf-req-uri-root-path` (`/ekoicici/v1`, `/v2`, `/v3`, `/v1/marketuat`), `tf-req-uri` (may carry its own query string), `tf-req-method`. Through the proxy it arrives as a real request to `root + uri` with body fields as query params (empty strings dropped) |
| Bodies | JSON, URL-encoded, or multipart with a URL-encoded field `form-data` (upload proxy) or `formdata` (direct) plus file parts |
| Identity | `user_code`, `initiator_id`, `org_id`, `bc` in bodies are **ignored**. Identity = the bearer token. Public calls (515, 518, 151 before login, 677) identify the org by `org_token` |
| Errors | Business failure → HTTP 200 + `status: 1` envelope. Not authenticated → HTTP 401 + JSON envelope. Every response is JSON (except `/reports/agent-subnetwork`) |
| Files | `{file: {blob: <base64>, name, "content-type"}}`, always (the proxy drops `tf-is-file-download`) |

## Release 1 interactions

### Login, session, org (P1)

| Key | Screen | Request (relevant fields) | Response read by the UI | Status |
|---|---|---|---|---|
| 677 (to `ORG_META_SERVICE`) | every page (server side) | `sub_domain` or `domain` (URL-encoded), `secret` | `data{org_id, app_name, org_name, logo, metadata{theme, support_contacts, login_meta, disabled_features, onboarding, user_type_labels, user_code_labels, cms_meta, cms_data}, login_types}`, `org_token`; not found = `response_type_id 1829` | ✅ |
| 515 send OTP | login | `mobile`, `org_token` | `status 0`, `data{mobile, otp_length[, otp in local only]}`; limit = `response_type_id 2377` + `data.description` (minutes) | ✅ |
| 518 verify OTP | login | `mobile`, `otp`/`id_token`, `org_token` | `response_type_id 1541`, `data.otp_verification_token`; wrong = top-level `otpFailed: true`; blocked = `accountInactive: true` | ✅ |
| 151 profile | login, signup refresh | `mobile` + Bearer (FE-6) | `response_type_id 369`, `data{user_detail, personal_detail, shop_detail, account_detail}`; new user = not 369 | ✅ |
| `/authentication/revoke` | logout | Bearer (FE-2) or `refresh_token` | `status 0` always | ✅ |
| `/authentication/token` | token refresh | `refresh_token` | `{access_token, refresh_token, …}`; invalid → **HTTP 200** + fail | ✅ |
| `/authentication/refresh-profile` | profile cards, widget | Bearer, `last_refresh_token` | `access_token, refresh_token, details, personal_details, shop_details, account_details` (`{csp_id}`: assisted onboarding status, P5) | ✅ |
| `/transactions/getip` | Dropzone watermark | — | `ip` | ✅ |
| 774 | Configurations → Theme | `orgDetails.metadata[{name:"theme", type:"json", value}]` | `status 0` | ✅ |

### Homes and dashboards (P2)

| Key | Screen | Response read | Status |
|---|---|---|---|
| `/transactions/wlc` | menu | top-level array of `{id, label, is_visible, behavior, icon, history_label, interaction_type_id, description, …}` | ✅ |
| 9 | wallet balance | `data.balance` | ✅ |
| 10010 / 10012 / 10023 | notifications | `data.notifications[]` / ack | ✅ |
| 623 | earning summary | `data{this_month, last_month, prev_month, commission_due}` | ✅ |
| 154 (recent) | home | `data.transaction_list[]` | ✅ |
| tf GET `/network/agent/pricing_commission` | commissions | `response_status_id 0`, `data.pricing_commission_data[]` (empty until pricing rules exist, P4) | ✅ |
| 818 | dashboard | `data.dashboard_object.totalActiveData{<type>:{activecount,totalcount}}` | ✅ |
| 1044 | dashboard filter | `param_attributes.list_elements[{label, tx_typeid}]` | ✅ |
| 682 | business dashboard | `data.dashboard_object.{products_overview{}, successRate{}, mostUsedServices{}, verificationTrends[], gtv_top_merchants[]}` (verificationTrends stays `[]`: no verification products yet) | ✅ |
| 817 | onboarding dashboard: joined per user type | `data.onboarding_funnel[0]{<type>:{totalCount}}` | ✅ |
| 683 / 727 | onboarding dashboard: funnel cards and table | `data.onboarding_funnel[]{status_ids…}`, `totalRecords`; 727 pages by `account_status` (stage ids 10/11/12) | ✅ |

### Network, history, profile (P3)

| Key | Screen | Status |
|---|---|---|
| tf GET `/network/agents` (paging + filters, `search_value` → 1827, `document=1`) | My Network, agent profile (KYC images with `document=1`: `cspDetails` still empty, follow-up) | ✅ |
| tf GET `/network/agent-list` (`usertype`, `user_id`) | network users, change role | ✅ |
| dojson tf POST `/network/agents/updateStatus` → 1831 | mark active/inactive | ✅ |
| tf PUT `/network/agents/profile/changeRole/{upgrademerchant 1842, promotecsps 1967, transfercsps 1872}` | change role | ✅ |
| dojson tf POST `/network/agents/profile/personalInfo/update`, `/address/update` | edit agent | ✅ |
| dojson tf POST `/network/agent` (delete demo) | demo users | ✅ |
| 792 | download agreement (answers "not available yet" until a real e-sign provider stores signed PDFs) | ⬜ |
| tf GET `/network/agents/transaction_history[/recent_transaction[/account_statement]]` | network statement (legacy) | ✅ |
| tf GET `/network/agents/ids` | notification creator (page not in the menu) | ➖ |
| 154 (`isNetworkTransactionHistory`, `account_id`, filters, paging 25) | history, network transactions | ✅ |
| 183 export (`reporttype` xlsx / pdf / csv) | history export | ✅ |
| `POST /reports/agent-subnetwork` (needs the session token: FE-4, done) | network export (xlsx / pdf) | ✅ |
| 401 (personal / shop) | profile | ✅ |
| 373 | account summary GSTIN | ✅ |

### Settings, pricing, onboard agents, support (P4)

| Key | Screen | Status |
|---|---|---|
| 737 (operation 0/2/3/4/5) | business settings toggles | ✅ |
| 726 | commission frequency, distributor pricing | ✅ |
| tf v2 `/network/agreement/recent-signature`, customupload `/network/agreement/upload-doc-sign-config` | agreement signing | ✅ |
| 553 / 554 / 555 (`intent_id 16`) | email OTP | ✅ |
| 353 | pincode lookup | ✅ |
| 837 / 1046 / 838 / 756 | pricing config | ✅ |
| dojson tf POST `/network/agent/multiple_onboarding` (+ demo) | onboard agents | ✅ |
| customupload tf POST `/network/agent/bulk_onboarding` | onboard via file (.xlsx or .csv: Mobile, Name, Distributor Mobile; old .xls rejected with a clear message) | ✅ |
| 692 / 10016 / 10017 / 10018 / 10020 | query center | ✅ |
| 10022 / 10000 (JSON or multipart `/transactions/upload`) | raise issue | ✅ |

### Agent onboarding (P5)

| Key | Step | Status |
|---|---|---|
| 521 | role selection (1566); assisted needs the agent's consent OTP first (`CONSENT_REQUIRED`) | ✅ |
| 523 multipart `doc_type 1/2/3/7` | Aadhaar, PAN (`doc_id`, duplicate = `PAN_ALREADY_REGISTERED`), selfie, passbook (1569) | ✅ |
| 298 | selfie location (1076) | ✅ |
| 522 | business details (1567) | ✅ |
| tf POST `/karza/digilocker-redirection-url` + 552 | DigiLocker (1621); local/test use a fake consent page (`/v1/dev/digilocker`) | ✅ |
| 454 / 155 | bank (1445) / bank list (155 done in P3) | ✅ |
| 170 / 10005 / 5 | secret PIN (1646 / one-time PinTwin key / 9); weak PINs rejected, stored as BCrypt | ✅ |
| 287 / 293 | agreement (1069 with the local fake e-sign / 1615); production needs an e-sign provider | ✅ |
| 519 / 387 | shop types / states (done in P3, used by the profile) | ✅ |
| 194 / 197 / refresh-profile `{csp_id}` | assisted onboarding (873 OTP sent / 874 already exists; 876 ok / 302 wrong OTP) | ✅ |

### Out of scope for Release 1

➖ EkoConnect widget flows (`/transaction/:id`: DMT, AePS, BBPS, invoices 542, manage account 536, e-value 491/240), DigiKhata (`/ekoicici/v3/customer/...`), KYC products (1041–1043, `/tools/kyc/*`), BBPS (280/63), payments (384/780/…), bulk payment, merchant/reverse e-value, inventory, Ask AI (`/gpt/*`, `/copilotkit`), legacy `/admin/pricing/[slug]` forms.
