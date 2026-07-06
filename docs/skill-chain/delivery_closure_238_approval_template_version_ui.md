# 238. UC-08 Approval Template Version UI Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: operators could not edit reusable approval templates or inspect historical version snapshots from the web console.
- Delivery boundary: add minimal version management UI on the existing approval template management page. This is not yet a full diff or rollback workflow.

## Completed

- Approval template cards now expose `Edit`.
- Editing reuses the existing template form and calls `ruleApi.updateApprovalTemplate()`.
- Successful edits refresh the template card and display the new version.
- Templates with `version > 1` expose `View version 1`.
- The version snapshot panel calls `ruleApi.getApprovalTemplateVersion()` and displays historical steps.

## Verification

- RED E2E failed waiting for `Edit Legal review approval`.
- GREEN verification:
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- No arbitrary version picker yet; current UI exposes version 1 as the audit baseline.
- No version diff or rollback action yet.
- Full visual independent approval flow designer remains open.
