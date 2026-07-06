# 246. UC-08 Rule Designer Template Edge Change Preview Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: branch-specific approval template insertion could preview the resulting path, but operators still could not see the exact edge changes before applying the template.
- Delivery boundary: read-only edge change summary in the existing rule designer. This is not yet a full visual before/after graph diff.

## Completed

- Added an edge change preview below the approval template path preview.
- The preview lists each edge that will be removed.
- The preview lists each edge that will be added.
- Branch conditions are shown in the preview and preserved in the generated edge changes.
- The preview is read-only and does not mutate the rule canvas until `Apply approval template` is clicked.

## Verification

- RED E2E failed waiting for `Remove edge riskBranch -> approvePath (true)`.
- GREEN verification:
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `3/3`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
- Future iterations can add graph-level before/after highlighting, drag-select insertion, and backend topology constraint validation for advanced edits.
