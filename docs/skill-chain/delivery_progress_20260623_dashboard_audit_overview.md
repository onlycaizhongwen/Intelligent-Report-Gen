# Delivery Progress - Dashboard Audit Overview

Date: 2026-06-23

## Scope

- Replaced fixed dashboard demo cards with audit-log-based aggregation.
- Supported basic ranges: `today`, `last7days`, `last30days`, `all`.
- Returned recent activities from persisted audit logs.

## Changed Behavior

- `GET /api/v1/dashboard/overview` now returns:
  - `range`
  - `cards.reportExports`
  - `cards.knowledgeOperations`
  - `cards.modelInvocations`
  - `cards.failedOperations`
  - `recentActivities`
- Removed fixed `reportsThisMonth=1` and `knowledgeEntries=2` demo response from the application service.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=AuditApplicationServiceTest test`
  - Failed as expected on fixed dashboard cards.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=AuditApplicationServiceTest test`
  - 3 tests, 0 failures.
- Module regression: `.\mvnw.cmd -pl backend/java-report-core test`
  - 37 tests, 0 failures.

## Remaining Risk

- The dashboard currently derives metrics from audit logs only. Later slices should add first-class query repositories for report trend, knowledge item totals, active data sources, citation hit rate, and active users.
