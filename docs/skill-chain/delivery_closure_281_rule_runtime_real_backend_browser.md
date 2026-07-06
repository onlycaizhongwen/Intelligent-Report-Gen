# Closure 281: Rule Runtime Real Backend Browser Acceptance

## Scope

- Requirement chain: `REQ-RULE-001`
- Use case: `UC-08`
- Target slice: browser-level rule runtime acceptance against the real Java backend.
- Runtime route: Vue rule page -> Java API at `http://127.0.0.1:18082/api/v1` -> PostgreSQL-backed rule persistence -> production rule execution -> real HTTP webhook callback -> action ledger UI.

## Result

The rule runtime path is now covered by a real-backend Playwright acceptance test. The browser can open `/rules`, select a rule created through the real API, run the published rule in production mode, display the production run result, show the persisted run history, render the webhook action ledger, and expose compensation summary metrics.

The test also starts a local HTTP webhook receiver and verifies that the Java backend sends one real callback with:

- `Idempotency-Key=rule-{ruleId}-run-{runId}-node-notifyFinance`
- body metadata including `eventType`, `ruleId`, `ruleName`, and `nodeId`

## Evidence

- Browser E2E: `frontend/web-console/tests/e2e/rule-runtime-real-backend.spec.ts`
- P3 smoke bundle entry: `scripts/p3-local-smoke-lib.mjs`, step `uc08-rule-runtime-real-backend-e2e`
- Requirement matrix: `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## TDD Evidence

RED:

```text
RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts

failed while waiting for production result
Java backend alert: Failed to connect to /127.0.0.1:<port>
```

Root cause:

The webhook server originally advertised `127.0.0.1`, which was reachable from the Playwright worker process but not from the Java backend runtime boundary.

GREEN:

```text
RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts

1 passed
```

Fix:

- Bind the webhook test server to `0.0.0.0`.
- Advertise `REAL_BACKEND_WEBHOOK_HOST` when supplied.
- Default the callback host to `host.docker.internal` for local container-backed Java smoke runs.

Regression:

```text
npm run typecheck

vue-tsc --noEmit
```

The P3 smoke step list now includes:

```text
uc08-rule-runtime-real-backend-e2e
```

## Remaining Gaps

- This closure covers the successful webhook action ledger path; browser-level failure compensation against the real backend remains a separate hardening slice.
- Live Higress route smoke was not executed in this closure.
- The full P3 smoke bundle was not rerun here; only the new real-backend E2E and step-list registration were verified.
