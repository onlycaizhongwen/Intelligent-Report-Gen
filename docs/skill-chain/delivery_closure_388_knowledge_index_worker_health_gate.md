# Delivery Closure 388: Knowledge Index Worker Health Gate

Date: 2026-07-08

## Scope

This closure strengthens UC-05 knowledge-base handoff readiness. Local delivery readiness now proves that both asynchronous knowledge item workers can run against the local Docker stack:

- `knowledge-index-cleanup-worker` for `knowledge.item.deleted`
- `knowledge-item-index-worker` for `knowledge.item.index_requested`

## Changes

- Added `scripts/knowledge-index-worker-smoke-lib.mjs` and `scripts/knowledge-index-worker-smoke.mjs`.
- Added `tests/unit/node/knowledge_index_worker_smoke.test.mjs`.
- Added required local readiness gate `knowledge-index-worker-health-smoke`.
- Isolated production evidence environment variables from local command gates so blank customer readiness templates cannot force local Higress smokes into production WAF/OIDC/TLS behavior.
- Regenerated `docs/skill-chain/generated/production-readiness-action-plan.latest.md` and `docs/skill-chain/generated/production-readiness.env.example`.

## Verification

- RED: `node --test tests/unit/node/knowledge_index_worker_smoke.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` failed for the missing worker smoke library, missing readiness gate, and stale generated handoff evidence.
- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs --test-name-pattern "sanitizeCommandEnv"` failed because blank customer evidence values were still passed to child smokes.
- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs --test-name-pattern "buildCommandExecutionEnv"` failed because the environment isolation helper did not exist.
- GREEN: `node --test tests/unit/node/knowledge_index_worker_smoke.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` passed `32/32`.
- Runtime: `node scripts/knowledge-index-worker-smoke.mjs` returned `passed=true`, `classification=knowledge-index-workers-running`, `workerCount=2`, and both smoke containers reported `healthStatus=healthy`.
- Runtime: full readiness regeneration with `DELIVERY_READINESS_ENV_FILE=docs/skill-chain/generated/production-readiness.env.example` returned `Local ready: true`, included `knowledge-index-worker-health-smoke` in passed local gates, and kept production readiness false.
- Browser entrypoint evidence remains `http://127.0.0.1:5173/` returning `200 OK`.

## Remaining Risk

This is local UC-05 worker health evidence. It does not replace customer/target proof for:

- WAF plugin reachability from the Higress runtime.
- Active WAF blocking for representative attacks.
- Trusted TLS certificate chain and hostname verification.
- OIDC endpoint security in the target/customer environment.
- Credentialed P0-P3 smoke with a real provider key.
- Customer-scale knowledge indexing and cleanup volume evidence.
