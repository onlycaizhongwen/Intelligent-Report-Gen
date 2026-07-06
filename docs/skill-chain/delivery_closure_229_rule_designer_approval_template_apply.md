# 229. UC-08 Rule Designer Approval Template Apply Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: reusable approval templates must be applicable inside the rule designer.
- Delivery boundary: select an enabled template, generate approval nodes from template steps, generate internal sequential edges, and persist the resulting rule definition through the existing save path.

## Completed

- Added approval template selection controls to the rule canvas editor.
- Loaded enabled approval templates through `ruleApi.listApprovalTemplates(...)`.
- Added `applyApprovalTemplate()` in `RuleDesigner.vue`.
- Converted template steps into approval canvas nodes with:
  - stable node id prefix `tpl{approvalTemplateId}_`
  - approval title
  - assignee role list
  - approval mode
  - SLA hours
  - SLA escalation policy
- Generated sequential edges between template approval steps.
- Preserved existing canvas nodes and edges when applying a template.
- Added duplicate node id protection before applying a template.

## Verification

- RED: `npm run e2e -- rule-approval-template-apply.spec.ts` failed waiting for `Approval template selector`.
- GREEN:
  - `npm run typecheck` passed.
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `1/1`.

## Remaining Gaps

- Template versioning, disable/edit lifecycle, usage count, and impact analysis.
- Full visual independent approval flow designer.
- Optional guided insertion that connects generated approval chains into an existing start/end path should be designed explicitly before implementation.
