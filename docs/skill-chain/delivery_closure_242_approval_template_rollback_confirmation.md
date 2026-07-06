# 242. UC-08 Approval Template Rollback Confirmation Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template rollback was available from version history, but the destructive business intent needed an explicit confirmation step before creating a new current version.
- Delivery boundary: frontend confirmation only. Backend immutable rollback behavior remains unchanged.

## Completed

- Added a rollback confirmation dialog to the approval template management page.
- Clicking `Rollback version N` now stages the selected template/version instead of immediately calling the rollback API.
- `Confirm rollback` executes the existing rollback flow and refreshes the template list.
- `Cancel` clears the staged rollback candidate without changing template history.

## Verification

- RED E2E failed waiting for `Rollback approval template version?`.
- GREEN verification:
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- Full visual independent approval flow designer remains open.
