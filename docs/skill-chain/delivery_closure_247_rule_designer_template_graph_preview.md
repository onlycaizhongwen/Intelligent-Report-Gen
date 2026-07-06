# 247. UC-08 Rule Designer Template Graph Preview Closure

Date: 2026-07-01

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template insertion had text and edge-change previews, but the interactive rule canvas itself did not show what would be inserted before applying the template.
- Delivery boundary: graph-level preview markers in the existing rule designer. This is not yet a full standalone approval flow designer.

## Completed

- Added preview status labels to Vue Flow node cards.
- Existing insertion source nodes show `Preview insert point` before applying a template.
- Existing target nodes show `Preview target`.
- Generated template approval steps are rendered as temporary `preview-*` Vue Flow nodes before `Apply approval template`.
- Preview nodes are read-only and are not persisted into `selectedRule.definition`.
- Added visual styling for preview states:
  - insert point: blue highlight,
  - target: amber highlight,
  - pending approval step: green dashed preview card.

## Verification

- RED E2E failed because `riskBranch` did not contain `Preview insert point`.
- GREEN verification:
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `3/3`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
- Future iterations can add preview edges, drag-select insertion, validation overlays, and backend topology constraint validation for advanced edits.
