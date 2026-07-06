# Closure 300: Data Source Credential Re-encryption Job

Date: 2026-07-06

## Scope

- Requirement: `REQ-KB-003`
- User journey: `UC-07`
- Surface: stale enterprise data-source connector credential discovery and re-encryption.

## Result

The Java application layer now has a minimal credential re-encryption job for stale data-source connector secrets.

- `KnowledgeBaseRepository.findDataSourcesWithCredentials(limit)` discovers data sources that have stored connector credentials.
- `KnowledgeApplicationService.reencryptStaleDataSourceCredentials(limit)` filters non-current credentials with `DataSourceCredentialCodec.isCurrent()`.
- Stale credentials are decrypted through the current codec/keyring and immediately re-encrypted with the active key id.
- Updated data sources are persisted through the existing repository save path.
- A `knowledge_data_source_credential_reencrypted` audit log is written without `password` or `credentialSecret`.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#reencryptsStaleDataSourceCredentialsWithCurrentKeyAndAuditsWithoutSecrets" test` failed because `KnowledgeApplicationService.reencryptStaleDataSourceCredentials(int)` did not exist.
- GREEN: the same targeted test passed `1/1` after the migration method, repository query, and immutable model copy method were added.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=DataSourceCredentialCodecTest,KnowledgeApplicationServiceTest,ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test` passed `30/30`.

## Remaining Gaps

- A production runner remains to be wired, for example an admin-only maintenance endpoint, scheduled Spring job, or one-shot command runner.
- Real Docker enterprise database/ERP/OA/finance sync smoke and live Higress route checks still need to be rerun when local infrastructure is available.
