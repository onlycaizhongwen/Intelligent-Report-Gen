# Delivery Closure 306: UC-07 Enterprise Data Source Docker Sync Smoke

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: repeatable local Docker smoke for representative ERP database, OA API, and finance API data-source synchronization.

## Result

- Added `scripts/data-source-enterprise-docker-smoke.mjs` and reusable `scripts/data-source-enterprise-docker-smoke-lib.mjs`.
- The smoke reuses local Docker dependencies:
  - Java API: `ir-java-smoke` at `http://127.0.0.1:18082/api/v1`
  - PostgreSQL app store: `ir-postgres`
  - ERP source database: existing `intelligent-report-system-mysql-1` on host port `13306`
- The script starts the existing MySQL container when it is stopped, waits for health, seeds a temporary `smoke_erp_orders` source table, and avoids deploying additional heavyweight middleware.
- OA and finance source systems are represented by an in-process HTTP fixture that the Java container reaches through `host.docker.internal`.
- Each source is exercised through the real Java API:
  - create knowledge base
  - save data source
  - test connection
  - start sync run
  - verify imported knowledge items can be searched back

## Covered Sources

- `erp-mysql`: JDBC MySQL source, 2 rows imported, cursor `id`, last cursor `2`.
- `oa-api`: HTTP GET source with Bearer auth, custom header, pagination, 2 rows imported.
- `finance-api`: HTTP POST source with API-key header, custom tenant header, body template, pagination, 2 rows imported.

## Verification

- RED:
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs`
  - Failed because `scripts/data-source-enterprise-docker-smoke-lib.mjs` did not exist and P3 had only 9 steps.
- GREEN:
  - `node --test tests/unit/node/data_source_enterprise_docker_smoke.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs`
  - Result: `3/3` passed.
- Live Docker smoke:
  - `node scripts/data-source-enterprise-docker-smoke.mjs`
  - Result: `passed=true`.
  - `erp-mysql`: `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, `lastCursor=2`.
  - `oa-api`: `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, `lastCursor=2`.
  - `finance-api`: `connectionSuccess=true`, `syncStatus=succeeded`, `processedRows=2`, `lastCursor=2`.
- Docker evidence:
  - `ir-java-smoke` healthy on `18082`.
  - `ir-postgres` healthy.
  - `intelligent-report-system-mysql-1` healthy on `13306`.
  - `ir-higress` still running on `18000`.

## Remaining Gaps

- Production OIDC/TLS/WAF checks for the complete endpoint authorization matrix.
- Long-running production connector hardening such as credential rotation runbooks and customer-specific ERP/OA/finance schemas. Source allowlist enforcement is covered by closure 307.
