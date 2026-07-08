# Closure 383: Document Parse Worker Startup Guard

Date: 2026-07-08

## Scope

- Requirement: `REQ-KB-001`, `REQ-KB-002`, UC-06 knowledge upload and document parsing.
- Risk closed: the UC-06 P0 bootstrap could previously pass after `docker run -d` returned a container id, even if the document parse worker exited immediately before consuming RocketMQ document parse events or writing OpenSearch/Milvus evidence.

## Change

- `scripts/document-parse-worker-smoke.mjs` now waits for a short stabilization window after starting the worker container.
- The smoke then runs `docker inspect --format '{{json .State}}'` against the worker container and fails closed unless the container remains `running`.
- If the worker is not running, the smoke captures a bounded `docker logs --tail 80` diagnostic and redacts sensitive values such as passwords, tokens, keys, and database URLs.
- The smoke output now includes structured `passed`, `classification`, `state`, container, topic, OpenSearch, Milvus, and MinIO evidence so P0 evidence can distinguish a real running worker from a transient container id.

## Verification

- RED: `node --test tests/unit/node/document_parse_worker_smoke.test.mjs` failed because `buildDockerInspectArgs` was not exported.
- GREEN: `node --test tests/unit/node/document_parse_worker_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs` passed `9/9`.
- Runtime smoke: `DOCUMENT_PARSE_WORKER_STABILIZATION_MS=2500 node scripts/document-parse-worker-smoke.mjs` returned:
  - `passed=true`
  - `classification=document-parse-worker-running`
  - `containerName=ir-document-parse-worker-smoke`
  - `topic=document_parse_requested`
  - `opensearchUrl=http://ir-opensearch:9200`
  - `milvusHost=ir-milvus`
  - `state.status=running`

## Remaining Risk

- This closes worker startup evidence only. Full UC-06 production confidence still requires document upload events to be consumed in the customer target environment, OpenSearch/Milvus indexing to be observed with customer-scale documents, and production WAF/TLS/OIDC evidence to pass at the target gateway.
