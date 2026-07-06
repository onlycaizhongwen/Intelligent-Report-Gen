# Delivery Progress - Share Links

Date: 2026-06-23

## Scope

- Replaced fixed `/share/demo-share-token-valid` response with persistent share links.
- Added `ShareLink` domain model, repository port, JDBC adapter, and Flyway migration.
- Added external share access validation for missing, inactive, and expired links.
- Added report owner guard before creating share links.

## Changed Behavior

- `POST /api/v1/reports/{reportId}/share-links` now creates a random persisted token.
- Cross-user share creation now raises `SecurityException` before a token is stored.
- `POST /api/v1/share-links/{shareToken}/access` now loads the token from storage and returns the linked report id only when active and not expired.
- Share links are stored in `share_links` via migration `V007__share_links.sql`.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`
  - Failed as expected because share link model/repository did not exist and service was demo-only.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`
  - 3 tests, 0 failures.
- PostgreSQL IT: `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - 13 tests, 0 failures.
  - Flyway validated 7 migrations; schema v007 is current.
- Module regression: `.\mvnw.cmd -pl backend/java-report-core test`
  - 40 tests, 0 failures.

## Remaining Risk

- Share access currently returns metadata only; the external readable report payload should be explicitly designed and protected from leaking internal-only fields.
