# Delivery Closure 263: MVC Security Runtime Contract

## Scope

- Requirement scope: `REQ-AUTH-001`, Java API runtime authentication and RBAC authorization behavior.
- Production risk closed: source-level permission annotations and filter tests existed, but there was no repeatable MVC runtime proof that the Spring Security filter, JWT parsing, `CurrentUserHolder`, permission aspect, and global exception handler produce the expected 401/403/200 contract together.

## Result

The Java backend now has MockMvc runtime coverage for a protected permission endpoint:

- Missing bearer token returns HTTP `401` and API body `code=401`.
- Valid JWT without `permission:read` returns HTTP `403` and API body `code=403`.
- Valid JWT with `permission:read` returns HTTP `200`, a successful API body, and a permission matrix payload.

This closes the highest-value local MVC runtime security proof without needing new Docker deployment or external credentials.

## Code Evidence

- `backend/java-report-core/src/test/java/com/company/report/shared/security/SecurityRuntimeContractTest.java`
  - Boots the Spring application with H2, disabled Flyway, disabled RocketMQ, and test JWT secret.
  - Exercises `/api/v1/roles/permission-matrix` through MockMvc and the real security chain.
- Existing runtime chain under test:
  - `JwtAuthenticationFilter`
  - `JwtTokenProvider`
  - `CurrentUserHolder`
  - `PermissionAspect`
  - `GlobalExceptionHandler`
  - `SecurityConfig`

## Verification

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=SecurityRuntimeContractTest" test`
  - `3/3` tests passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest" test`
  - `32/32` tests passed.

## Residual Risk

This closure proves the in-process Java MVC runtime contract. It does not replace live Higress route tests, browser-level 403/200 checks, or full endpoint-by-endpoint authorization matrix coverage.
