# 233. UC-08 Approval Template Version Reference Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: reusable approval templates applied to a rule canvas need explicit provenance so production rules remain traceable after template lifecycle changes.
- Delivery boundary: newly applied approval template nodes persist template id, template version, and step id; backend usage detection recognizes explicit template references while keeping legacy `tpl{id}_` node prefix compatibility.

## Completed

- Approval template responses now expose `version: 1`.
- Rule designer now writes the following fields onto generated approval nodes:
  - `approvalTemplateId`
  - `approvalTemplateVersion`
  - `approvalTemplateStepId`
- Backend approval template usage impact now detects references by explicit `approvalTemplateId` first and still supports existing `tpl{approvalTemplateId}_` node ids.

## Verification

- RED:
  - Backend test failed because approval template responses lacked `version` and explicit node references were not counted in usage.
  - Frontend E2E failed because saved canvas nodes lacked template provenance fields.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#createsAndListsApprovalTemplatesWithValidatedSteps,RuleApplicationServiceTest#approvalTemplateUsageRecognizesExplicitVersionReferencesOnRuleNodes" test` passed `2/2`.
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `1/1`.
  - `npm run typecheck` passed.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.

## Remaining Gaps

- `version` is currently a fixed v1 contract marker; immutable template version history and versioned template edits still need a dedicated persistence model.
- Dedicated paginated impact query for large rule libraries.
- Full visual independent approval flow designer.
