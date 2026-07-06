# Delivery Closure 313: UC-07 API Data Source Profile Drift Repair

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: explicit operator-driven repair for one already-saved API data source whose `fieldMapping.profileId` no longer satisfies the current governed schema/profile/cursor rules.

## Result

- `POST /api/v1/data-sources/{dataSourceId}/profile-drift/repair` is exposed behind `datasource:manage`.
- The endpoint defaults to dry-run preview. It returns the current failure reason, previous cursor, proposed cursor, and proposed canonical field mapping without mutating the data source.
- When `confirmed=true`, the endpoint overlays the current built-in profile's required mapping fields, updates `cursorColumn`, clears stale `lastCursor` if the cursor field changes, and writes `knowledge_data_source_profile_repaired` audit evidence.
- The repair response and smoke scripts do not expose credential plaintext or ciphertext.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#previewsApiDataSourceProfileDriftRepairWithoutPersistingChanges+confirmsApiDataSourceProfileDriftRepairAndAllowsNextSync" test`
  - Result: failed because `repairDataSourceProfileDrift(Long, Map<String,Object>)` was not defined.
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts+dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission" test`
  - Result: failed because the controller endpoint and structured API contract were missing.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest#previewsApiDataSourceProfileDriftRepairWithoutPersistingChanges+confirmsApiDataSourceProfileDriftRepairAndAllowsNextSync" test`
  - Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=ContractSurfaceTest#dataSourceSyncRunEndpointsDeclareFieldLevelResponseContracts+dataSourceCredentialMaintenanceEndpointRequiresDataSourceManagePermission" test`
  - Result: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
- Regression:
  - `.\mvnw.cmd -pl backend/java-report-core "-Dtest=KnowledgeApplicationServiceTest" test`
  - Result: `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0`.
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
  - Result: `passed=true`; ERP, OA, and finance sources each synced successfully. OA and finance repair dry-runs returned `repaired=false` and `requiresConfirmation=false` for valid profiles without exposing `enc:v1:` values.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`; `POST /api/v1/data-sources/999999999/profile-drift/repair` returned expected Java-routed `404`, and `/api/v1/chat` remained behind the Java auth boundary.

## Remaining Gaps

- Customer-specific profile catalogs still need their own canonical repair rules before production rollout.
- Bulk repair remains intentionally absent; operators should audit, preview, and confirm individual repairs until customer migration policy is defined.
