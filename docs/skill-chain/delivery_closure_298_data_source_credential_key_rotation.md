# Closure 298: Data Source Credential Key Rotation Marker

Date: 2026-07-06

## Scope

- Requirement: `REQ-KB-003`
- User journey: `UC-07`
- Surface: enterprise data-source connector credential storage, encryption metadata, and rotation readiness.

## Result

Data-source connector secrets now carry an explicit credential key identifier in newly encrypted payloads.

- `DataSourceCredentialCodec` now emits `enc:v2:<keyId>:` payloads for Spring-managed runtime encryption.
- `security.data-source-credential-key-id` is configured separately from `security.data-source-credential-key` in `application-dev.yml` and `application-prod.yml`.
- Existing `enc:v1:` AES-GCM payloads and legacy `enc:` Base64 payloads remain readable by the current codec, preserving compatibility for already stored local-development fixtures and older test data.
- `isCurrent()` now checks the exact active prefix, so services can detect secrets encrypted under a retired key identifier and schedule re-encryption.
- Key identifiers are normalized before being written into the encrypted payload prefix to avoid unsafe delimiter characters.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=DataSourceCredentialCodecTest" test` first failed because the codec had no key-id-aware constructor, then failed because encryption still emitted the hardcoded `enc:v1:` prefix.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=DataSourceCredentialCodecTest" test` passed `1/1`.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=DataSourceCredentialCodecTest,KnowledgeApplicationServiceTest" test` passed `26/26`.
- Profile guard: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ReportCoreApplicationContextTest,ReportCoreProdProfileContextTest" test` passed `2/2`.

## Remaining Gaps

- Full multi-keyring decrypt support is not implemented yet; rotating the key material itself still requires an operational migration or explicit keyring configuration.
- No scheduled re-encryption job exists yet for stale `enc:v1:` or retired `enc:v2:<keyId>:` payloads.
- Real Docker enterprise database/ERP/OA/finance sync smoke and live Higress route checks still need to be rerun when the local Docker daemon and gateway dependencies are available.
