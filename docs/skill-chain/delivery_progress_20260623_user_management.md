# Delivery Progress 2026-06-23 - User Management

## Scope

- Replaced fixed demo user responses with repository-backed user management.
- Added persistent `UserAccount` model with display name, status, and RBAC roles.
- Added in-memory and JDBC user repositories.
- Added PostgreSQL migration `V011__user_accounts.sql`.
- Expanded permission matrix with concrete roles, permissions, and role-permission mappings.

## Validation Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`
  - Failed because `InMemoryUserRepository` and repository-backed constructor did not exist.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=PermissionApplicationServiceTest test`
  - Tests run: 5, Failures: 0, Errors: 0, Skipped: 0.
- PostgreSQL integration: `$env:RUN_POSTGRES_INTEGRATION='true'; .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`
  - Flyway migrated schema to `v011`.
  - Tests run: 17, Failures: 0, Errors: 0, Skipped: 0.
- Full module regression: `.\mvnw.cmd -pl backend/java-report-core test`
  - Tests run: 50, Failures: 0, Errors: 0, Skipped: 0.

## Remaining Risks

- Authentication is still delegated to Higress/OIDC boundary; local user accounts currently support management/profile data and RBAC mapping.
- Passwords and identity-provider user sync are intentionally out of scope for this slice.
- Fine-grained per-resource authorization policy remains a follow-up hardening item.
