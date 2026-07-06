# Closure 294: UC-08 Higress Webhook Retry Compensation

- Closure date: 2026-07-02
- Requirement chain: `REQ-RULE-001`, `REQ-AUTH-001`
- User journey: `UC-08` rule designer/runtime workflow
- Runtime path: browser -> Higress -> Java rule runtime -> webhook callback -> manual retry -> action compensation ledger

## Outcome

The UC-08 webhook compensation loop now covers the customer-facing retry path after a failed production webhook:

- A published rule can call a real webhook endpoint that first returns `503 erp unavailable`.
- The browser shows the failed webhook action as `pending_retry`.
- The user can click the row-level retry action from `/rules`.
- A successful retry creates a succeeded action ledger item while preserving the original `sourceActionExecutionId`.
- The retry reuses the original webhook `Idempotency-Key`, preventing duplicate downstream side effects.
- The browser shows the retry result, including `Succeeded 1` and `Success rate 50%`.
- The same retry acceptance path passes through local Higress at `http://127.0.0.1:18000`.

## Code Evidence

- `backend/java-report-core/src/main/java/com/company/report/rule/application/RuleApplicationService.java`
  - Persists `sourceActionExecutionId` for retry success, retry failure, replay exhaustion, and manual ignore compensation records.
  - Exposes `sourceActionExecutionId` in `listActionExecutions(...)` response data.
- `backend/java-report-core/src/test/java/com/company/report/rule/application/RuleApplicationServiceTest.java`
  - Verifies manual retry keeps source action traceability in the returned ledger item and audit detail.
- `frontend/web-console/src/pages/rules/RuleDesigner.vue`
  - Adds `sourceActionExecutionId` to the action ledger item type and displays source action evidence in the ledger.
- `frontend/web-console/tests/e2e/rule-runtime-real-backend.spec.ts`
  - Adds the real-backend browser retry flow for failed webhook compensation.
  - Verifies webhook request count, idempotency key reuse, browser summary, and backend `sourceActionExecutionId`.
- `scripts/p3-local-smoke-lib.mjs`
  - Broadens the UC-08 P3 smoke grep to `webhook compensation`, so the smoke includes both failure/ignore and retry-success acceptance paths.

## Verification Evidence

- RED: the first direct Java browser E2E failed because `GET /api/v1/rules/{ruleId}/action-executions` returned the succeeded retry item with `sourceActionExecutionId=null`.
- RED: `RuleApplicationServiceTest#manualRetryReplaysPendingWebhookActionExecutionAndUpdatesLedger` failed with expected `sourceActionExecutionId=1`, actual `null`.
- GREEN targeted Java: `.\mvnw.cmd -pl backend/java-report-core -Dtest=RuleApplicationServiceTest#manualRetryReplaysPendingWebhookActionExecutionAndUpdatesLedger test` passed `1/1`.
- Docker refresh: `ir-java-smoke` was recreated with image `sha256:cad7e1e5c144b7416c68b898bd63a5b06b4510ee65201b7abbdb679b9af2be69`; `http://127.0.0.1:18082/actuator/health` returned `{"status":"UP"}`.
- GREEN direct Java container: with `REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1`, `npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts -g "webhook compensation"` passed `2/2`.
- GREEN Higress: with `REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1`, the same Playwright command passed `2/2`.
- Frontend contract regression: `npm run test -- src/api/apiContracts.test.ts` passed `30/30`.
- Backend regression: `.\mvnw.cmd -pl backend/java-report-core -Dtest=RuleApplicationServiceTest test` passed `88/88`.
- Smoke contract: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs` passed `6/6`.

## Remaining Gaps

- UC-08 still needs automatic replay exhaustion browser acceptance, including retry limit and operator-visible terminal state.
- Broader rule-runtime endpoint authorization through Higress remains open beyond the representative gateway checks.
- OIDC/TLS/WAF behavior remains production hardening work.
