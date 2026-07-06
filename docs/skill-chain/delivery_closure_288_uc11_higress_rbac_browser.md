# Closure 288: UC-11 Higress RBAC Browser Acceptance

## Scope

- Requirement chain: `REQ-AUTH-001`
- Use case: `UC-11`
- Target slice: prove the user and RBAC browser acceptance flow works through local Higress, including disabled-account rejection on a management page.
- Runtime route: browser -> Vite `/api/v1` proxy -> Higress `http://127.0.0.1:18000` -> Java report core.

## Result

UC-11 now has a repeatable Higress-routed browser check:

- An admin JWT calls `POST /api/v1/users/batch-import` through Higress to create analyst/viewer users.
- The same gateway path disables one imported user through `PUT /api/v1/users/{userId}/status`.
- The browser opens `/admin/users` with a disabled-account JWT.
- The page receives the Java security rejection through Higress and shows the no-permission message.
- The disabled user's row is not exposed on the management page.

P2 local smoke now includes `uc11-higress-rbac-e2e` after the direct-Java UC-11 E2E, so the RBAC tier verifies both direct backend behavior and gateway-routed browser behavior.

## Code Evidence

- `scripts/p2-local-smoke-lib.mjs`
- `tests/unit/node/p2_local_smoke_bundle.test.mjs`
- Existing browser flow: `frontend/web-console/tests/e2e/user-rbac-real-backend.spec.ts`

## TDD Evidence

RED:

```text
node --test tests/unit/node/p2_local_smoke_bundle.test.mjs

failed with:
4 !== 5
```

GREEN:

```text
node --test tests/unit/node/p2_local_smoke_bundle.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs

8 passed
```

Runtime browser evidence:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18000
REAL_BACKEND_FRONTEND_PORT=5178
npm run e2e:real-backend -- tests/e2e/user-rbac-real-backend.spec.ts

1 passed
```

## Remaining Gaps

- This closure verifies disabled-account browser rejection through Higress. It does not cover OIDC session revocation or browser login through Higress.
- TLS, WAF, production K8s Gateway API resources, and broader endpoint-by-endpoint gateway authorization remain production hardening work.
