# Delivery Closure 255: UC-04 Enterprise Export Template Governance

Date: 2026-07-01

## Target

Close the next UC-04 production governance slice: enterprise export branding should be centrally managed, versioned, auditable, and reusable by export requests without forcing each client to resend the full `brand` payload.

## Changes

- Added `EnterpriseExportTemplate` domain model and `EnterpriseExportTemplateRepository`.
- Added in-memory and JDBC repositories for enterprise export template persistence.
- Added Flyway migration `V053__enterprise_export_templates.sql` with `template_id + version` uniqueness and JSONB `brand_snapshot`.
- Added application-service operations:
  - `createEnterpriseExportTemplate`
  - `listEnterpriseExportTemplates`
  - `updateEnterpriseExportTemplate`
  - `listEnterpriseExportTemplateVersions`
  - `disableEnterpriseExportTemplate`
  - `enableEnterpriseExportTemplate`
- Added REST endpoints under `/api/v1/enterprise-export-templates`, guarded by existing `report:export` permission.
- Export creation now resolves a managed active enterprise export template by `templateId` when `brand` is omitted.
- Export `brandSnapshot` now includes `templateVersion` when the export used a managed template.
- Inline `brand` export payloads remain supported for backward compatibility.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#managesEnterpriseExportTemplatesWithVersionedBrandSnapshots+usesManagedEnterpriseExportTemplateWhenBrandPayloadIsOmitted test` failed because the enterprise export template management methods did not exist.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#managesEnterpriseExportTemplatesWithVersionedBrandSnapshots+usesManagedEnterpriseExportTemplateWhenBrandPayloadIsOmitted test`: 2 passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportApplicationServiceTest#managesEnterpriseExportTemplatesWithVersionedBrandSnapshots+usesManagedEnterpriseExportTemplateWhenBrandPayloadIsOmitted,ContractSurfaceTest#enterpriseExportTemplatesExposeGovernedReportExportEndpoints" test`: 3 passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test`: 2 passed.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`: 41 passed.

## Remaining UC-04 Work

- The central enterprise template management UI is still not closed.
- The API currently uses existing `report:export` permission; a future admin surface may split this into a dedicated template-management permission.
- DOCX/PDF/PPTX layout fidelity remains minimal compared with a professional rendering engine.
- A fresh real-backend/browser export smoke was not run in this closure.
