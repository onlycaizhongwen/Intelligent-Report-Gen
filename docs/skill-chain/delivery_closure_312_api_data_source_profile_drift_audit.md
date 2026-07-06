# Delivery Closure 312: UC-07 API Data Source Profile Drift Audit

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: operational audit endpoint for already-saved API data sources whose `fieldMapping.profileId` no longer satisfies the current governed schema/profile/cursor rules.

## Result

- `GET /api/v1/data-sources/profile-drift` is exposed behind `datasource:manage`.
- The endpoint scans saved API data sources that declare `fieldMapping.profileId`, reuses the same mapping/profile/cursor validation path used by save-time and sync-time guards, and returns `scannedCount`, `driftCount`, and drift `items`.
- Drift items include `dataSourceId`, `name`, `sourceType`, `profileId`, `cursorColumn`, and the concrete `failureReason`; no credential plaintext or ciphertext is returned.
- Local Docker and Higress smoke scripts now exercise the profile-drift audit endpoint as part of UC-07 data-source validation.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#listsApiDataSourcesWithProfileDriftForOperationalAudit" test`
  - Result: failed because `auditDataSourceProfileDrift(int)` was not defined.
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts" test`
  - Result: failed because `GET /api/v1/data-sources/profile-drift` had no structured API contract.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#listsApiDataSourcesWithProfileDriftForOperationalAudit" test`
  - Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts" test`
  - Result: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`.
- Regression:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test`
  - Result: `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0`.
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest" test`
  - Result: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`.
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs`
  - Result: `tests 8`, `pass 8`, `fail 0`.
- Build/runtime:
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package`
  - Result: build success.
  - Replaced `/app/app.jar` in `ir-java-smoke`, restarted the container, and waited until Docker health returned `healthy`.
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`
  - Result: `passed=true`; ERP, OA, and finance sources each reported `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, imported title checks found, and `profileDriftCount=0`.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`; `GET /api/v1/data-sources/profile-drift?limit=10` returned `200` through Higress with `scannedCount=8`, `driftCount=0`, and `/api/v1/chat` remained behind the Java auth boundary.

## Remaining Gaps

- Customer-specific profile catalogs still need their own profile rules before production rollout.
- The audit endpoint currently reports drift but does not auto-migrate saved legacy data-source configs; migration should remain an explicit operator action.
