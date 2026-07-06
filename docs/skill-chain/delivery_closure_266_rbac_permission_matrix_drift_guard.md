# Delivery Closure 266: RBAC Permission Matrix Drift Guard

Date: 2026-07-02

## Scope

This closure continues the production-readiness loop for `REQ-AUTH-001` and the implemented Java API surface. The goal is to prevent controller-level permission annotations from drifting away from the RBAC permission matrix used by the web console, gateway policy mapping, and operator configuration.

## Result

- Added a permission matrix contract test that scans all Java `*Controller.java` files for `@RequiresPermission("...")`.
- The test verifies every controller-required permission is present in `PermissionApplicationService.permissionMatrix().permissions`.
- Future backend endpoints that introduce a new permission without exposing it in the matrix now fail local Maven regression before delivery.

## Mutation Evidence

The guard was mutation-checked by temporarily removing `notification:read` from the matrix.

Expected failure was observed:

- Test: `PermissionApplicationServiceTest#permissionMatrixContainsEveryControllerRequiredPermission`
- Failure reason: matrix did not contain controller-required `notification:read`

The temporary mutation was then restored.

## Verification

Target GREEN:

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest#permissionMatrixContainsEveryControllerRequiredPermission" test
```

Result:

- Tests run: 1
- Failures: 0
- Errors: 0
- Skipped: 0

Regression:

```powershell
.\mvnw.cmd -pl backend/java-report-core "-Dtest=PermissionApplicationServiceTest,ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,JwtAuthenticationFilterTest,SecurityRuntimeContractTest" test
```

Result:

- Tests run: 62
- Failures: 0
- Errors: 0
- Skipped: 0

## Residual Risk

This closure proves source-level permission matrix alignment and related Java security regression locally. It does not replace live Higress route policy checks, browser-level RBAC flows, or external OIDC/session revocation validation.
