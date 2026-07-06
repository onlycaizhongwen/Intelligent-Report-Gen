# 236. UC-08 Approval Template Version Persistence Foundation Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template provenance should not depend on a hardcoded response version.
- Delivery boundary: persist a template `version` column and expose it in backend/frontend responses; keep lifecycle enable/disable from changing the version.

## Completed

- Added Flyway migration `V050__rule_approval_template_version.sql`.
- `RuleApprovalTemplate` now carries `version`.
- New templates are created with `version=1`.
- JDBC repository inserts, updates, selects, and maps the `version` column.
- Approval template responses now read `template.version()` instead of hardcoding `1`.
- Approval template management UI displays `Version 1`.

## Verification

- `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsAndListsApprovalTemplatesWithValidatedSteps,RuleApplicationServiceTest#disablesAndEnablesApprovalTemplatesForDesignerReuse,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `3/3`.

## Remaining Gaps

- Template edit/publish flow that creates immutable `version=2+` snapshots.
- Rules currently store applied `approvalTemplateVersion`, but there is no separate version history table yet.
- Full visual independent approval flow designer.
