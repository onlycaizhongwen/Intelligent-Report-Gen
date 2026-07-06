# Delivery Closure 259: UC-09 Collaboration Permission Boundary

## Scope

- Requirement scope: `REQ-COLLAB-002`
- Prototype journey: report annotation submission and collaboration task status update
- Production risk closed: collaboration write endpoints were protected only by service-level ownership and assignee checks, but lacked controller-level RBAC permission declarations.

## Result

Collaboration write operations now require `collaboration:write` at the REST boundary:

- `POST /api/v1/reports/{reportId}/annotations`
- `PUT /api/v1/tasks/{taskId}/status`

The permission matrix now exposes `collaboration:write` and grants it to:

- `system_admin`
- `senior_analyst`
- `analyst`

`viewer` remains read-only and does not receive `collaboration:write`.

## Code Evidence

- `backend/java-report-core/src/main/java/com/company/report/citation/interfaces/rest/CollaborationController.java`
  - Adds `@RequiresPermission("collaboration:write")` to annotation creation.
  - Adds `@RequiresPermission("collaboration:write")` to task status update.
- `backend/java-report-core/src/main/java/com/company/report/permission/application/PermissionApplicationService.java`
  - Adds `collaboration:write` to the permission catalog.
  - Grants collaboration write permission to operational authoring roles while excluding `viewer`.
- `backend/java-report-core/src/test/java/com/company/report/contract/ContractSurfaceTest.java`
  - Adds contract coverage for collaboration endpoint permission declarations.
- `backend/java-report-core/src/test/java/com/company/report/permission/application/PermissionApplicationServiceTest.java`
  - Extends permission matrix coverage for `collaboration:write`.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#collaborationEndpointsRequireWritePermission,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`
  - Failed because collaboration controller endpoints lacked `@RequiresPermission("collaboration:write")`.
  - Failed because `PermissionApplicationService.permissionMatrix()` did not expose `collaboration:write`.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#collaborationEndpointsRequireWritePermission,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`
  - `2/2` tests passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=CollaborationApplicationServiceTest,ContractSurfaceTest#collaborationEndpointsRequireWritePermission,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`
  - `8/8` tests passed.

## Residual Risk

This closure verifies source-level endpoint contracts, role permission matrix behavior, and collaboration service rules. It does not yet prove a gateway-level or full MVC 403/200 runtime smoke through Higress and the Java service.
