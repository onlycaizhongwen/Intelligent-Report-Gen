# 252. UC-08 Rule Designer Broken Path Edges Closure

Date: 2026-07-01

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: topology validation could mark the blocked node, but the incoming broken path edge was not visible enough for operators working on a graph.
- Delivery boundary: broken path edge feedback in the existing rule designer edge list and Vue Flow canvas. Full multi-edge path tracing remains future work.

## Completed

- Extended local topology validation to return `blockedEdgeKeys`.
- When a downstream node cannot reach `end`, the incoming edge is marked as a broken path.
- The edge list now displays `Broken path start -> review` for the broken edge.
- The Vue Flow edge label also shows `Broken path start -> review` and uses red dashed styling.
- Existing save blocking, `Topology blocked` node marker, and API short-circuit behavior remain unchanged.

## Verification

- RED E2E failed because `Broken path start -> review` was missing.
- GREEN verification:
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `4/4`.
  - `npm run typecheck` passed.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `5/5`.

## Remaining Gaps

- Multi-edge broken path tracing can be more explicit for larger rule graphs.
- Full visual independent approval flow designer and drag-select insertion remain future work.
