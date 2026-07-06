# 235. UC-08 Approval Template Usage UI Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template managers need an operable page-level entry to inspect template usage, not only a short inline summary.
- Delivery boundary: approval template management page can open a usage panel for a template and load referenced rules through the dedicated paginated usage API.

## Completed

- Added `View usage` action to each approval template card.
- Added a usage detail panel showing:
  - template name
  - current page and total pages
  - referenced rule name and status
  - previous/next usage page controls
- The page calls `ruleApi.listApprovalTemplateUsage(templateId, { page, pageSize: 10 })`.
- Existing disable confirmation still uses the template summary and remains unchanged.

## Verification

- RED: `npm run e2e -- approval-templates.spec.ts` failed waiting for `View usage Legal review approval`.
- GREEN:
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.
  - `npm run typecheck` passed.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.

## Remaining Gaps

- Full immutable approval template version history.
- Full visual independent approval flow designer.
- Larger usage result sets should add search/filter by rule name/status if customer rule libraries grow substantially.
