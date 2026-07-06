# Delivery Progress: Controlled Download Access and Audit

Time: 2026-06-23 13:37:20 +08:00

## Scope

- Hardened controlled report-export download URL generation.
- Added service-layer owner check before issuing a presigned download URL.
- Added `report_export_download` operation audit after successful download URL creation.
- Added regression coverage for cross-user export-file access denial.
- Added PostgreSQL integration coverage for the same download URL, owner-check, and audit-log path.

## Changed Files

- `backend/java-report-core/src/main/java/com/company/report/report/application/ReportApplicationService.java`
- `backend/java-report-core/src/test/java/com/company/report/report/application/ReportApplicationServiceTest.java`
- `backend/java-report-core/src/test/java/com/company/report/report/infrastructure/persistence/JdbcReportGenerationTaskRepositoryPostgresIT.java`

## Verification

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`
  - Result: 15 tests, 0 failures, 0 errors.
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - Result: 11 tests, 0 failures, 0 errors.
  - Evidence: Flyway validated 6 migrations and reported schema version 006 up to date on PostgreSQL 16.14.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`
  - Result: 1 test, 0 failures, 0 errors.
  - Evidence: Spring Boot loaded 41 request mappings.
- `.\mvnw.cmd -pl backend/java-report-core test`
  - Result: 31 tests, 0 failures, 0 errors.

## Remaining Risks

- `SecurityException` currently relies on the global exception handler behavior; controller-level HTTP status assertions should be added.
- `createExport()` still does not enforce report owner access before export creation.
- Current user is still represented by `CurrentUserHolder`; production authentication integration remains incomplete.
