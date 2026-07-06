# Closure 320: Controller permission catalog drift guard

## Scope

- Requirement: `REQ-AUTH-001`
- User journey: when a Java controller declares `@RequiresPermission(...)`, the permission must also be represented in the gateway-facing RBAC catalog probes.
- Production gap: Closure 319 generated the endpoint authorization matrix, but a future controller could still introduce a new permission string without adding it to the RBAC catalog and Higress smoke coverage.

## Result

- Added `findControllerPermissionCatalogGaps(...)` to `scripts/controller-authorization-matrix-lib.mjs`.
- Extended `tests/unit/node/controller_authorization_matrix.test.mjs` to compare:
  - permissions declared by Java controller endpoints;
  - permissions covered by `buildHigressPermissionCatalogAuthorizationChecks()`.
- The guard currently proves all controller-level `@RequiresPermission(...)` values are covered by the 16 gateway-facing RBAC permission probe families.

## Verification

- RED:
  - `node --test tests/unit/node/controller_authorization_matrix.test.mjs`
  - failed because `findControllerPermissionCatalogGaps` was not exported.
- GREEN:
  - `node --test tests/unit/node/controller_authorization_matrix.test.mjs`
  - result: `4/4` passed.
- Gateway regression:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - result: `8/8` passed.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - result: `passed=true`.

## Remaining Risk

- This is a static drift guard between controller annotations and local Higress permission probe definitions.
- It does not replace live Higress smoke execution, production OIDC login, TLS certificates, or WAF policy validation.
