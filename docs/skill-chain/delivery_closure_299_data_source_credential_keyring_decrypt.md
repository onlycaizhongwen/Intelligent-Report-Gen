# Closure 299: Data Source Credential Keyring Decrypt

Date: 2026-07-06

## Scope

- Requirement: `REQ-KB-003`
- User journey: `UC-07`
- Surface: enterprise data-source connector credential decryption during credential key rotation.

## Result

Data-source connector credential rotation now has a minimal keyring decrypt path.

- Newly encrypted secrets still use the active `security.data-source-credential-key-id` and `security.data-source-credential-key`.
- `security.data-source-credential-previous-keys` can provide retired keys as comma-separated `keyId=keyMaterial` entries.
- `enc:v2:<keyId>:` payloads are decrypted with the matching keyring entry instead of assuming the active key material.
- `isCurrent()` remains strict: a retired-key payload can be decrypted but is still marked non-current so later code can schedule re-encryption.
- Existing `enc:v1:` AES-GCM payloads and legacy `enc:` Base64 payloads remain compatible with the current codec.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=DataSourceCredentialCodecTest" test` failed because `DataSourceCredentialCodec` had no constructor/keyring path for retired key material.
- GREEN: the same targeted codec test passed `2/2` after keyring decrypt support was added.

## Remaining Gaps

- A stale-secret discovery and re-encryption job is still needed to migrate old `enc:v1:` and retired `enc:v2:<keyId>:` payloads to the active key.
- Real Docker enterprise database/ERP/OA/finance sync smoke and live Higress route checks still need to be rerun when local infrastructure is available.
