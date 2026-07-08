# Delivery Closure 394: Rule Runtime Request Contracts

Date: 2026-07-08

## Scope

Closed the remaining S4/API request-contract placeholders for rule runtime, approval actions, scheduler operations, and webhook action execution operations.

The following endpoints now declare field-level JSON request contracts instead of a generic `business request body` placeholder:

- `POST /api/v1/rules/{ruleId}/review-submissions`
- `POST /api/v1/rules/{ruleId}/approvals`
- `POST /api/v1/rules/{ruleId}/runs`
- `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions`
- `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements`
- `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders`
- `POST /api/v1/rules/approval-records/batch-actions`
- `PUT /api/v1/rules/{ruleId}/schedule`
- `POST /api/v1/rules/{ruleId}/schedule/retry`
- `POST /api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry`
- `POST /api/v1/rules/{ruleId}/action-executions/batch`

## Request Schema Notes

- Review submission and publish approval expose the audit `comment`.
- Production rule execution exposes the runtime `sample` object.
- Approval actions expose `action` and `comment`; supplements expose `comment` and `evidenceUrl`.
- Reminder and single action-execution retry endpoints explicitly declare empty-object semantics because the backend derives the operation from path parameters.
- Batch approval actions expose `action`, `approvalRecordIds`, and `comment`.
- Scheduler configuration exposes `scheduleEnabled`, `scheduleIntervalSeconds`, `maxRetryCount`, `nextRunAt`, and `scheduleInput`.
- Scheduler retry exposes `nextRunAt` and `scheduleInput`.
- Batch action execution operations expose `operation`, `actionExecutionIds`, and `reason`.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleRuntimeEndpointsDeclareFieldLevelRequestContracts" test` failed because `POST /api/v1/rules/{ruleId}/review-submissions requestBody.properties` was missing.
- GREEN: the same targeted test passed after the API contract declared field-level request schemas for all remaining rule runtime endpoints.
- JSON parse check confirmed `jsonBlocks=14` and `genericPayloadPlaceholders=0`.

## Remaining Risk

This closes the known generic request-body placeholder gap in S4/API contract documentation. Production WAF/TLS/OIDC evidence, credentialed production smoke tests, and customer-scale validation remain separate delivery gates.
