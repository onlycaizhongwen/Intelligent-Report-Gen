# 232. UC-08 Approval Template Disable Confirmation Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: disabling a referenced approval template should not be a blind one-click action.
- Delivery boundary: when a template has `usageCount > 0`, the web console shows a confirmation panel with referencing rules before calling the disable API.

## Completed

- Added a referenced-template confirmation panel in `ApprovalTemplates.vue`.
- The confirmation panel lists the template name, usage count, and referencing rule names/statuses.
- Unused templates can still be disabled directly.
- Referenced templates require `Confirm disable` before the disable request is sent.

## Verification

- RED: `npm run e2e -- approval-templates.spec.ts` failed waiting for `Disable referenced approval template?`.
- GREEN:
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- Dedicated paginated impact query for large rule libraries.
- Immutable template versions and explicit version references on generated nodes.
- Full visual independent approval flow designer.
