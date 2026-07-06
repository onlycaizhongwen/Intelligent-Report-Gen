# Delivery Production Gap Audit 253

Date: 2026-07-01

## Scope

This audit resumes the goal of iterating the intelligent report generation system toward a customer-production deliverable, using the prototype and requirement acceptance matrix as the control surface.

## Current P0-P3 Smoke Coverage

| Priority | Smoke entry | Covered UC | Evidence |
| --- | --- | --- | --- |
| P0 | `scripts/p0-local-smoke.mjs` | UC-01, UC-02, UC-04, UC-06 | Real provider worker/smoke, template filling real backend E2E, real backend upload parsing E2E, real backend export E2E |
| P1 | `scripts/p1-local-smoke.mjs` | UC-03, UC-09, UC-12 | Citation E2E, share E2E, share Postgres audit IT, audit E2E |
| P2 | `scripts/p2-local-smoke.mjs` | UC-10, UC-11, UC-14 | Collaboration, RBAC, report version real backend E2E |
| P3 | `scripts/p3-local-smoke.mjs` | UC-07, UC-08, UC-13 | Data source sync, approval inbox, rule runtime, approval delegate rules, dashboard real backend E2E |

## Remaining Production Gaps

| Gap | Current evidence | Risk | Next action |
| --- | --- | --- | --- |
| UC-01 model routing governance | Global candidate chain plus generation mode/template routing existed, but matrix still called out tenant/scenario/cost routing and stronger fallback governance | Customer production cannot explain why a model was selected for tenant or cost policy | Closed in this iteration with tenant/scenario/cost model route selection and audit payload persistence |
| UC-04 enterprise export governance | DOCX/PDF/PPT support exists, brand validation now covers PPTX, export status/audit persists normalized `brandSnapshot`, and backend enterprise export templates are now centrally managed with versions, enable/disable lifecycle, REST endpoints, and `templateVersion` export snapshots | Central template management UI and high-fidelity DOCX/PDF/PPT rendering remain limited | Closed snapshot auditability in delivery closure 254 and backend central template governance in delivery closure 255; next UC-04 work should focus on management UI or rendering fidelity |
| UC-06 production document parsing | Text/PDF/OCR fallback and Office/CSV support exist | High-precision OCR, complex table recovery, scanned layout reconstruction, and multi-column understanding remain production risks | Treat as larger dependency-heavy stream; avoid blocking smaller production closures |
| UC-02 template filling acceptance | Template schema API, backend validation, task/report persistence, and browser-visible template snapshot evidence are now covered | Downstream AI worker completion for template-generated reports still needs a full end-to-end smoke | Continue with template worker completion or external-provider controlled smoke when credentials are available |
| UC-08 rule designer/runtime | Many approval/template/topology closures exist; rule runtime contract schema and successful real-backend browser webhook ledger are now covered | Higher-order workflow constraints, browser-level failure compensation, and live Higress route checks still remain | Continue with failure compensation, gateway smoke, or customer-facing workflow acceptance slices |

## Implemented Closure

UC-01 now supports explicit model route governance by:

- `LLM_MODEL_ROUTE_TENANT_<KEY>`
- `LLM_MODEL_ROUTE_SCENARIO_<KEY>`
- `LLM_MODEL_ROUTE_COST_<KEY>`
- existing `LLM_MODEL_ROUTE_GENERATION_MODE_<KEY>`
- existing `LLM_MODEL_ROUTE_TEMPLATE_<KEY>`

Route priority is tenant, scenario, cost policy, generation mode, then template. The selected routing policy is returned by the Python provider, included in the worker completion payload, and merged into Java `model_invocations.parameters.routingPolicy` for audit queries.

## Verification

RED:

- `python -m pytest tests/unit/python/test_llm_provider.py -q` failed because tenant/scenario/cost routing still selected `gpt-4.1-mini`.
- `python -m pytest tests/unit/python/test_report_generation_worker.py -q` failed because `modelInvocation.routingPolicy` was missing.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#completesTaskFromControlledAiCallbackWithReferencesAndModelAuditSummary test` failed because `model_invocations.parameters.routingPolicy` was missing.

