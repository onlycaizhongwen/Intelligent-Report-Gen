# 237. UC-08 Approval Template Immutable Versions Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: reusable approval templates need audit-safe version traceability after rules apply a template.
- Delivery boundary: add template edit flow that creates `version=2+` snapshots and expose a version lookup API. This does not yet add a visual version comparison UI.

## Completed

- Added immutable version snapshot table migration `V051__rule_approval_template_versions.sql`.
- New template creation now persists a `version=1` snapshot.
- Template content edits increment the current template version and persist a new immutable snapshot.
- Lifecycle enable/disable remains separate from content versioning and does not increment version.
- Added backend API:
  - `PUT /api/v1/rules/approval-templates/{templateId}`
  - `GET /api/v1/rules/approval-templates/{templateId}/versions/{version}`
- Added frontend API contract methods:
  - `ruleApi.updateApprovalTemplate()`
  - `ruleApi.getApprovalTemplateVersion()`

## Verification

- RED backend verification failed on missing `updateApprovalTemplate`, missing `getApprovalTemplateVersion`, and missing `V051__rule_approval_template_versions.sql`.
- RED frontend verification failed on missing `ruleApi.updateApprovalTemplate`.
- GREEN targeted verification:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalTemplateEditsCreateImmutableVersionSnapshots,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.

## Remaining Gaps

- Approval template edit/version management UI is not implemented yet.
- No version diff viewer or rollback action yet.
- Full visual independent approval flow designer remains open.
