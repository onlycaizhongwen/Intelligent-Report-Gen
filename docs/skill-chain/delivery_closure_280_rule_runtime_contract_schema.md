# Closure 280: Rule Runtime Contract Schema

## Scope

- Requirement chain: `REQ-RULE-001`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `POST /api/v1/rules/{ruleId}/review-submissions`
  - `POST /api/v1/rules/{ruleId}/approvals`
  - `POST /api/v1/rules/{ruleId}/runs`
  - `GET /api/v1/rules/{ruleId}/runs`
  - `GET /api/v1/rules/{ruleId}/runs/{runId}/subprocess-topology`
  - `GET /api/v1/rules/{ruleId}/approval-records`
  - `GET /api/v1/rules/approval-records`
  - `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/actions`
  - `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplements`
  - `POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/reminders`
  - `POST /api/v1/rules/approval-records/batch-actions`
  - `GET /api/v1/rules/{ruleId}/action-executions`
  - `GET /api/v1/rules/{ruleId}/metrics`
  - `PUT /api/v1/rules/{ruleId}/schedule`
  - `POST /api/v1/rules/{ruleId}/schedule/retry`
  - `POST /api/v1/rules/{ruleId}/action-executions/{actionExecutionId}/retry`
  - `POST /api/v1/rules/{ruleId}/action-executions/batch`

## Result

Rule runtime APIs no longer expose generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas for review/publish, production runs, subprocess topology, approval records, approval actions, action execution retry/compensation, metrics, and schedule retry flows.

- Rule fields: `ruleId`, `name`, `description`, `status`, `definition`, `versionId`, `scheduleEnabled`, `scheduleIntervalSeconds`, `nextRunAt`, `failureCount`, `maxRetryCount`, `scheduleInput`
- Run fields: `runId` or `debugRunId`, `ruleId`, `versionId`, `status`, `runType`, `triggeredByUserId`, `durationMs`, `errorMessage`, `matched`, `input`, `output`
- Approval record fields include assignment/delegate/SLA/group summary fields.
- Action execution fields include retry status, endpoint, idempotency key, next retry time, error message, and metadata.
- Metrics fields: `ruleId`, `totalRuns`, `succeededRuns`, `failedRuns`, `successRate`, `averageDurationMs`, `lastStatus`, `lastErrorMessage`

## Evidence

- Contract guard: `ContractSurfaceTest#ruleRuntimeEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `RuleApplicationService#toResponse`, `toRunResponse`, `subprocessRunTopology`, `toApprovalRecordResponse`, `toActionExecutionResponse`, `metrics`, `configureSchedule`, `retrySchedule`, and batch handling methods

## TDD Evidence

RED:

```text
ContractSurfaceTest.ruleRuntimeEndpointsDeclareFieldLevelResponseContracts
POST /api/v1/rules/{ruleId}/review-submissions responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#ruleRuntimeEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun browser-level rule runtime E2E or live Higress route smoke.
- The S4 backend supplement no longer has generic `business response data` placeholders; remaining production hardening should move to runtime E2E, gateway route checks, and customer-facing workflow acceptance.