GREEN:

- `python -m pytest tests/unit/python/test_llm_provider.py tests/unit/python/test_report_generation_worker.py -q`: 15 passed.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#completesTaskFromControlledAiCallbackWithReferencesAndModelAuditSummary test`: 1 passed.

## Residual Risk

This closure does not prove a fresh full P0 smoke run because the real provider smoke requires an external `DASHSCOPE_API_KEY` and running local infrastructure. It does reduce UC-01 production governance risk and adds repeatable unit and Java service-level regression evidence.

## Follow-up Closure 254

UC-04 export brand snapshot governance was closed after this audit:

- DOCX, PDF, and PPTX now share enterprise brand-template validation.
- Enterprise exports persist `brandSnapshot` to `report_export_files.brand_snapshot`.
- Export audit details include the same normalized template snapshot.
- Frontend API typing exposes `ReportExportStatus.brandSnapshot`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_254_uc04_export_brand_snapshot.md`.

## Follow-up Closure 255

UC-04 enterprise export template governance was closed after closure 254:

- Added backend model, repository, migration, REST endpoints, and service methods for enterprise export templates.
- Enterprise export templates are versioned by `templateId + version` and can be enabled or disabled.
- Export creation can use a managed active enterprise template when `brand` is omitted.
- Export `brandSnapshot` includes `templateVersion` for managed-template exports.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_255_uc04_enterprise_export_template_governance.md`.

## Follow-up Closure 256

UC-04 enterprise export template frontend contract was closed after closure 255:

- Added web-console API types and methods for enterprise export template create/list/update/version-history/enable/disable.
- Frontend create/update requests now send backend-compatible `brand` payloads.
- Template `brandSnapshot` remains modeled as response/status/audit data and includes optional `templateVersion`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_256_uc04_enterprise_export_template_frontend_contract.md`.

## Follow-up Closure 257

UC-04 enterprise export template permission boundary was closed after closure 256:

- Enterprise template maintenance endpoints now require `report:template:manage` instead of ordinary `report:export`.
- Ordinary report export still uses `report:export`.
- Permission matrix grants template management to `system_admin` and `senior_analyst`, while keeping `analyst` limited to ordinary export.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_257_uc04_enterprise_export_template_permission_boundary.md`.

## Follow-up Closure 258

UC-05 report version permission boundary was closed after closure 257:

- Report version list and diff endpoints now require `report:read`.
- Report version rollback now requires `report:create`.
- Existing version list, diff, and rollback service behavior remains covered by targeted regression tests.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_258_uc05_report_version_permission_boundary.md`.

## Follow-up Closure 259

UC-09 collaboration permission boundary was closed after closure 258:

- Report annotation creation now requires `collaboration:write` at the REST boundary.
- Collaboration task status update now requires `collaboration:write` at the REST boundary.
- Permission matrix exposes `collaboration:write` for `system_admin`, `senior_analyst`, and `analyst`, while keeping `viewer` read-only.
- Existing collaboration ownership and assignee service guards remain covered by targeted regression tests.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_259_uc09_collaboration_permission_boundary.md`.

## Follow-up Closure 260

RBAC permission catalog and knowledge delete boundary were closed after closure 259:

- Knowledge item deletion now requires `knowledge:manage` at the REST boundary.
- Permission matrix now exposes controller-used `datasource:manage`, `audit:read`, `dashboard:read`, and `notification:read`.
- Role mappings keep `viewer` read-only, give `analyst` dashboard and notification access, and reserve data source management plus global audit for higher roles.
- Existing knowledge item deletion, citation protection, import, upload, and data source service behavior remains covered by targeted regression tests.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_260_rbac_permission_catalog_and_knowledge_delete_boundary.md`.

## Follow-up Closure 261

Controller security intent governance was closed after closure 260:

- Every Java REST controller endpoint must now declare `@RequiresPermission`, `@AuthenticatedEndpoint`, or `@PublicEndpoint`.
- Authenticated-only special flows are explicitly marked for `/history`, `/auth/me`, and generation task SSE streams.
- Public share-link flows are explicitly marked and remain limited to the existing share token, password, download policy, and risk-context controls.
- A source-level contract test now fails if future controller endpoints omit security intent metadata.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_261_controller_security_intent_contract.md`.

