# Delivery Closure 390: Ingestion and User Import Request Contracts

Date: 2026-07-08

## Scope

Closed the next customer-review gap in S4/API contract evidence for high-frequency ingestion and RBAC initialization requests.

The following endpoints now declare field-level JSON request contracts instead of a generic `business request body` placeholder:

- `POST /api/v1/knowledge-items/batch-import`
- `POST /api/v1/data-sources/{dataSourceId}/sync-runs`
- `POST /api/v1/users/batch-import`

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#knowledgeDataSourceAndUserMutationEndpointsDeclareFieldLevelRequestContracts" test` failed because `POST /api/v1/knowledge-items/batch-import requestBody.properties` was missing.
- GREEN: the same targeted test passed after the API contract declared `knowledgeBaseId/items`, `mode/sampleRows/timeoutMs`, and `users` at field level.
- JSON parse check confirmed all API contract JSON blocks remain parseable.

## Remaining Risk

This closes the ingestion/user-import request-contract gap only. Other backend supplement endpoints still need module-by-module request schema deepening with runtime confirmation.
