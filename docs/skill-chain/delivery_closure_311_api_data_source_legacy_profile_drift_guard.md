# Delivery Closure 311: UC-07 API Data Source Legacy Profile Drift Guard

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: sync-time guard for legacy saved API data sources whose profile mapping no longer satisfies the current governed schema.

## Result

- `POST /api/v1/data-sources/{dataSourceId}/sync-runs` now reuses the same API mapping/profile/cursor validation used during data-source save.
- Legacy saved API data sources with `fieldMapping.profileId` drift, including `finance-vouchers` saved with `cursorColumn=id`, fail before acquiring the sync lease and before importing request-provided `sampleRows`.
- The failed sync run records the concrete profile validation message as `failureReason`, preserving operator visibility without mutating the saved data source or importing unsafe rows.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: failed with `Tests run: 36, Failures: 1` because a legacy `finance-vouchers` source with `cursorColumn=id` synced `sampleRows` successfully and imported 1 row.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`.
- Build/runtime:
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package`
  - Copied the rebuilt jar into `ir-java-smoke:/app/app.jar`, restarted `ir-java-smoke`, and waited until Docker health returned `healthy`.
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`
  - Result: `passed=true`; ERP, OA, and finance sources each reported `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, and imported title checks found.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`; Java auth boundary and data-source gateway checks returned expected outcomes.

## Remaining Gaps

- Customer-specific profile catalogs still need their own sync-time drift rules before production rollout.
- A bulk audit/report endpoint for existing legacy drifted data sources remains future production hardening work.
