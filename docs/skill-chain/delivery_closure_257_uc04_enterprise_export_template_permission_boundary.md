# Delivery Closure 257: UC-04 Enterprise Export Template Permission Boundary

Date: 2026-07-01

## Target

Close the UC-04 security hardening slice for centralized enterprise export template governance. Enterprise template maintenance must not be controlled by the same permission used for ordinary report export operations.

## Changes

- Changed enterprise export template management endpoints from `report:export` to `report:template:manage`.
- Kept ordinary report export and export-status endpoints on `report:export`.
- Added `report:template:manage` to the permission matrix.
- Granted `report:template:manage` to `system_admin` and `senior_analyst`.
- Kept `analyst` limited to ordinary `report:export` without enterprise template management.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ContractSurfaceTest#enterpriseExportTemplatesExposeGovernedReportExportEndpoints test` failed because `/enterprise-export-templates` endpoints still used `report:export`.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings test` initially could not complete during parallel Maven execution due to Surefire test discovery conflict in the shared module target directory; it was rerun sequentially after implementation.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ContractSurfaceTest#enterpriseExportTemplatesExposeGovernedReportExportEndpoints test`: 1 passed.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings test`: 1 passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#managesEnterpriseExportTemplatesWithVersionedBrandSnapshots+usesManagedEnterpriseExportTemplateWhenBrandPayloadIsOmitted,ContractSurfaceTest#enterpriseExportTemplatesExposeGovernedReportExportEndpoints,PermissionApplicationServiceTest#permissionMatrixContainsRolePermissionMappings" test`: 4 passed.

## Remaining UC-04 Work

- Central enterprise export template management UI is still not implemented.
- No real browser flow or gateway-level permission smoke was run in this closure.
- A future real-backend security smoke should prove that an `analyst` token with only `report:export` is rejected for `/enterprise-export-templates`.
