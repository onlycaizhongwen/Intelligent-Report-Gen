# 248. UC-08 Rule Designer Template Preview Edges Closure

Date: 2026-07-01

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template insertion already showed text summaries and temporary preview nodes, but the Vue Flow canvas did not show which edges would be removed or added before applying the template.
- Delivery boundary: graph-level preview edges for the existing rule designer. This is not yet a full standalone approval flow designer.

## Completed

- Added temporary Vue Flow preview edges before `Apply approval template`.
- Branch insertion now shows a red dashed remove preview edge for the selected replaced path.
- Branch insertion now shows green dashed add preview edges from the insertion node to the first template step and from the last template step to the selected target.
- Preview edge labels use real business node ids:
  - `Preview remove edge riskBranch -> approvePath`
  - `Preview add edge riskBranch -> tpl901_financeManager`
  - `Preview add edge tpl901_financeDirector -> approvePath`
- Preview edges use distinct `preview-*` edge ids and `preview-*` node endpoints where needed, so they do not collide with real rule edges.
- Saving still reads from `selectedRule.definition.edges`; preview edges are not persisted into the rule definition.

## Verification

- RED E2E failed on missing `Preview remove edge riskBranch -> approvePath`.
- GREEN verification:
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `3/3`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
- Future iterations can add drag-select insertion, invalid-topology overlays, backend topology constraint validation, and larger graph usability improvements.
