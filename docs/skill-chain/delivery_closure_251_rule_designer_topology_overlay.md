# 251. UC-08 Rule Designer Topology Overlay Closure

Date: 2026-07-01

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: the rule designer blocked invalid topology before save, but the graph did not visually identify the node where the executable path was blocked.
- Delivery boundary: blocked-node overlay in the existing Vue Flow rule canvas. Full broken-path edge highlighting remains future work.

## Completed

- Added `topologyStatus/topologyLabel` flow node data for local topology validation failures.
- When `Save canvas changes` detects a non-terminating path, the blocked node is marked on the canvas with `Topology blocked`.
- Added red visual styling for blocked topology nodes.
- Existing text error `Rule topology path cannot reach end` remains visible, and the save request is still prevented.
- Topology highlight state is cleared when the local rule definition changes.

## Verification

- RED E2E failed because `rule-flow-node-start` did not contain `Topology blocked` after deleting `start -> end`.
- GREEN verification:
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `3/3`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `4/4`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.

## Remaining Gaps

- Broken edges and full invalid paths can be highlighted more precisely.
- Full visual independent approval flow designer and drag-select insertion remain future work.
