# 239. UC-08 Approval Template Version History Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template managers could only open the hardcoded version 1 snapshot after editing a template.
- Delivery boundary: expose and render the full approval template version history. This does not yet add diff or rollback actions.

## Completed

- Added repository support for listing approval template snapshots by `templateId`, ordered by newest version first.
- Added backend service and REST endpoint:
  - `GET /api/v1/rules/approval-templates/{templateId}/versions`
- Added frontend API method:
  - `ruleApi.listApprovalTemplateVersions()`
- Approval template management page now shows `View versions` for versioned templates.
- Version history panel renders all returned versions and allows opening any snapshot.

## Verification

- RED backend verification failed on missing `listApprovalTemplateVersions()` and missing REST route.
- RED frontend API verification failed on missing `ruleApi.listApprovalTemplateVersions`.
- RED E2E failed waiting for `View versions Legal review approval`.
- GREEN verification:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalTemplateEditsCreateImmutableVersionSnapshots,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- No version diff viewer yet.
- No rollback action yet.
- Full visual independent approval flow designer remains open.
