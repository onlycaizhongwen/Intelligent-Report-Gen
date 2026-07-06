# Delivery Closure 305: UC-07 Data Source Higress Route Smoke

## Scope

- Requirement: `REQ-KB-003`
- Capability: `knowledge-base-ingestion`
- Slice: local Higress gateway route and authorization smoke for enterprise data-source presets, save validation, and sync trigger boundaries.

## Result

- Extended the repeatable local Higress smoke to cover UC-07 data-source endpoints:
  - `GET /api/v1/data-sources/presets`
  - `POST /api/v1/data-sources`
  - `POST /api/v1/data-sources/{dataSourceId}/sync-runs`
- The smoke now proves:
  - Missing token returns Java `401`.
  - Valid JWT without data-source permission returns Java `403`.
  - Valid JWT with `datasource:manage` returns `200` for presets and includes enterprise templates.
  - Invalid save payload returns Java `400` validation response.
  - Missing data source sync trigger returns Java `404`, preserving resource-not-found semantics through Higress.
- Kept Python `/api/v1/chat` out of the public gateway path by verifying it also reaches Java auth and returns `401`.
- Updated local Higress endpoint data to route Java traffic through the stable Docker host gateway `172.30.0.1:18082`, avoiding stale Java container IP drift after restarts.

## Runtime Path

```text
browser/client -> Higress :18000 -> Java report core :18082 -> PostgreSQL/RBAC/data-source service
```

## Verification

- RED:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - Failed while `data-source-sync-not-found-through-higress` still expected `400/400`.
- GREEN:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - Result: `6/6` passed.
- Live Docker/Higress smoke:
  - `docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"`
  - Observed `ir-higress`, `ir-java-smoke`, `ir-postgres`, `ir-redis`, `ir-minio`, `ir-milvus`, `ir-opensearch`, and RocketMQ containers running locally; no new dependency was deployed for this slice.
  - `node scripts/higress-gateway-smoke.mjs`
  - Result: `passed=true`; data-source route checks returned `401`, `403`, `200`, `400`, and `404` as expected.

## Remaining Gaps

- Run real Docker enterprise database/ERP/OA/finance sync smoke tests against representative external source containers/services.
- Add production OIDC/TLS/WAF checks for the complete endpoint authorization matrix.
