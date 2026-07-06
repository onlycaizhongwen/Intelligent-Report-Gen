# 240. UC-08 Approval Template Version Diff Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template version history could list and open snapshots, but managers could not see what changed between two versions.
- Delivery boundary: compare two approval template versions by `stepId` and show added, removed, modified, and unchanged step counts. This does not yet add rollback.

## Completed

- Added backend diff service:
  - `RuleApplicationService.compareApprovalTemplateVersions(templateId, baseVersion, targetVersion)`
- Added backend REST endpoint:
  - `GET /api/v1/rules/approval-templates/{templateId}/versions/diff?baseVersion=1&targetVersion=2`
- Diff output includes:
  - `summary.added`
  - `summary.removed`
  - `summary.modified`
  - `summary.unchanged`
  - `changes[]` with `changeType`, `stepId`, `baseStep`, and `targetStep`
- Added frontend API method:
  - `ruleApi.compareApprovalTemplateVersions()`
- Approval template version history panel now supports comparing the oldest and newest returned versions.

## Verification

- RED backend verification failed on missing service method and REST route.
- RED frontend API verification failed on missing `ruleApi.compareApprovalTemplateVersions`.
- RED E2E failed waiting for `Compare version 1 to 2 Legal review approval`.
- GREEN verification:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalTemplateVersionsCanBeComparedByStepId,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- No rollback action yet.
- Full visual independent approval flow designer remains open.
