# Delivery Closure 260: RBAC Permission Catalog and Knowledge Delete Boundary

## Scope

- Requirement scope: `REQ-KB-001`, `REQ-KB-003`, `REQ-AUDIT-001`, `REQ-DASH-001`
- Production risk closed:
  - The knowledge item delete endpoint reused service-level safeguards but lacked REST boundary permission metadata.
  - Several permissions already used by controllers were not exposed by the permission matrix, which could make frontend, gateway, and admin role configuration drift from backend enforcement.

## Result

Knowledge item deletion now requires `knowledge:manage` at the REST boundary:

- `DELETE /api/v1/knowledge-items/{itemId}`

The permission matrix now includes controller-used permissions:

- `datasource:manage`
- `audit:read`
- `dashboard:read`
- `notification:read`

Role mapping remains least-privilege oriented:

- `system_admin`: all permissions.
- `senior_analyst`: data source management, global audit, dashboard, and notifications.
- `analyst`: dashboard and notifications, but not global audit or data source management.
- `viewer`: report read only.

## Code Evidence

- `backend/java-report-core/src/main/java/com/company/report/knowledge/interfaces/rest/KnowledgeController.java`
  - Adds `@RequiresPermission("knowledge:manage")` to knowledge item deletion.
- `backend/java-report-core/src/main/java/com/company/report/permission/application/PermissionApplicationService.java`
  - Adds missing controller-used permissions to `permissionMatrix()`.
  - Updates role mappings for admin, senior analyst, and analyst.
- `backend/java-report-core/src/test/java/com/company/report/contract/ContractSurfaceTest.java`
  - Adds endpoint contract coverage for knowledge deletion permission.
- `backend/java-report-core/src/test/java/com/company/report/permission/application/PermissionApplicationServiceTest.java`
  - Extends permission matrix coverage for the missing RBAC catalog entries.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#knowledgeDeleteEndpointRequiresManagePermission,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`
  - Failed because `DELETE /knowledge-items/{itemId}` lacked `@RequiresPermission("knowledge:manage")`.
  - Failed because the permission matrix lacked `datasource:manage`, `audit:read`, `dashboard:read`, and `notification:read`.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#knowledgeDeleteEndpointRequiresManagePermission,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`
  - `2/2` tests passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,ContractSurfaceTest#knowledgeDeleteEndpointRequiresManagePermission,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`
  - `27/27` tests passed.

## Residual Risk

This closure verifies controller contract, permission catalog, role mapping, and knowledge application behavior. It does not yet prove gateway-level RBAC propagation or live browser 403/200 behavior for each permission.
