# Delivery Closure 254: UC-04 Export Brand Snapshot Governance

Date: 2026-07-01

## Target

Close a UC-04 production governance gap: enterprise exports must be auditable by the exact normalized brand/template settings used at export time, and PPTX must follow the same enterprise brand validation as DOCX/PDF.

## Changes

- Java export creation now treats DOCX, PDF, and PPTX as enterprise export formats for brand-template validation.
- Enterprise export records now include `brandSnapshot`, built from the normalized `ExportTheme` and materialized layout defaults.
- Export audit details include the same `brandSnapshot` so audit review can explain which template, colors, fonts, logo, header/footer, and layout were used.
- JDBC export persistence now stores and reads `brand_snapshot` JSONB.
- Flyway migration `V052__report_export_brand_snapshot.sql` adds `report_export_files.brand_snapshot`.
- Frontend report API types now expose `ReportExportStatus.brandSnapshot` for contract-level inspection.
- Java context-test hardening fixed two existing full-regression blockers discovered during verification:
  - `JdbcShareLinkRepository` now marks the `JdbcTemplate + ObjectMapper` constructor with `@Autowired`, removing Spring constructor ambiguity.
  - `ReportCoreProdProfileContextTest` now provides an externalized `security.data-source-credential-key`, matching the production profile contract.

## Verification

RED:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#rejectsPowerPointExportWhenEnterpriseBrandTemplateIsIncomplete+persistsEnterpriseExportBrandSnapshotForAuditAndStatusLookup test` failed because PPTX export did not reject incomplete enterprise brand fields and export status did not contain `brandSnapshot`.
- `npm run typecheck` failed because `brandSnapshot` did not exist on the frontend export status type.

GREEN:

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#rejectsPowerPointExportWhenEnterpriseBrandTemplateIsIncomplete+persistsEnterpriseExportBrandSnapshotForAuditAndStatusLookup test`: 2 passed.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`: 39 passed.
- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test`: 2 passed after context-test hardening.
- `.\mvnw.cmd -pl backend/java-report-core test`: 248 passed.
- `npm run typecheck`: passed.
- `npm run test -- src/api/apiContracts.test.ts`: 28 passed.

## Remaining UC-04 Work

- Full central enterprise template management UI is still not closed.
- DOCX/PDF/PPTX layout fidelity is still minimal compared with a professional document-rendering engine.
- A fresh real-backend export smoke was not run in this closure; it depends on the local Java service, PostgreSQL, MinIO, and browser test stack being available.
