# 241. UC-08 Approval Template Version Rollback Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template managers could inspect version history and diff, but could not restore a historical template safely.
- Delivery boundary: rollback creates a new current version from a historical snapshot. It does not mutate or delete existing snapshots.

## Completed

- Added backend rollback service:
  - `RuleApplicationService.rollbackApprovalTemplateVersion(templateId, version)`
- Added backend REST endpoint:
  - `POST /api/v1/rules/approval-templates/{templateId}/versions/{version}/rollback`
- Rollback behavior:
  - reads the selected historical snapshot,
  - creates a new current template version using that snapshot content,
  - preserves existing historical versions,
  - returns `sourceVersion`, `newVersion`, `currentVersion`, and `changeReason=rollback`.
- Added frontend API method:
  - `ruleApi.rollbackApprovalTemplateVersion()`
- Approval template version history panel now shows rollback actions for non-current versions and refreshes the template list after rollback.

## Verification

- RED backend verification failed on missing rollback service and REST route.
- RED frontend API verification failed on missing `ruleApi.rollbackApprovalTemplateVersion`.
- RED E2E failed waiting for `Rollback version 1 Legal review approval`.
- GREEN verification:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalTemplateRollbackCreatesNewCurrentVersionFromSnapshot,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- Rollback currently has no extra confirmation dialog.
- Full visual independent approval flow designer remains open.
