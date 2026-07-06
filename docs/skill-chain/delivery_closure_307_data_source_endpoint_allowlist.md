# Delivery Closure 307: UC-07 Data Source Endpoint Allowlist

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: outbound data-source endpoint allowlist and SSRF guard for enterprise connectors.

## Result

- Added `DataSourceEndpointAllowlist` for HTTP API and PostgreSQL/MySQL JDBC endpoints.
- `saveDataSource` now rejects configured API/JDBC endpoints whose host is outside the allowlist.
- `testConnection` now refuses to open outbound connections for disallowed endpoints.
- `startDataSourceSync` now checks the endpoint before acquiring a sync lease or reading request-provided `sampleRows`, preventing historical or repository-seeded data sources from bypassing the outbound boundary.
- Local development keeps Docker reuse working through `DATA_SOURCE_ENDPOINT_ALLOWLIST` defaults for loopback and local service names.
- Production profile requires `DATA_SOURCE_ENDPOINT_ALLOWLIST` to be supplied explicitly.

## Configuration

- Dev default:
  - `localhost`
  - `127.0.0.1`
  - `::1`
  - `host.docker.internal`
  - `postgres`
  - `mysql`
  - `ir-postgres`
  - `intelligent-report-system-mysql-1`
- Production:
  - `application-prod.yml` reads `DATA_SOURCE_ENDPOINT_ALLOWLIST` without a permissive default.

## Verification

- RED:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Failed because disallowed API/JDBC endpoints were saved successfully and a disallowed historical API endpoint could sync request-provided rows.
- GREEN:
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=KnowledgeApplicationServiceTest test`
  - Result: `Tests run: 32, Failures: 0, Errors: 0, Skipped: 0`.
- Build/runtime:
  - `.\mvnw.cmd -pl backend/java-report-core -DskipTests package`
  - Copied the rebuilt jar into `ir-java-smoke:/app/app.jar`, restarted the container, and waited until Docker health returned `healthy`.
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`
  - Result: `passed=true`.
  - `erp-mysql`, `oa-api`, and `finance-api` each reported `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, and `lastCursor=2`.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`.
- Node regression:
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs`
  - Result: `9/9` passed.

## Remaining Gaps

- Production OIDC/TLS/WAF checks remain open production hardening work.
- Customer-specific connector schema governance and ERP/OA/finance field mapping profiles remain future hardening work.
