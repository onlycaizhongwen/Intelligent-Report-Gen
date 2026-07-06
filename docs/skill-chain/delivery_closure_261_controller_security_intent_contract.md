# Delivery Closure 261: Controller Security Intent Contract

## Scope

- Requirement scope: cross-cutting security governance for `REQ-AUTH-001`, `REQ-AUDIT-001`, `REQ-REPORT-002`, and `REQ-COLLAB-001`
- Production risk closed: controller endpoints could exist without an explicit security intent declaration, making it hard to distinguish intentional authenticated/public flows from accidental missing RBAC annotations.

## Result

Every Java REST controller endpoint must now declare one of:

- `@RequiresPermission(...)` for normal RBAC-protected business operations.
- `@AuthenticatedEndpoint(...)` for JWT-authenticated endpoints that intentionally do not require a narrower permission.
- `@PublicEndpoint(...)` for explicitly public ingress paths that are controlled by business tokens, share passwords, risk checks, or other non-JWT gates.

Special endpoints are now explicit:

- `GET /api/v1/history` is authenticated-only personal history.
- `GET /api/v1/auth/me` is authenticated-only current RBAC profile.
- `GET /api/v1/reports/generation-tasks/{taskId}/stream` is authenticated-only generation stream.
- `POST /api/v1/share-links/{shareToken}/access` is a public share-token challenge endpoint.
- `POST /api/v1/share-links/{shareToken}/report` is a public read-only share-token report endpoint.
- `POST /api/v1/share-links/{shareToken}/exports/{exportFileId}/download-url` is a public share-token controlled download endpoint.

## Code Evidence

- `backend/java-report-core/src/main/java/com/company/report/shared/security/AuthenticatedEndpoint.java`
- `backend/java-report-core/src/main/java/com/company/report/shared/security/PublicEndpoint.java`
- `backend/java-report-core/src/main/java/com/company/report/audit/interfaces/rest/AuditController.java`
- `backend/java-report-core/src/main/java/com/company/report/permission/interfaces/rest/PermissionController.java`
- `backend/java-report-core/src/main/java/com/company/report/report/interfaces/rest/ReportController.java`
- `backend/java-report-core/src/test/java/com/company/report/contract/ContractSurfaceTest.java`

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointDeclaresSecurityIntent" test`
  - Failed with six missing security intent declarations: `/history`, `/auth/me`, three share-link public endpoints, and generation task SSE stream.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointDeclaresSecurityIntent" test`
  - `1/1` test passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,JwtTokenProviderTest,PermissionAspectTest,AuditApplicationServiceTest,ReportApplicationServiceTest#openTaskStreamRejectsUnknownTask+confirmOutlineMovesTaskIntoRetrievalStage" test`
  - `33/33` tests passed.

## Residual Risk

This closure is source-level governance and compile-time regression protection. It does not replace gateway-level smoke tests or MVC 401/403/200 runtime tests for every endpoint.
