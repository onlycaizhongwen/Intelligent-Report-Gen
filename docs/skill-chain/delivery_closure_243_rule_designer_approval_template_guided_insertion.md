# 243. UC-08 Rule Designer Approval Template Guided Insertion Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: applying an approval template to the rule canvas only appended generated approval nodes and internal edges, so operators could not insert the reusable approval chain into an existing `start -> end` path.
- Delivery boundary: guided insertion for a selected existing source node. This does not yet provide a full visual independent approval flow designer.

## Completed

- Added an `Insert after node` selector beside the approval template selector.
- Applying a template with no insertion node keeps the previous append behavior.
- Applying a template after a selected node rewires that node's outgoing edges:
  - selected node -> first template approval node,
  - template approval nodes remain sequential,
  - last template approval node -> previous outgoing targets.
- The saved rule definition now persists the inserted approval chain instead of leaving the original direct edge in place.

## Verification

- RED E2E failed waiting for `Insert approval template after node`.
- GREEN verification:
  - `npm run typecheck` passed.
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `1/1`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
- Future iterations can add drag-select insertion, branch-specific insertion, and template preview before apply.
