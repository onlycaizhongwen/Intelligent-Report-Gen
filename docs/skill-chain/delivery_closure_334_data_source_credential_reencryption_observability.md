# Delivery Closure 334: UC-07 Data Source Credential Re-encryption Observability

## Scope

- Requirement: `REQ-KB-003`
- Journey: `UC-07` enterprise data-source credential rotation and maintenance
- Endpoint: `POST /api/v1/data-sources/credentials/reencrypt`
- Scheduler: `DataSourceCredentialReencryptionScheduler`

## Result

Data-source credential re-encryption jobs now leave aggregate run evidence even when no credential is migrated:

- `KnowledgeApplicationService.reencryptStaleDataSourceCredentials(...)` still returns `scannedCount` and `migratedCount`.
- Every run now writes `knowledge_data_source_credential_reencryption_run`.
- The run audit includes `limit`, `scannedCount`, and `migratedCount`.
- The run audit uses `resourceType=knowledge_data_source` and `resourceId=null`, making it a maintenance-job record rather than a specific data-source record.
- The run audit does not include `credentialSecret`, `password`, or `plainSecret`.
- Existing per-data-source `knowledge_data_source_credential_reencrypted` audit entries remain unchanged for migrated credentials.

## Evidence

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#reencryptsStaleDataSourceCredentialsWritesRunSummaryAuditWithoutSecrets" test` first failed because no `knowledge_data_source_credential_reencryption_run` audit was written when the scanned credential was already current.
- GREEN: the same targeted summary-audit test passed `1/1`.
- Credential re-encryption regression: targeted re-encryption/scheduler regression passed `3/3`.
- Knowledge/data-source regression: `KnowledgeApplicationServiceTest` passed `42/42`.
- Contract guard: `ContractSurfaceTest#everyControllerEndpointHasStructuredJsonContract` passed `1/1`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/knowledge/application/KnowledgeApplicationService.java`
- `backend/java-report-core/src/test/java/com/company/report/knowledge/application/KnowledgeApplicationServiceTest.java`
- `docs/skill-chain/api_contract.md`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure adds application-level run evidence, not a full metrics dashboard.
- Production OIDC/TLS/WAF checks and broader operations dashboards remain future hardening work.
