# Delivery Progress: Report List, Versions, and Rollback

Time: 2026-06-23 13:31:18 +08:00

## Scope

- Replaced fixed/demo report list behavior with repository-backed pagination by current user.
- Replaced fixed/demo report version list behavior with repository-backed version history.
- Replaced fixed/demo rollback behavior with persistent current-version switching.
- Added PostgreSQL integration coverage for report owner pagination/count and report version current-marker rollback.

## Changed Files

- `backend/java-report-core/src/main/java/com/company/report/report/application/ReportApplicationService.java`
- `backend/java-report-core/src/main/java/com/company/report/report/domain/repository/ReportRepository.java`
- `backend/java-report-core/src/main/java/com/company/report/report/domain/repository/ReportContentRepository.java`
- `backend/java-report-core/src/main/java/com/company/report/report/infrastructure/persistence/ReportMapper.java`
- `backend/java-report-core/src/main/java/com/company/report/report/infrastructure/persistence/MyBatisReportRepository.java`
- `backend/java-report-core/src/main/java/com/company/report/report/infrastructure/persistence/JdbcReportContentRepository.java`
- `backend/java-report-core/src/test/java/com/company/report/report/application/ReportApplicationServiceTest.java`
- `backend/java-report-core/src/test/java/com/company/report/report/infrastructure/persistence/JdbcReportGenerationTaskRepositoryPostgresIT.java`

## Verification

- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`
  - Result: 13 tests, 0 failures, 0 errors.
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - Result: 10 tests, 0 failures, 0 errors.
  - Evidence: Flyway validated 6 migrations and reported schema version 006 up to date on PostgreSQL 16.14.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`
  - Result: 1 test, 0 failures, 0 errors.
  - Evidence: Spring Boot loaded 41 request mappings.
- `.\mvnw.cmd -pl backend/java-report-core test`
  - Result: 29 tests, 0 failures, 0 errors.

## Remaining Risks

- Report list still depends on `CurrentUserHolder`; controller-level authorization and cross-user access tests should be added before customer production.
- File download URL endpoint still needs download access audit and permission checks.
- `getReference`, dashboard/model invocation summaries, and several collaboration/knowledge/rule endpoints still contain fixed example payloads.
