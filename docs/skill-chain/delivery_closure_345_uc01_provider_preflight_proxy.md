# Delivery Closure 345: UC-01 Provider Preflight And Proxy Propagation

## Scope

- Requirement: `REQ-REPORT-001`, `REQ-AI-001`
- User journey: natural-language report generation through the external OpenAI-compatible provider worker
- Runtime boundary: delivery smoke -> P0 smoke -> provider preflight container -> RocketMQ worker container -> external provider

## Result

UC-01 delivery validation now checks external provider connectivity from the same Docker image and Docker network before spending the full P0 smoke budget:

- Added `scripts/uc01-provider-connectivity-preflight.mjs`.
- P0 smoke now runs `uc01-provider-preflight` before `uc01-worker` and `uc01-strict-smoke`.
- Delivery smoke now propagates host `HTTPS_PROXY`, `HTTP_PROXY`, and `NO_PROXY` into P0.
- The UC-01 worker container also receives those proxy variables.
- Docker run arguments can pass `LLM_API_KEY` by environment reference, avoiding secret values in failed command strings.

## Evidence

- RED: `node --test tests/unit/node/uc01_real_provider_worker.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs` failed because provider preflight config was not exported, P0 still had 10 steps, and delivery did not pass proxy env.
- GREEN: the same Node command passed `8/8`.
- RED security check: `buildDockerRunArgs` serialized `LLM_API_KEY=secret-key` even when sensitive env reference mode was requested.
- GREEN security check: `buildDockerRunArgs(..., { referenceEnvKeys: ['LLM_API_KEY'] })` emits `-e LLM_API_KEY` without the secret value.
- Runtime preflight: `node scripts/uc01-provider-connectivity-preflight.mjs` ran inside `intelligent-report-system-python-ai-service` on `intelligent-report-infra_default` and failed early with a sanitized diagnostic:
  - URL: `https://dashscope.aliyuncs.com/compatible-mode/v1/models`
  - Error: `URLError` / `SSL: UNEXPECTED_EOF_WHILE_READING`
  - Proxy flags: `https=false`, `http=false`, `noProxy=""`
  - OpenSSL: `OpenSSL 3.5.5 27 Jan 2026`

## Files

- `scripts/uc01-real-provider-worker-lib.mjs`
- `scripts/uc01-real-provider-worker.mjs`
- `scripts/uc01-provider-connectivity-preflight.mjs`
- `scripts/p0-local-smoke-lib.mjs`
- `scripts/p0-local-smoke.mjs`
- `scripts/delivery-local-smoke-lib.mjs`
- `scripts/delivery-local-smoke.mjs`
- `tests/unit/node/uc01_real_provider_worker.test.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`
- `tests/unit/node/delivery_local_smoke_bundle.test.mjs`

## Remaining Risk

- Full one-command delivery smoke is still blocked until the local Docker runtime has a working route to the provider endpoint. The current shell has `DASHSCOPE_API_KEY` set but no `HTTPS_PROXY`, `HTTP_PROXY`, or `NO_PROXY` variables.
- Browser access from the host does not prove container access; the preflight remains the acceptance gate because it uses the same container network boundary as the worker.
