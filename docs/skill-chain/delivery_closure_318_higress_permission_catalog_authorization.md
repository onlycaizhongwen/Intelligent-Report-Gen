# Closure 318: Higress permission catalog authorization matrix

## Scope

- Requirement: `REQ-AUTH-001`
- User journey: every protected customer-facing API permission must keep the Java RBAC boundary when traffic enters through local Higress.
- Production gap: Closure 316 covered representative modules, but several catalog permissions still had no dedicated gateway-level `401/403/authorized` probe.

## Result

- Added `buildHigressPermissionCatalogAuthorizationChecks()` to `scripts/higress-gateway-smoke-lib.mjs`.
- The new matrix covers every permission currently published by the RBAC permission catalog:
  - `report:create`
  - `report:read`
  - `report:export`
  - `report:template:manage`
  - `report:share`
  - `collaboration:write`
  - `knowledge:manage`
  - `knowledge:upload`
  - `datasource:manage`
  - `rule:manage`
  - `rule:debug`
  - `audit:read`
  - `dashboard:read`
  - `notification:read`
  - `permission:read`
  - `user:manage`
- Each permission now has three Higress-routed checks:
  - missing token -> `401`
  - valid token without the required permission -> `403`
  - valid token with the required permission -> business-layer `200`, `400`, or `404`
- Authorized negative probes intentionally use missing resources or invalid input for side-effect-safe coverage while proving the request passed authentication and authorization.

## Verification

- RED:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - failed because `buildHigressPermissionCatalogAuthorizationChecks` was not exported.
- GREEN unit:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - result: `8/8` passed.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - result: `passed=true`.
  - The live bundle included the existing Java route boundary checks, permission-matrix checks, representative module checks, data-source checks, and the new 48 permission-catalog checks.

## Remaining Risk

- This closes "at least one gateway authorization probe per RBAC catalog permission".
- It is still not generated endpoint-by-endpoint from all controller methods; production OIDC login, TLS certificates, and WAF behavior remain separate hardening work.