## Follow-up Closure 262

Share-link public endpoint method guarding was closed after closure 261:

- Public share-link JWT bypass now requires both a matching path and `POST` method.
- Spring Security `permitAll` for share-link access/report/download-url endpoints is restricted to `HttpMethod.POST`.
- Unexpected methods on public share-link paths now require a bearer token before downstream handling.
- The filter regression test covers GET attempts on the share-link public paths.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_262_share_public_endpoint_method_guard.md`.

## Follow-up Closure 263

MVC runtime security contract coverage was added after closure 262:

- Added MockMvc coverage for a protected permission endpoint through the real Spring Security filter chain and permission aspect.
- Missing bearer token now has repeatable local evidence for HTTP `401` and API `code=401`.
- Valid JWT without the endpoint permission now has repeatable local evidence for HTTP `403` and API `code=403`.
- Valid JWT with `permission:read` now has repeatable local evidence for HTTP `200` and permission matrix payload.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_263_mvc_security_runtime_contract.md`.

## Follow-up Closure 264

Disabled-account runtime guard was closed after closure 263:

- JWT tokens carrying `status=disabled` are rejected at the Java JWT filter boundary.
- Authenticated-only endpoints such as `/api/v1/auth/me` now get the same disabled-account protection as permission-protected endpoints.
- The regression first proved `/auth/me` returned `200` for a disabled token, then passed after moving the guard into `JwtAuthenticationFilter`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_264_disabled_account_runtime_guard.md`.

## Follow-up Closure 265

API contract controller coverage was closed after closure 264:

- Added a contract regression that scans every Java controller endpoint and fails if the route is absent from `docs/skill-chain/api_contract.md`.
- Added a backend-actual endpoint supplement for currently implemented routes that were missing from the API contract, including organization directory, enterprise export templates, report worker callbacks, approval templates, approval records, alerts, and action retry endpoints.
- This prevents future S4/API documentation drift from silently accumulating.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_265_api_contract_controller_coverage.md`.

## Follow-up Closure 268

S4 structured API JSON contract coverage was closed after closure 267:

- Added a structured JSON contract regression that scans every Java controller endpoint and requires a matching contract object in `api_contract.md` section `6` or `13.2`.
- Added an encoding-stable ASCII anchor for the backend structured supplement so local JVM/console encoding differences do not break lookup.
- Normalized no-body endpoints from `requestBody: null` to explicit structured `type=none` objects.
- Added structured contracts for public share report and share export download-url endpoints.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_268_api_contract_structured_backend_supplement.md`.

## Follow-up Closure 281

UC-08 rule runtime real-backend browser acceptance was closed after closure 280:

- Added a Playwright real-backend E2E that creates a published rule through the Java API, opens `/rules`, runs production execution from the browser, and verifies run history plus webhook action ledger output.
- The test starts a real local webhook receiver and verifies the Java backend sends exactly one callback with the expected idempotency key and body metadata.
- P3 smoke registration now includes `uc08-rule-runtime-real-backend-e2e`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_281_rule_runtime_real_backend_browser.md`.

## Follow-up Closure 282

UC-02 template filling real-backend browser acceptance was closed after closure 281:

