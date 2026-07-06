# Closure 295: UC-08 Higress Webhook Automatic Replay Exhaustion

Date: 2026-07-02

## Scope

- Requirement: `REQ-RULE-001`, `REQ-AUTH-001`
- User journey: `UC-08`
- Runtime path: browser -> Higress -> Java rule runtime -> webhook callback -> scheduled replay worker -> action compensation ledger

## Result

Webhook compensation now covers the automatic replay exhaustion path. A published rule can call a real webhook that keeps returning `503`, the Java replay worker performs the allowed asynchronous retry, then writes one terminal `compensation_exhausted` ledger record.

The browser acceptance verifies:

- The `/rules` page shows the failed `pending_retry` action.
- The scheduled replay worker exhausts the action automatically.
- The refreshed ledger shows `compensation_exhausted`.
- The terminal row displays `Source action #<original failed action id>`.
- The replay reuses the original webhook `Idempotency-Key`.

## Fix Details

- `RuleApplicationService` now preserves the original `sourceActionExecutionId` across retry chains and exhaustion records.
- `JdbcRuleRepository.findDueWebhookActionExecutions(...)` and the in-memory repository now scan only when the open action is the latest record for its idempotency chain. This prevents repeated `compensation_exhausted` rows after a terminal compensation record exists.
- Local dev configuration exposes the replay worker switch through `RULE_WEBHOOK_REPLAY_WORKER_ENABLED` and `RULE_WEBHOOK_REPLAY_WORKER_DELAY_MS`.
- P3 local smoke now includes a dedicated Higress-routed step named `uc08-rule-runtime-higress-replay-exhaustion-e2e`.

## Evidence

- RED: direct Java browser E2E initially failed waiting for `compensation_exhausted` with the original `sourceActionExecutionId`.
- RED: backend regression initially failed because the worker processed the same idempotency chain again after exhaustion.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=RuleApplicationServiceTest#webhookActionReplayWorkerExhaustsActionAfterMaxAsyncReplayAttempts test` passed `1/1`.
- GREEN: rebuilt Java Docker image `sha256:4787bd4d9091277583cfb56a9793af4a844f7b45e142e0465e129973e6286f0e`.
- GREEN: recreated `ir-java-smoke` with replay worker enabled and health returned `{"status":"UP"}`.
- GREEN: direct Java browser E2E on `http://127.0.0.1:18082` passed `automatic webhook replay exhaustion` `1/1`.
- GREEN: Higress browser E2E on `http://127.0.0.1:18000` passed `automatic webhook replay exhaustion` `1/1`.
- Regression: `npm run test -- src/api/apiContracts.test.ts` passed `30/30`.
- Regression: `.\mvnw.cmd -pl backend/java-report-core -Dtest=RuleApplicationServiceTest test` passed `88/88`.
- Regression: `node --test tests/unit/node/p3_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs` passed `6/6`.

## Remaining Gaps

- Full P3 smoke bundle execution remains a broader production-hardening run.
- OIDC login, TLS certificates, WAF behavior, and production K8s Gateway API resources still need production-environment validation.
