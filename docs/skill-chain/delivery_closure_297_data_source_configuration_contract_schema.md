# Closure 297: Data Source Configuration Contract Schema

Date: 2026-07-06

## Scope

- Requirement: `REQ-KB-003`
- User journey: `UC-06`
- Surface: S4 API contract for enterprise data-source connection test and configuration save endpoints.

## Result

The enterprise data-source configuration endpoints no longer rely on generic `summary` response schemas.

- `POST /api/v1/data-sources/test-connection` now declares field-level request and response contracts for `dataSourceId`, `success`, `message`, and `sourceType`.
- `POST /api/v1/data-sources` now declares field-level request and response contracts for source identity, endpoint, target knowledge base, sync query, field mapping, cursor, schedule, retry, failure count, and credential configuration state.
- The existing `ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts` now guards both configuration endpoints and sync-run endpoints under `REQ-KB-003`.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts" test` failed because `POST /api/v1/data-sources/test-connection` lacked `responseBody.data.dataSourceId`.
- GREEN: the same targeted contract test passed `1/1` after the structured contract supplement was added.

## Remaining Gaps

- Real Docker enterprise database/ERP/OA/finance sync smoke is still pending because the local Docker daemon is currently unavailable.
- Connector credential rotation policy, source-specific field mapping validation, and live Higress route checks remain production hardening work.