- Added a Playwright real-backend E2E that reads active report-template schema from Java, fills every rendered field, submits the template task from `/reports/create`, and verifies browser-visible template snapshot evidence.
- The page now displays task id, report id, template id, template version, and submitted template parameters so customer acceptance does not require packet inspection.
- P0 smoke registration now includes `uc02-report-template-real-backend-e2e`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_282_uc02_template_filling_real_backend_browser.md`.

## Follow-up Closure 283

Higress local gateway real route smoke was closed after closure 282:

- Added a repeatable Node smoke that calls Higress on `http://127.0.0.1:18000`.
- Verified `/api/v1/roles/permission-matrix` returns the Java `401` envelope through the gateway.
- Verified `/api/v1/chat` does not expose a Python AI chat response and is controlled by the Java API boundary.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_283_higress_gateway_real_route_smoke.md`.

## Follow-up Closure 284

UC-02 template outline confirmation real-backend browser acceptance was closed after closure 283:

- After template task creation, `/reports/create` now exposes a `查看大纲` link using the returned `taskId`.
- `/reports/{taskId}/outline` now calls the real Java `PUT /api/v1/reports/generation-tasks/{taskId}/outline` endpoint before opening the generation stream.
- The browser displays `大纲已确认：{taskId}` and `下一阶段：retrieval`, proving the template flow reaches the backend lifecycle stage that triggers downstream generation.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_284_uc02_template_outline_confirmation_real_backend.md`.

## Follow-up Closure 285

UC-02 template completion controlled-worker smoke was closed after closure 284:

- Added `scripts/uc02-template-completion-smoke.mjs`, which uses the real Java backend to create a template task, confirm its outline, call the worker completion endpoint, and verify the completed report.
- The smoke also queries local PostgreSQL `model_invocations` through the existing `ir-postgres` container, proving model audit metadata is persisted for the template completion path.
- P0 smoke registration now includes `uc02-template-completion-smoke` after the UC-02 browser E2E.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_285_uc02_template_completion_controlled_worker_smoke.md`.

## Follow-up Closure 286

Higress endpoint-level security smoke was closed after closure 285:

- Extended `scripts/higress-gateway-smoke.mjs` so the local gateway at `http://127.0.0.1:18000` verifies representative Java RBAC outcomes, not only route ownership.
- The smoke now covers missing token `401`, valid JWT without `permission:read` `403`, and valid JWT with `permission:read` `200` for `GET /api/v1/roles/permission-matrix`.
- The same smoke still verifies `/api/v1/chat` is controlled by the Java authentication boundary and is not publicly exposed as Python chat.
- P2 local smoke now includes `higress-endpoint-security-smoke`, so the permission/RBAC delivery tier carries a gateway security check before browser RBAC scenarios.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_286_higress_endpoint_security_smoke.md`.

## Follow-up Closure 287

UC-02 Higress browser acceptance was closed after closure 286:

- Added a dedicated P0 local smoke step named `uc02-report-template-higress-e2e`.
- The step reuses the existing real-backend Playwright template-flow spec, but points `REAL_BACKEND_API_BASE_URL` and `REAL_BACKEND_ORIGIN` at local Higress instead of direct Java.
- Runtime evidence proves the browser can submit template fields, display persisted template snapshot evidence, navigate to outline confirmation, and confirm the outline through `http://127.0.0.1:18000`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_287_uc02_higress_browser_acceptance.md`.

## Follow-up Closure 288

UC-11 Higress RBAC browser acceptance was closed after closure 287:

- Added a dedicated P2 local smoke step named `uc11-higress-rbac-e2e`.
- The step reuses the existing real-backend Playwright user/RBAC spec, but points `REAL_BACKEND_API_BASE_URL` and `REAL_BACKEND_ORIGIN` at local Higress instead of direct Java.
- Runtime evidence proves the gateway-routed browser flow can batch-import users, disable an account, and reject disabled-account access to `/admin/users` without exposing the disabled user's row.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_288_uc11_higress_rbac_browser.md`.

## Follow-up Closure 289

UC-04 Higress export browser acceptance was closed after closure 288:

