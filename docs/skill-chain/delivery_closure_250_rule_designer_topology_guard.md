# 250. UC-08 Rule Designer Topology Guard Closure

Date: 2026-07-01

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: backend validation now rejects invalid executable topology, but the rule designer still allowed users to click save before seeing a local topology error.
- Delivery boundary: local save guard and visible error message in the existing rule designer. This is not yet a full visual invalid-topology overlay.

## Completed

- Added local topology validation before `Save canvas changes`.
- The rule designer now blocks save when the current graph has no valid path from `start` to `end`.
- Invalid self/dangling edges and cyclic executable paths are checked locally before calling the save API.
- The page shows `Rule topology path cannot reach end` when a path is broken, and the save request is not sent.
- Updated the branch insertion E2E fixture to represent a real executable rule graph where both branch paths terminate at `end`.

## Verification

- RED E2E failed because deleting `start -> end` and saving did not show `Rule topology path cannot reach end`.
- GREEN verification:
  - `npm run e2e -- rule-approval-template-apply.spec.ts` passed `3/3`.
  - `npm run typecheck` passed.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run e2e -- approval-templates.spec.ts rule-approval-template-apply.spec.ts` passed `4/4`.

## Remaining Gaps

- The graph can still be improved with visual invalid-edge overlays and highlighted broken paths.
- Full visual independent approval flow designer and drag-select insertion remain future work.
