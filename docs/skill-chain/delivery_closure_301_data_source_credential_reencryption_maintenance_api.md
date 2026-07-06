# Delivery Closure 301: Data Source Credential Re-Encryption Maintenance API

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: expose the stale data-source credential re-encryption job as a protected maintenance API.

## Result

- Added `POST /api/v1/data-sources/credentials/reencrypt` in the Java knowledge controller.
- Protected the endpoint with `datasource:manage`.
- Accepted optional JSON body field `limit`; missing or blank values default to `100`, numeric/string values are clamped to at least `1`.
- Returned aggregate result fields only:
  - `scannedCount`
  - `migratedCount`
- Updated S4 API contract docs with table coverage, traceability, and structured JSON schema.

## Security Notes

- The API does not return credential plaintext, encrypted credential payloads, or key material.
- Authorization is intentionally the same `datasource:manage` permission used by enterprise data-source configuration and sync operations.
- Secret migration audit remains no-secret and is owned by the application service.

## Verification

- RED/GREEN endpoint permission test:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission" test`
- Contract regression for this slice:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#everyControllerEndpointIsDocumentedInApiContract,ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract,ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts,ContractSurfaceTest#dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission" test`
  - Result: `4/4` tests passed.
- Broader Java regression before commit:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest,DataSourceCredentialCodecTest,KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test`
  - Result: `66/66` tests passed.
- Static diff check:
  - `git diff --check`
  - Result: no whitespace errors; Git reported expected LF-to-CRLF working-tree warnings.

## Remaining Gaps

- Add an operational runbook or scheduler for periodic rotation-window execution.
- Run real Docker enterprise database/ERP/OA/finance connector smoke tests.
- Run live Higress route checks for the protected maintenance endpoint.
