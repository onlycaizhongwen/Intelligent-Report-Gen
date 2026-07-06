# Delivery Closure 309: UC-07 API Data Source Schema Profiles

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: built-in enterprise API schema profiles for OA documents and finance vouchers.

## Result

- API data-source presets now include `fieldMapping.profileId` so users can apply governed OA and finance connector templates without free-form schema drift.
- `oa-api` uses profile `oa-documents` with `rowsPath=data.documents`, `titleField=documentNo`, `contentField=content`, `cursorField=id`, `method=GET`, and `authType=bearer`.
- `finance-api` uses profile `finance-vouchers` with `rowsPath=data.vouchers`, `titleField=voucherNo`, `contentField=summary`, `cursorField=voucherId`, `method=POST`, `authType=api_key`, `apiKeyHeader=X-API-Key`, and `headers.X-Tenant=finance`.
- Saving an API data source with an unknown profile or a mapping that does not match the selected built-in profile fails before persistence.
- The local Docker smoke fixture and frontend API type both carry `profileId`, keeping UI presets, backend validation, and real sync smoke aligned.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: failed with 34 tests and 2 failures because presets did not include `profileId` and mismatched profile mappings were accepted.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: `Tests run: 34, Failures: 0, Errors: 0, Skipped: 0`.
- Node regression:
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs`
  - Result: `9/9` passed.
- Frontend type contract:
  - `npm run typecheck` in `frontend/web-console`
  - Result: passed.
- Build/runtime:
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package`
  - Copied the rebuilt jar into `ir-java-smoke:/app/app.jar`, restarted `ir-java-smoke`, and waited until Docker health returned `healthy`.
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`
  - Result: `passed=true`; `erp-mysql`, `oa-api`, and `finance-api` each reported `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, and imported title checks found. Finance used `POST`, API Key, `X-Tenant`, and `voucherId` cursor fields.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`; `/api/v1/chat` remained behind the Java auth boundary and data-source routes returned expected `401/403/200/400/404` outcomes.

## Remaining Gaps

- Customer-specific ERP/OA/finance schema variants still need explicit future profiles or tenant configuration before production rollout.
- Production OIDC/TLS/WAF checks remain open production hardening work.
