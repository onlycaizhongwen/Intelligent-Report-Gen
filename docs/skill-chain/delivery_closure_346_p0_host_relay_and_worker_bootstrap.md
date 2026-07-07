# Delivery Closure 346: P0 Host Relay And Worker Bootstrap

## Scope

- Requirements: `REQ-REPORT-001`, `REQ-REPORT-002`, `REQ-REPORT-004`, `REQ-AI-001`, `REQ-KB-002`
- User journeys: UC-01 intelligent report generation, UC-02 template filling, UC-04 export, UC-06 knowledge upload and OCR parsing
- Runtime boundary: local delivery smoke -> P0 smoke -> host-side provider relay -> Docker workers -> Java/PostgreSQL/MinIO/OpenSearch/Milvus/Higress

## Result

P0 local smoke now reaches a complete one-command local acceptance loop:

- Added a local smoke-only host relay for the external OpenAI-compatible provider.
  - Direct Docker TLS to DashScope still returns `SSL: UNEXPECTED_EOF_WHILE_READING`.
  - Host `curl`/Node can reach DashScope.
  - The worker container now calls `http://host.docker.internal:18091/compatible-mode/v1`, and the host relay forwards to DashScope.
  - Direct provider validation remains available with `P0_SMOKE_PROVIDER_RELAY=none`.
- Added `document-parse-worker-smoke` startup before UC-06 browser validation.
  - P0 no longer depends on a manually pre-started document parse worker.
  - The worker reuses local `postgres/minio/opensearch/milvus/rocketmq` on `intelligent-report-infra_default`.

## Evidence

- Root-cause evidence:
  - Host `curl.exe` reached DashScope `/compatible-mode/v1/models` and returned provider HTTP evidence.
  - Container `openssl s_client` to all DashScope IPv4 A records failed with TLS EOF.
  - Container TLS to `www.baidu.com`, `github.com`, and `openai.com` succeeded, narrowing the failure to the Docker-to-DashScope path.
  - OpenSSL 3.5, OpenSSL 3.0, and OpenSSL 1.1.1 based Python images all failed from Docker, ruling out a simple OpenSSL-version-only cause.
- RED: Node tests failed because `uc01-provider-host-relay-lib.mjs` and the P0 relay step were absent.
- GREEN: `node --test tests/unit/node/uc01_provider_host_relay.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs` passed `6/6`.
- RED: Node tests failed because `document-parse-worker-smoke-lib.mjs` and the P0 UC-06 worker bootstrap step were absent.
- GREEN: `node --test tests/unit/node/document_parse_worker_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs` passed `4/4`.
- Regression: `node --test tests/unit/node/document_parse_worker_smoke.test.mjs tests/unit/node/uc01_provider_host_relay.test.mjs tests/unit/node/uc01_real_provider_worker.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs` passed `15/15`.
- Runtime:
  - Relay preflight returned `status=200` for `/models` from the Docker worker network through `host.docker.internal:18091`.
  - Strict UC-01 smoke completed `taskId=357`, `reportId=548`, `provider=openai-compatible`, `modelName=qwen-plus`, `status=succeeded`, `totalTokens=618`.
  - Full `node scripts/p0-local-smoke.mjs` completed all 13 planned steps, including UC-06 processed browser E2E, UC-02 direct/Higress template completion, and UC-04 direct/Higress export flows.

## Files

- `scripts/uc01-provider-host-relay-lib.mjs`
- `scripts/uc01-provider-host-relay.mjs`
- `scripts/uc01-provider-host-relay-start.mjs`
- `scripts/document-parse-worker-smoke-lib.mjs`
- `scripts/document-parse-worker-smoke.mjs`
- `scripts/p0-local-smoke-lib.mjs`
- `scripts/p0-local-smoke.mjs`
- `tests/unit/node/uc01_provider_host_relay.test.mjs`
- `tests/unit/node/document_parse_worker_smoke.test.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- The host relay is a local smoke workaround, not a production architecture. Production should keep the provider reachable from the deployed worker/network directly or through a managed egress/proxy.
- Full P1-P3 delivery smoke should be re-run after this P0 closure to prove the top-level delivery runner with the new P0 defaults.
