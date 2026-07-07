# Delivery Closure 350: Auth Me No Default Admin Fallback

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Java `/api/v1/auth/me`
- Boundary: production authentication context safety after Higress/OIDC or JWT authentication

## Result

`GET /api/v1/auth/me` no longer fabricates a default local administrator profile when `CurrentUserHolder` is empty.

The endpoint now only returns the user profile parsed by the authentication filter. If the controller is reached without an authenticated current user, it raises `SecurityException("current authenticated user required")` instead of returning `userId=1`, `ADMIN`, and broad sample permissions.

This keeps the endpoint aligned with the documented contract: login belongs to the external Higress/OIDC entry, and Java only exposes the already-authenticated RBAC context.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#exposesExternalAuthProfileEndpoint" test` failed because `PermissionController.currentUser()` still created `new CurrentUser(1L, Set.of("ADMIN"), ...)`.
- GREEN: the same targeted contract test passed after removing the fallback and requiring an authenticated current user.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=SecurityRuntimeContractTest" test` passed `5/5`, covering missing bearer rejection, insufficient permission rejection, authorized permission access, request-body precheck, and disabled-account rejection.

## Remaining Risk

- Full production OIDC/JWKS acceptance remains separate from this closure.
- This closure removes an unsafe fallback inside Java; it does not replace gateway-level OIDC login testing.
