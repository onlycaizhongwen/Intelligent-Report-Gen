# Delivery Closure 310: UC-07 API Data Source Profile Cursor Governance

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: save-time alignment between API schema profile cursor fields and incremental sync cursor configuration.

## Result

- API data-source save validation now checks `cursorColumn` when `fieldMapping.profileId` is set.
- `oa-documents` requires both `fieldMapping.cursorField=id` and `cursorColumn=id`.
- `finance-vouchers` requires both `fieldMapping.cursorField=voucherId` and `cursorColumn=voucherId`.
- This prevents a profile-valid API row mapping from being saved with a different incremental cursor, which would otherwise create silent duplicate or skipped sync behavior.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: failed with `Tests run: 35, Failures: 1` because a `finance-vouchers` mapping with `cursorColumn=id` was saved without error.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: `Tests run: 35, Failures: 0, Errors: 0, Skipped: 0`.
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

- Customer-specific profile catalogs still need their own cursor rules before production rollout.
- Long-running duplicate/skip detection for already-saved legacy API data sources remains future production hardening work.
