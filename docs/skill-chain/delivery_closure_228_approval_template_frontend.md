# 228. UC-08 Approval Template Frontend Management Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: reusable approval templates must be visible and operable from the web console.
- Delivery boundary: frontend API contract plus a minimal management page. Rule-designer template application remains follow-up work.

## Completed

- Added `ruleApi.createApprovalTemplate(...)`.
- Added `ruleApi.listApprovalTemplates(...)`.
- Added `/rules/approval-templates` route.
- Added `Approval templates` side navigation entry.
- Added `ApprovalTemplates.vue` with:
  - template list loading
  - template create form
  - pipe-delimited multi-step input
  - step rendering for title, roles, mode, SLA, and escalation policy
- Added Playwright E2E coverage for list/create behavior and request payload.

## Verification

- RED: `npm run test -- src/api/apiContracts.test.ts` failed at `ruleApi.createApprovalTemplate is not a function`.
- GREEN: `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
- RED: `npm run e2e -- approval-templates.spec.ts` failed waiting for the `Approval templates` navigation item.
- GREEN: `npm run e2e -- approval-templates.spec.ts` passed `1/1`.
- Final regression:
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts approval-delegate-rules.spec.ts` passed `4/4`.

## Remaining Gaps

- Apply approval template inside the rule designer to generate approval nodes and edges.
- Template versioning, disable/edit lifecycle, and usage count.
- Full visual independent approval flow designer.
