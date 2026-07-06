# Delivery Closure 308: UC-07 API Data Source Mapping Governance

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: fail-fast governance for advanced API connector mapping options.

## Result

- API data-source save now validates advanced `fieldMapping` options before persistence.
- Invalid HTTP methods are rejected unless they are `GET` or `POST`.
- Invalid auth types are rejected unless they are `bearer`, `api_key`, `basic`, or `none`.
- `maxPages`, `pageSize`, and `pageStart` are range-checked during save instead of waiting until sync execution.
- Reserved outbound headers such as `Authorization`, `Content-Length`, and `Host` are rejected in user-provided custom headers and `apiKeyHeader`.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Failed because an unsafe API mapping was saved without error.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: `Tests run: 33, Failures: 0, Errors: 0, Skipped: 0`.
- Build/runtime:
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package`
  - Copied the rebuilt jar into `ir-java-smoke:/app/app.jar`, restarted the container, and waited until Docker health returned `healthy`.
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`
  - Result: `passed=true`; `erp-mysql`, `oa-api`, and `finance-api` each synced 2 rows successfully.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`.
- Node regression:
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs`
  - Result: `9/9` passed.

## Remaining Gaps

- Customer-specific ERP/OA/finance schema profiles remain future production hardening work.
- Production OIDC/TLS/WAF checks remain open production hardening work.
