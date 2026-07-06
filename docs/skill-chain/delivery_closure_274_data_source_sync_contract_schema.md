# Closure 274: Data Source Sync Contract Schema

## Scope

- Requirement chain: `UC-06` / `REQ-KB-003`
- Phase artifact: `S4 API Contract`
- Target endpoints:
  - `POST /api/v1/data-sources/{dataSourceId}/sync-runs`
  - `GET /api/v1/data-sources/{dataSourceId}/sync-runs`

## Result

Enterprise data-source sync run APIs no longer expose only generic `business response data` in the backend supplement. The API contract now declares backend-real field-level response schemas.

- Single sync run fields: `syncRunId`, `dataSourceId`, `mode`, `status`, `processedRows`, `failureReason`, `message`, `lastCursor`, `startedAt`, `finishedAt`
- Paged sync run list fields: `items`, `page`, `pageSize`, `total`
- Paged item fields: `syncRunId`, `dataSourceId`, `mode`, `status`, `processedRows`, `failureReason`, `message`, `lastCursor`, `startedAt`, `finishedAt`

## Evidence

- Contract guard: `ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts`
- API contract: `docs/skill-chain/api_contract.md` section `13.2`
- Backend field source: `KnowledgeApplicationService#saveDataSourceSyncRun` and `KnowledgeApplicationService#listDataSourceSyncRuns`

## TDD Evidence

RED:

```text
ContractSurfaceTest.dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts
POST /api/v1/data-sources/{dataSourceId}/sync-runs responseBody.data.properties
Expecting value to be true but was false
```

GREEN:

```text
.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts" test
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Remaining Gaps

- This closure did not rerun real Docker enterprise database/ERP/OA/finance sync smoke tests.
- Source-specific field mapping validation, connector credentials hardening, and live Higress route smoke remain production hardening work.
