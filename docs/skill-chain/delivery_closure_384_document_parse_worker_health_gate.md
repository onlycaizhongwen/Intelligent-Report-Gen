# Closure 384: Document Parse Worker Health Gate

Date: 2026-07-08

## Scope

- Requirement: `REQ-KB-001`, `REQ-KB-002`, UC-06 knowledge upload and document parsing.
- Risk closed: the UC-06 document parse worker smoke could prove the container stayed running, but it did not yet require Docker healthcheck evidence from the Python service image.

## Change

- `parseContainerState(...)` now records Docker `Health.Status` and `Health.FailingStreak`.
- `summarizeWorkerStartup(...)` now passes only when the worker is running and, when Docker healthcheck is present, the health status is `healthy`.
- The CLI now polls `starting` health status until healthy or timeout instead of treating the first inspect result as final.
- Unhealthy containers fail closed with `classification=document-parse-worker-unhealthy` and bounded redacted logs.

## Verification

- RED: `node --test tests/unit/node/document_parse_worker_smoke.test.mjs` failed because health fields were missing, healthy classification was still `document-parse-worker-running`, and unhealthy state still passed.
- GREEN: `node --test tests/unit/node/document_parse_worker_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs` passed `10/10`.
- Runtime smoke: `DOCUMENT_PARSE_WORKER_STABILIZATION_MS=1000 DOCUMENT_PARSE_WORKER_HEALTH_TIMEOUT_MS=50000 node scripts/document-parse-worker-smoke.mjs` returned:
  - `passed=true`
  - `classification=document-parse-worker-healthy`
  - `state.status=running`
  - `state.healthStatus=healthy`
  - `state.healthFailingStreak=0`

## Remaining Risk

- This closes local worker health evidence. It does not replace customer target document upload, RocketMQ consumption, OpenSearch/Milvus indexing, production WAF, trusted TLS, OIDC, or credentialed P0-P3 evidence.
