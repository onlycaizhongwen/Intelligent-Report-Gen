# 2026-06-23 Export Audit Increment

Target: make report export observable through the real audit log instead of demo-only audit responses.

Completed:

- Added `operation_logs` persistence through Flyway `V006__operation_logs.sql`.
- Expanded audit domain model with actor, operation type, resource, result, detail JSON, and timestamp.
- Added `JdbcAuditRepository` for PostgreSQL write/read/page/count operations.
- Updated `AuditApplicationService.auditLogs()` and `history()` to read persisted audit records.
- Wired `ReportApplicationService.createExport()` to write a `report_export` success log after export file metadata is saved.
- Kept an 8-argument test constructor fallback for existing unit tests while Spring uses the 9-argument constructor with the real `AuditRepository`.

Verification evidence:

- RED first: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#recordsReportExportOperationInAuditLog test` initially failed at compile time because audit persistence was missing.
- GREEN: same targeted test passed: 1 test, 0 failures.
- PostgreSQL targeted integration passed and migrated the real local database from Flyway v005 to v006.
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`: 7 tests, 0 failures.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`: 1 test, 0 failures.
- `.\mvnw.cmd -pl backend/java-report-core test`: 25 tests, 0 failures.

Still needed:

- Export failure audit and retry state handling.
- Unified file download API instead of returning raw MinIO presigned URLs directly.
- Dashboard aggregation should read `operation_logs` instead of fixed cards.