- Added a dedicated P0 local smoke step named `uc04-higress-export-e2e`.
- The step reuses the real-backend report export Playwright spec, but points `REAL_BACKEND_API_BASE_URL` and `REAL_BACKEND_ORIGIN` at local Higress instead of direct Java.
- Runtime evidence proves the browser can export PDF and PPTX through `http://127.0.0.1:18000`, receive Java-controlled download metadata, and download the real MinIO artifact through a correctly signed public presigned URL.
- The report detail UI now preserves page state and exposes a controlled `下载导出文件` link after export completion.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_289_uc04_higress_export_browser.md`.

## Follow-up Closure 290

UC-04 enterprise export template management UI was closed after closure 289:

- Added the `Enterprise export templates` web console page and navigation entry.
- The page supports managed template list, create, update-as-new-version, disable, enable, and version-history inspection.
- Added a mock Playwright management flow and a real-backend browser flow.
- The real-backend browser flow passed both direct Java and Higress-routed execution.
- P0 local smoke now includes `uc04-enterprise-export-template-higress-e2e`.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_290_uc04_enterprise_export_template_ui.md`.

## Follow-up Closure 291

UC-04 managed export template selection was closed after closure 290:

- Report detail now loads active managed enterprise export templates.
- Users can select `已治理企业模板` before exporting.
- The selected template snapshot is previewed on the export panel.
- Managed-template export sends only `format + templateId`, so Java resolves the governed `brandSnapshot`.
- Manual inline-brand export remains covered by the existing report detail regression.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_291_uc04_managed_template_export_selection.md`.

## Follow-up Closure 292

UC-04 managed template Higress export was closed after closure 291:

- Added a real-backend browser export path that creates a managed enterprise template, creates and completes a report, selects the managed template, and exports PDF.
- Verified the browser request sends only `format + templateId` without inline `brand`.
- Verified Java returns the governed `brandSnapshot.templateId` and `templateVersion`.
- Verified the controlled download URL resolves and MinIO returns a non-empty artifact.
- The full Higress-routed UC-04 export real-backend spec now passes PDF, PPTX, and managed-template PDF paths.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_292_uc04_managed_template_higress_export.md`.

## Follow-up Closure 293

UC-08 Higress webhook failure compensation was closed after closure 292:

- Added a real-backend browser flow where a published rule calls a real webhook endpoint that returns `503 erp unavailable`.
- The `/rules` page now refreshes the webhook action ledger after failed production runs, so pending compensation is visible without manual reload.
- The ledger now renders the concrete webhook failure reason and supports selecting the failed row for batch ignore.
- Added a dedicated P3 smoke step named `uc08-rule-runtime-higress-compensation-e2e`.
- Verified the same failure-compensation flow through direct Java and local Higress.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_293_uc08_higress_webhook_failure_compensation.md`.

## Follow-up Closure 294

UC-08 Higress webhook retry compensation was closed after closure 293:

- Added a real-backend browser flow where a published rule calls a webhook that first returns `503 erp unavailable`, then succeeds on manual retry.
- The `/rules` page now supports row-level retry from the webhook action ledger and renders source action traceability for compensation rows.
- Java retry success, retry failure, replay exhaustion, and manual ignore records now preserve `sourceActionExecutionId` in metadata and expose it in `listActionExecutions(...)`.
- The retry path reuses the original webhook `Idempotency-Key`, protecting downstream ERP/OA/finance callbacks from duplicate side effects.
- The Docker smoke Java container was refreshed to the new image and the same retry acceptance flow passed through direct Java and local Higress.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_294_uc08_higress_webhook_retry_compensation.md`.

## Follow-up Closure 295

UC-08 Higress webhook automatic replay exhaustion was closed after closure 294:

- Added a real-backend browser flow where a published rule calls a webhook that always returns `503 erp unavailable`.
- The Java scheduled replay worker is enabled only for local smoke validation through `RULE_WEBHOOK_REPLAY_WORKER_ENABLED=true` and keeps the default disabled.
- The automatic replay chain now preserves the original `sourceActionExecutionId` when it writes `compensation_exhausted`.
- Due-replay scanning now stops once the idempotency chain has a later terminal row, preventing duplicate `compensation_exhausted` records.
- P3 local smoke now includes `uc08-rule-runtime-higress-replay-exhaustion-e2e`.
- The same automatic exhaustion flow passed through direct Java and local Higress.

Verification evidence is recorded in `docs/skill-chain/delivery_closure_295_uc08_higress_webhook_replay_exhaustion.md`.
