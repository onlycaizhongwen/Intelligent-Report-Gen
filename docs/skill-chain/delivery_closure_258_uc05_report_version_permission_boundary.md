# Delivery Closure 258: UC-05 Report Version Permission Boundary

Date: 2026-07-01

## Target

Close a production security gap in report version management. Report version list, diff, and rollback endpoints must be protected by explicit RBAC permissions instead of remaining callable without `@RequiresPermission`.

## Changes

- Added `@RequiresPermission("report:read")` to `GET /reports/{reportId}/versions`.
- Added `@RequiresPermission("report:read")` to `GET /reports/{reportId}/versions/diff`.
- Added `@RequiresPermission("report:create")` to `POST /reports/{reportId}/versions/{versionId}/rollback`.
- Kept existing version service behavior unchanged.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ContractSurfaceTest#reportVersionEndpointsRequireReadOrCreatePermissions test` failed because the report version endpoints had no required permission annotations.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ContractSurfaceTest#reportVersionEndpointsRequireReadOrCreatePermissions test`: 1 passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#listsVersionsAndRollsBackCurrentReportVersion+comparesReportVersionsBySectionSnapshot,ContractSurfaceTest#reportVersionEndpointsRequireReadOrCreatePermissions" test`: 3 passed.

## Remaining Work

- This closure proves source-level RBAC contract coverage and service regression, not a gateway-level or browser-level permission smoke.
- A future real-backend security smoke should verify unauthenticated and under-permissioned requests return 403 for the version endpoints.
