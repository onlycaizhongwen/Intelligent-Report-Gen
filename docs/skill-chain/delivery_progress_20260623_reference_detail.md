# Delivery Progress - Reference Detail

Date: 2026-06-23

## Scope

- Replaced fixed demo reference response with current report citation data.
- Added report owner guard before reference lookup.
- Kept implementation on existing `report_sections.citation_marks` JSONB to avoid schema expansion before the citation model is finalized.

## Changed Behavior

- `GET /api/v1/reports/{reportId}/references/{referenceId}` now returns citation fields from the current report version:
  - `reportId`
  - `referenceId`
  - `sourceTitle`
  - `sourceType`
  - `snapshot`
  - `score`
  - `anchor`
- Cross-user reference access now raises `SecurityException`.
- Legacy string citations are ignored for detail lookup instead of breaking section/export reads.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`
  - Failed as expected on fixed `qualityScore=86.5` response and missing owner guard.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`
  - 18 tests, 0 failures.
- PostgreSQL IT: `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - 12 tests, 0 failures.
  - Flyway validated 6 migrations; schema version 006 already current.
- Module regression: `.\mvnw.cmd -pl backend/java-report-core test`
  - 34 tests, 0 failures.

## Remaining Risk

- Reference data is still embedded in section citation JSON. A dedicated citation/source table should be added later if reference lifecycle, de-duplication, or source-level audit becomes a requirement.
