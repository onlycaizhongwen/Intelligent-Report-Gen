# 231. UC-08 Approval Template Usage Impact Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template managers need to see whether a template is already used before disabling it.
- Delivery boundary: expose a minimal usage count and referenced rule list based on rule canvas nodes generated from the template prefix `tpl{approvalTemplateId}_`.

## Completed

- Added approval template usage impact to backend template responses:
  - `usageCount`
  - `usageRules`
- Added disable response `impact` with the same usage summary.
- Added frontend display for template usage count and referenced rule names/statuses.
- Kept the implementation read-only against existing rule definitions; no schema migration was required.

## Verification

- RED:
  - Backend test failed because template responses did not contain `usageCount`.
  - Frontend E2E failed waiting for `Usage 1 rule`.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalTemplateListAndDisableExposeRuleUsageImpact" test` passed `1/1`.
  - `npm run typecheck` passed.
  - `npm run e2e -- approval-templates.spec.ts` passed `1/1`.

## Remaining Gaps

- Immutable template versions and explicit template version references on generated nodes.
- Dedicated impact query endpoint with pagination for large rule libraries.
- Confirm dialog before disabling templates that are already referenced.
- Full visual independent approval flow designer.
