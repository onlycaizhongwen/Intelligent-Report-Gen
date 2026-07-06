# 234. UC-08 Approval Template Usage Pagination Closure

Date: 2026-06-29

## Scope

- Requirement: `REQ-RULE-001`
- Prototype gap: approval template impact analysis must remain usable when a customer has a large rule library.
- Delivery boundary: provide a dedicated paginated usage query for approval templates and stop relying on an application-level full rule scan for usage impact.

## Completed

- Added `GET /api/v1/rules/approval-templates/{templateId}/usage`.
- Added `RuleApplicationService.listApprovalTemplateUsage(templateId, page, pageSize)`.
- Added repository-level usage lookup and count:
  - `findRulesUsingApprovalTemplate(templateId, page, pageSize)`
  - `countRulesUsingApprovalTemplate(templateId)`
- In-memory repository detects both explicit `approvalTemplateId` references and legacy `tpl{id}_` node prefixes.
- JDBC repository uses PostgreSQL JSONB node inspection with `LIMIT/OFFSET`, avoiding application-layer full rule scans.
- Existing template list and disable impact summaries now reuse the repository usage lookup/count path.
- Frontend API contract now exposes `ruleApi.listApprovalTemplateUsage()`.

## Verification

- RED:
  - Backend test failed because `listApprovalTemplateUsage()` and the route did not exist.
  - Frontend contract test failed because `ruleApi.listApprovalTemplateUsage` did not exist.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=RuleApplicationServiceTest#approvalTemplateUsageCanBeQueriedWithPagination,ContractSurfaceTest#ruleApprovalTemplatesUseManagePermissionEndpoints" test` passed `2/2`.
  - `npm run test -- src/api/apiContracts.test.ts` passed `27/27`.

## Remaining Gaps

- Full immutable approval template version history.
- Full visual independent approval flow designer.
- Larger scale database performance should be rechecked with representative production rule counts and JSONB indexes.
