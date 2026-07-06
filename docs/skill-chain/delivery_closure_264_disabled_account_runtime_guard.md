# Delivery Closure 264: Disabled Account Runtime Guard

## Scope

- Requirement scope: `REQ-AUTH-001`, account disablement and stale token enforcement.
- Production risk closed: disabled accounts were rejected by `@RequiresPermission` through `PermissionAspect`, but authenticated-only endpoints such as `/api/v1/auth/me` could still accept a stale JWT carrying `status=disabled`.

## Result

Disabled account enforcement now happens at the JWT filter boundary:

- JWT tokens with `status=disabled` are rejected before `CurrentUserHolder` and Spring Security authentication are populated.
- `@AuthenticatedEndpoint` methods now receive the same disabled-account protection as `@RequiresPermission` methods.
- `/api/v1/auth/me` no longer returns profile data for disabled-account tokens.

## Code Evidence

- `backend/java-report-core/src/main/java/com/company/report/shared/security/JwtAuthenticationFilter.java`
  - Checks `CurrentUser.enabled()` immediately after token parsing.
  - Returns HTTP `403` with API `code=403` for disabled account tokens.
- `backend/java-report-core/src/test/java/com/company/report/shared/security/SecurityRuntimeContractTest.java`
  - `authenticatedOnlyEndpointRejectsDisabledAccountToken` covers `/api/v1/auth/me`.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=SecurityRuntimeContractTest#authenticatedOnlyEndpointRejectsDisabledAccountToken" test`
  - Failed because `GET /api/v1/auth/me` returned HTTP `200` and exposed `status=disabled`.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=SecurityRuntimeContractTest#authenticatedOnlyEndpointRejectsDisabledAccountToken" test`
  - `1/1` test passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest" test`
  - `33/33` tests passed.

## Residual Risk

This closure enforces disabled-account tokens locally in the Java service. Live OIDC/Higress token revocation propagation and browser-level session invalidation still need real environment smoke coverage.
