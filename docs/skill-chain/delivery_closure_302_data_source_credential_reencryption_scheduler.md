# Delivery Closure 302: Data Source Credential Re-Encryption Scheduler

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: make stale data-source credential re-encryption operable as a scheduled maintenance task.

## Result

- Added `DataSourceCredentialReencryptionScheduler` as a Spring-managed component.
- The scheduler is default-off and only runs when `DATA_SOURCE_CREDENTIAL_REENCRYPTION_SCHEDULER_ENABLED=true`.
- Runtime knobs:
  - `DATA_SOURCE_CREDENTIAL_REENCRYPTION_SCHEDULER_ENABLED`, default `false`
  - `DATA_SOURCE_CREDENTIAL_REENCRYPTION_SCHEDULER_DELAY_MS`, default `86400000`
  - `DATA_SOURCE_CREDENTIAL_REENCRYPTION_SCHEDULER_LIMIT`, default `100`
- The scheduler delegates to `KnowledgeApplicationService.reencryptStaleDataSourceCredentials(limit)` and inherits the existing no-secret audit behavior.

## Operational Notes

- Keep the scheduler disabled by default in local and production profiles.
- Enable it only during an approved key-rotation window or an explicitly planned maintenance cycle.
- Start with a small `LIMIT` for the first production run, inspect audit counts, then increase gradually.
- Do not log credential plaintext, encrypted payloads, previous keys, or active key material.

## Verification

- RED/GREEN scheduler test:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#credentialReencryptionSchedulerUsesConfiguredScanLimit" test`
  - RED failed because `DataSourceCredentialReencryptionScheduler` did not exist.
  - GREEN passed `1/1` after adding the scheduler.
- Broader Java regression before commit:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest,DataSourceCredentialCodecTest,ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test`
  - Result: `31/31` tests passed.
- Static diff check to run before commit:
  - `git diff --check`
  - Result: no whitespace errors; Git reported expected LF-to-CRLF working-tree warnings.

## Remaining Gaps

- Run real Docker enterprise database/ERP/OA/finance sync smoke tests.
- Run live Higress route checks for the protected maintenance endpoint.
