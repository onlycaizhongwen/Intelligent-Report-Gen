# Closure 287: UC-02 Higress Browser Acceptance

## Scope

- Requirement chain: `REQ-REPORT-001`, `REQ-REPORT-002`, `REQ-AUTH-001`
- Use case: `UC-02`
- Target slice: prove the customer-facing template report creation and outline confirmation browser flow works through the local Higress gateway, not only through direct Java backend access.
- Runtime route: browser -> Vite `/api/v1` proxy -> Higress `http://127.0.0.1:18000` -> Java report core.

## Result

UC-02 template browser acceptance now has repeatable Higress evidence:

- The browser opens `/reports/create`.
- The browser reads active templates through Higress.
- The browser submits template fields through Higress to create a template generation task.
- The browser displays task id, report id, template id, version, and submitted template snapshot evidence.
- The browser navigates to `/reports/{taskId}/outline`.
- The browser confirms the outline through Higress and displays the `retrieval` next stage.

P0 local smoke now includes a dedicated `uc02-report-template-higress-e2e` step. The existing direct-Java UC-02 E2E remains in place, so P0 can prove both direct backend behavior and gateway-routed browser behavior.

## Code Evidence

- `scripts/p0-local-smoke-lib.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`
- Existing browser flow: `frontend/web-console/tests/e2e/report-template-real-backend.spec.ts`

## TDD Evidence

RED:

```text
node --test tests/unit/node/p0_local_smoke_bundle.test.mjs

failed with:
6 !== 7
```

GREEN:

```text
node --test tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p2_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs

8 passed
```

Runtime browser evidence:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18000
REAL_BACKEND_FRONTEND_PORT=5177
npm run e2e:real-backend -- tests/e2e/report-template-real-backend.spec.ts

2 passed
```

## Remaining Gaps

- This closure proves UC-02 template creation and outline confirmation through Higress. It does not prove external LLM provider completion through Higress.
- OIDC login, TLS, WAF, and production K8s Gateway API behavior remain production hardening work.
- Browser-level `403/200` UI rendering through Higress for broader endpoint sets remains future hardening work.
