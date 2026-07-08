# Delivery Closure 363: Rule Webhook Manual Retry Replay Race

Date: 2026-07-08

## Scope

- Requirement: `REQ-RULE-001`
- Surface: rule runtime webhook action compensation
- Boundary: browser manual retry, automatic webhook replay worker, Java rule repository queries

## Result

Manual-only webhook compensation is now separated from the automatic replay worker. When a webhook action sets `maxAsyncReplayAttempts` to `0`, the scheduled replay worker skips the action instead of leasing and retrying it in the background. This prevents duplicate successful retry ledger rows racing against a browser-triggered manual retry.

The repository due-action queries also exclude disabled automatic replay actions, keeping in-memory and JDBC behavior aligned.

## Evidence

- RED: `./mvnw -pl backend/java-report-core -Dtest=RuleApplicationServiceTest#webhookActionReplayWorkerSkipsActionsWhenAsyncReplayIsDisabled test` failed with `processed=1` instead of `0`.
- GREEN: the same targeted Java regression passed.
- Regression: replay worker Java regression command passed for the existing replay, retry-pending, disabled, exhaustion, and metric paths.
- Browser symptom: `npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts -g "browser retries failed webhook compensation successfully"` passed against the real Java backend.
- Full local P3: `node scripts/p3-local-smoke.mjs` passed all 10 steps.
- Full local delivery: `node scripts/delivery-local-smoke.mjs` passed P0, P1, P2, and P3.
- Browser entrypoint: `http://127.0.0.1:5173/` returned HTTP `200`.

## Remaining Risk

This closure proves the local Java/browser/Higress compensation path. Production readiness still depends on the existing external gates for WAF blocking, trusted TLS, and customer OIDC evidence.
