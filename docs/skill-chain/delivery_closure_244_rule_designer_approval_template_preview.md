# 244. UC-08 Rule Designer Approval Template Preview Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: guided insertion can change existing rule canvas edges, so operators need a lightweight preview before applying a reusable approval template.
- Delivery boundary: text preview of the path that will be inserted. This is not yet a visual diff or full approval flow designer.

## Completed

- Added a live preview beside the approval template insertion controls.
- When no insertion node is selected, the preview shows the generated template approval chain.
- When an insertion node is selected, the preview shows the selected node, generated approval nodes, and current outgoing targets in execution order.
- The preview is read-only and does not change the save payload until `Apply approval template` is clicked.

## Verification

- RED E2E failed waiting for `Preview path start -> tpl901_financeManager -> tpl901_financeDirector -> end`.
- GREEN verification:
  - `npm run typecheck` passed.
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `1/1`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
- Future iterations can add visual before/after diff, branch-specific insertion preview, and drag-select insertion.
