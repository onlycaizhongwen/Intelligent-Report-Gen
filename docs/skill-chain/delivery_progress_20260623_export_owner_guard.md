# Delivery Progress: Report Export Owner Guard

Time: 2026-06-23 13:41:59 +08:00

## Scope

- Added owner authorization before creating report export files.
- Reused the same report-owner guard for controlled download URL generation.
- Added regression coverage to ensure a non-owner cannot create an export.
- Added side-effect assertions: unauthorized export must not write object storage, export-file metadata, or audit logs.
- Extended PostgreSQL integration coverage so the same owner guard is verified against real `reports`, `report_export_files`, and `operation_logs` tables.

## Changed Files

- `backend/java-report-core/src/main/java/com/company/report/report/application/ReportApplicationService.java`
- `backend/java-report-core/src/test/java/com/company/report/report/application/ReportApplicationServiceTest.java`
- `backend/java-report-core/src/test/java/com/company/report/report/infrastructure/persistence/JdbcReportGenerationTaskRepositoryPostgresIT.java`

## TDD Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`
  - Failure: `rejectsExportWhenReportBelongsToAnotherUser` expected `SecurityException`, but no throwable was raised.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`
  - Result: 16 tests, 0 failures, 0 errors.

## Verification

- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - Result: 11 tests, 0 failures, 0 errors.
  - Evidence: Flyway validated 6 migrations and reported schema version 006 up to date on PostgreSQL 16.14.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`
  - Result: 1 test, 0 failures, 0 errors.
  - Evidence: Spring Boot loaded 41 request mappings.
- `.\mvnw.cmd -pl backend/java-report-core test`
  - Result: 32 tests, 0 failures, 0 errors.

## Remaining Risks

- Controller-level HTTP status tests are still missing for `SecurityException`.
- Authentication is still backed by `CurrentUserHolder`; production JWT/OIDC integration remains incomplete.
- Several application services still return fixed demo data and should be replaced with persisted behavior before customer delivery.
