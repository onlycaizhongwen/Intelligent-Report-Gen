# 249. UC-08 Rule Topology Validation Closure

Date: 2026-07-01

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: the rule definition validator checked schema and dangling edges, but it did not reject executable graph cycles or paths that could not reach an `end` node.
- Delivery boundary: backend domain validation for saved/debugged rule definitions. Frontend validation overlays are still future work.

## Completed

- Added executable topology validation to `RuleDomainService.validateDefinition()`.
- The validator now starts from the `start` node and checks that every reachable path terminates at an `end` node.
- The validator rejects cycles on the active traversal path before a rule can be saved or debugged.
- Existing schema validation remains in place for node ids, required `start/end`, dangling edges, self edges, and branch `true/false` paths.

## Verification

- RED backend tests failed because cyclic and non-terminating rule graphs were accepted.
- GREEN verification:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=RuleDomainServiceTest test` passed `15/15`.
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=RuleApplicationServiceTest test` passed `88/88`.

## Remaining Gaps

- Frontend graph validation overlays can show invalid topology before save.
- The rule designer still needs richer visual authoring for large approval flows and drag-select insertion.
