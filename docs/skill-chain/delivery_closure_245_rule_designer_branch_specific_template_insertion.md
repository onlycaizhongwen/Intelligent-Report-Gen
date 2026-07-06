# 245. UC-08 Rule Designer Branch-Specific Template Insertion Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: guided approval template insertion could rewrite all outgoing edges from a selected node, which is unsafe for branch nodes with true/false or multi-path routing.
- Delivery boundary: branch-path selection for template insertion in the existing rule designer. This does not yet provide a full visual approval flow designer.

## Completed

- Added a branch path selector when the selected insertion node has multiple outgoing edges.
- Selecting one branch path rewires only that edge:
  - selected branch node -> first generated approval node,
  - generated approval nodes remain sequential,
  - last generated approval node -> original target of the selected edge.
- Sibling branch edges remain unchanged.
- The selected branch condition is preserved on the rewired edges for execution traceability.
- Changing the insertion node clears the previously selected branch edge to avoid stale edge-key reuse.
- The live preview now follows the selected branch path instead of showing all outgoing targets.

## Verification

- RED E2E failed waiting for `Insert approval template into branch path`.
- GREEN verification:
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `3/3`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
- Future iterations can add visual before/after diff, drag-select insertion, and branch path validation against backend rule topology constraints.
