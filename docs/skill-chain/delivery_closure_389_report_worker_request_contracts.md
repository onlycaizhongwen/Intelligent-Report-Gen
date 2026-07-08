# Delivery Closure 389: Report Worker Callback Request Contracts

Date: 2026-07-08

## Scope

Closed a customer-review gap in the S4/API contract evidence for the report generation worker callback path.

The following controlled Java endpoints now declare field-level JSON request contracts instead of a generic `business request body` placeholder:

- `POST /api/v1/reports/generation-tasks/{taskId}/failure`
- `POST /api/v1/reports/generation-tasks/{taskId}/completion`
- `POST /api/v1/reports/generation-tasks/{taskId}/retry`

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#reportGenerationWorkerCallbacksDeclareFieldLevelRequestContracts" test` failed because `completion requestBody.properties` was missing.
- GREEN: the same targeted test passed after the API contract declared `sections`, `references`, `modelInvocation`, `errorCode`, `message`, `retryable`, and `reason` at field level.

## Remaining Risk

This closes the report worker callback request-contract gap only. Other backend supplement endpoints may still use generic `requestBody.payload` placeholders and should be deepened by module priority instead of being bulk-edited without DTO/runtime confirmation.
