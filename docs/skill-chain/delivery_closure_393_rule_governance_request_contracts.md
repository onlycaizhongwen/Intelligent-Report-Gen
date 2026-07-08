# Delivery Closure 393: Rule Governance Configuration Request Contracts

Date: 2026-07-08

## Scope

Closed the S4/API contract review gap for rule-governance configuration endpoints. The following endpoints now declare field-level JSON request contracts instead of a generic `business request body` placeholder:

- `POST /api/v1/rules/approval-templates`
- `PUT /api/v1/rules/approval-templates/{templateId}`
- `POST /api/v1/rules/approval-templates/{templateId}/versions/{version}/rollback`
- `POST /api/v1/rules/approval-templates/{templateId}/disable`
- `POST /api/v1/rules/approval-templates/{templateId}/enable`
- `POST /api/v1/rules/approval-delegate-rules`
- `POST /api/v1/rules/approval-delegate-rules/batch-import`
- `PUT /api/v1/rules/approval-delegate-rules/{delegateRuleId}`
- `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/disable`
- `POST /api/v1/rules/approval-delegate-rules/{delegateRuleId}/enable`

## Request Schema Notes

- Approval templates now expose `name`, `description`, `status`, and ordered `steps`.
- Template steps now expose `stepId`, `approvalTitle`, `assigneeRoles`, `approvalMode`, `slaHours`, and `slaEscalations`.
- Approval delegate rules now expose `assigneeRole`, `delegateRole`, `activeFrom`, `activeTo`, `activeWeekdays`, `activeDates`, and `reason`.
- Template rollback/disable/enable endpoints explicitly declare empty-object semantics because the backend derives the operation from path parameters.
- Delegate rule disable/enable endpoints expose the optional `reason` field used by the backend.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleGovernanceConfigurationEndpointsDeclareFieldLevelRequestContracts" test` failed because `POST /api/v1/rules/approval-templates requestBody.properties` was missing.
- GREEN: the same targeted test passed after the API contract declared field-level request schemas for approval templates and delegate rules.
- JSON parse check confirmed `jsonBlocks=14` and `genericPayloadPlaceholders=11`.

## Remaining Risk

This closes rule-governance configuration request-contract review evidence only. Remaining generic request-body placeholders are concentrated in rule runtime, approval action, schedule, and action-execution endpoints. Production WAF/TLS/OIDC evidence remains an external delivery gate.
