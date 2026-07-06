# Delivery Progress 2026-06-23 - Knowledge Delete And Data Source

## Scope

- Replaced fixed knowledge item deletion with repository-backed soft delete and owner guard.
- Replaced fixed data-source save/test responses with persistent knowledge data sources.
- Added `KnowledgeDataSource` domain model and repository methods.
- Added PostgreSQL migration `V012__knowledge_data_sources.sql`.

## Validation Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Failed because repository methods for item lookup/delete and data sources did not exist.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Tests run: 6, Failures: 0, Errors: 0, Skipped: 0.
- PostgreSQL integration: `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - Flyway migrated schema to `v012`.
  - Tests run: 17, Failures: 0, Errors: 0, Skipped: 0.
- Full module regression: `.\mvnw.cmd -pl backend/java-report-core test`
  - Tests run: 52, Failures: 0, Errors: 0, Skipped: 0.

## Remaining Risks

- Data-source connection testing validates persisted configuration availability; it does not open live external ERP/OA/finance connections yet.
- Credentials/secrets for enterprise data sources are not stored in this slice and should be integrated through a secret manager or encrypted config.
- Knowledge deletion currently soft-deletes manual items; document chunk cascade/index cleanup remains a later hardening slice.
