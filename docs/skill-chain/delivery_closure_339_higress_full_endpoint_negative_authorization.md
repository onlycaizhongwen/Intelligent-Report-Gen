# Delivery Closure 339: Higress Full Endpoint Negative Authorization

## Scope

- Requirement: `REQ-AUTH-001`
- Journey: cross-UC gateway authorization hardening
- Runtime boundary: client -> Higress -> Java MVC handler mapping -> permission interceptor -> controller

## Result

Higress endpoint authorization coverage now supports full live negative execution for every Java controller endpoint protected by `@RequiresPermission`:

- `runHigressGatewaySmoke(...)` can append all generated endpoint-level negative probes through `controllerEndpointAuthorizationNegativeCoverage`.
- `scripts/higress-gateway-smoke.mjs` enables this with `HIGRESS_CONTROLLER_ENDPOINT_AUTH_NEGATIVE_COVERAGE=all|true|1|yes`.
- The full live path executes only missing-token and insufficient-permission probes, avoiding authorized write-side probes across broad POST/PUT/DELETE endpoints.
- Java now enforces `@RequiresPermission` in a Spring MVC `HandlerInterceptor` before request body binding, while keeping the existing permission aspect as defense in depth.
- The controller authorization matrix now extracts mapping `consumes` metadata so multipart endpoints receive a minimal `multipart/form-data` probe body and hit the intended handler boundary.

## Evidence

- RED: Java targeted security test failed because `POST /api/v1/data-sources` with an insufficient-permission token and no body returned `500` from request body parsing instead of `403`.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=com.company.report.shared.security.SecurityRuntimeContractTest" test` passed `5/5`.
- RED: Node controller matrix test failed because multipart endpoint `consumes` metadata was absent.
- GREEN: `node --test tests/unit/node/controller_authorization_matrix.test.mjs` passed `5/5`.
- RED: Node Higress smoke test failed because generated multipart endpoint negative probes had no `multipart/form-data` `Content-Type` or body.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `14/14`.
- Runtime RED before the interceptor/container refresh: full Higress negative coverage returned `controller401=98`, `controller403=48`, `failedCount=50`.
- Runtime RED after the interceptor/container refresh but before multipart probe support: full Higress negative coverage returned `controller401=98`, `controller403=96`, `failedCount=2`.
- Runtime GREEN: `HIGRESS_CONTROLLER_ENDPOINT_AUTH_NEGATIVE_COVERAGE=all node scripts/higress-gateway-smoke.mjs` returned `passed=true`.
- Runtime GREEN summary: `totalResults=276`, `controllerEndpointNegativeResults=196`, `controller401=98`, `controller403=98`, `failedCount=0`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/shared/security/PermissionInterceptor.java`
- `backend/java-report-core/src/main/java/com/company/report/shared/security/PermissionWebMvcConfig.java`
- `backend/java-report-core/src/test/java/com/company/report/shared/security/SecurityRuntimeContractTest.java`
- `scripts/controller-authorization-matrix-lib.mjs`
- `scripts/higress-gateway-smoke-lib.mjs`
- `scripts/higress-gateway-smoke.mjs`
- `tests/unit/node/controller_authorization_matrix.test.mjs`
- `tests/unit/node/higress_gateway_smoke.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure proves local Higress full negative authorization propagation, not production OIDC login behavior, trusted TLS certificate lifecycle, or production WAF policy enforcement.
- Authorized endpoint live probes remain intentionally excluded from broad execution until endpoint-specific fixtures and side-effect rollback controls are added.
