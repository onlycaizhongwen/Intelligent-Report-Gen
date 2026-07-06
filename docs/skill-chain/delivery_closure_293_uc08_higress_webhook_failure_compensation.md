# Closure 293: UC-08 Higress Webhook Failure Compensation

- Closure date: 2026-07-02
- Requirement chain: `REQ-RULE-001`, `REQ-AUTH-001`
- User journey: `UC-08` rule designer/runtime workflow
- Runtime path: browser -> Higress -> Java rule runtime -> webhook callback -> action compensation ledger

## Outcome

The rule runtime page now supports a customer-visible webhook failure compensation loop:

- A published rule can execute a real webhook action whose callback returns `503`.
- The browser shows the production run failure and refreshes the `Webhook action ledger` even when the run request throws.
- The ledger displays the failed action, `pending_retry`, the concrete failure reason, the compensation summary, and selectable compensatable rows.
- The user can batch-ignore the failed action from the page, and the ledger then shows `compensation_ignored`.
- The same browser acceptance path passes through local Higress at `http://127.0.0.1:18000`.

## Code Evidence

- `frontend/web-console/src/pages/rules/RuleDesigner.vue`
  - Refreshes run history, approval records, and webhook action executions after failed production runs.
  - Displays `execution.errorMessage` directly in the webhook action ledger.
- `frontend/web-console/tests/e2e/rule-runtime-real-backend.spec.ts`
  - Adds a real-backend failure compensation browser flow with a local webhook receiver returning `503 erp unavailable`.
  - Verifies batch ignore and backend ledger state.
- `scripts/p3-local-smoke-lib.mjs`
  - Adds `uc08-rule-runtime-higress-compensation-e2e`.
- `tests/unit/node/p3_local_smoke_bundle.test.mjs`
  - Locks the updated P3 smoke command sequence.

## Verification Evidence

- RED: `RUN_REAL_BACKEND_E2E=true ... npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts -g "failed webhook compensation"` first failed because the page did not expose a ledger row after the failed production run.
- GREEN direct Java: the same command with `REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1` passed `1/1`.
- GREEN Higress: the same command with `REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1` passed `1/1`.
- Regression: `npm run typecheck` passed.
- Regression: `npm run test -- src/api/apiContracts.test.ts` passed `30/30`.
- Smoke contract: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs` passed `6/6`.

## Remaining Gaps

- UC-08 still needs broader gateway authorization coverage across all rule runtime endpoints.
- OIDC/TLS/WAF behavior remains production hardening work.
- Customer-facing workflow acceptance can still expand to retry-after-failure and automatic replay exhaustion paths.
