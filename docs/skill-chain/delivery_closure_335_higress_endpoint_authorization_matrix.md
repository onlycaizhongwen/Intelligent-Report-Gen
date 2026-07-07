# Delivery Closure 335: Higress Endpoint Authorization Matrix

## Scope

- Requirement: `REQ-AUTH-001`
- Journey: cross-UC gateway authorization hardening
- Runtime boundary: client -> Higress -> Java controller permission aspect

## Result

Higress authorization coverage now has an endpoint-by-endpoint probe matrix generated from the Java controller authorization matrix:

- `buildHigressControllerEndpointAuthorizationChecks(...)` consumes the parsed Java controller matrix.
- Every controller endpoint with `boundary=permission` gets three Higress probe definitions: missing token, insufficient permission, and authorized token.
- Path variables are replaced with stable probe values such as `999999999`.
- Probe metadata preserves the controller endpoint, controller, handler, and required permission.
- `docs/skill-chain/higress_controller_endpoint_authorization_matrix.md` is generated from the probe definitions and is guarded by a unit test.
- Current generated coverage: 98 permission endpoints, 294 probe definitions.

## Evidence

- RED: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` first failed because `buildHigressControllerEndpointAuthorizationChecks` was not exported.
- RED: the same test then failed because `renderHigressControllerEndpointAuthorizationMatrixMarkdown` was not exported and the synchronized Markdown artifact was absent.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `10/10`.
- Matrix regression: `node --test tests/unit/node/controller_authorization_matrix.test.mjs` passed `4/4`.

## Files

- `scripts/higress-gateway-smoke-lib.mjs`
- `tests/unit/node/higress_gateway_smoke.test.mjs`
- `docs/skill-chain/higress_controller_endpoint_authorization_matrix.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure proves endpoint-by-endpoint probe generation and synchronized documentation, not live execution of all 294 probes.
- Production OIDC login, TLS certificates, WAF behavior, and full live gateway authorization execution remain future hardening work.
