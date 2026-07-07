# Delivery Closure 344: UC-01 Provider Failure Diagnostics

## Scope

- Requirement: `REQ-REPORT-001`, `REQ-AI-001`
- User journey: natural-language report generation through the external provider worker
- Runtime boundary: delivery smoke -> P0 smoke -> RocketMQ worker -> external OpenAI-compatible provider -> Java completion callback and PostgreSQL model audit

## Result

The full delivery smoke currently reaches UC-01 but fails strict provider audit because the local Docker worker cannot complete TLS to the configured external provider. This closure makes that failure diagnosable and repeatable:

- `uc01-real-provider-smoke.mjs` now reads `error_code` and `error_message` from `model_invocations`.
- `parseModelInvocationLine(...)` preserves nullable token and provider failure diagnostics in smoke output.
- `RocketMqReportGenerationSource` now logs report-generation worker callback failures with `topic`, `tag`, `eventKey`, and `taskId` before returning `RECONSUME_LATER`.

## Evidence

- RED: `node --test tests/unit/node/uc01_real_provider_smoke.test.mjs` failed because `parseModelInvocationLine` was not exported and the SQL did not include provider failure diagnostics.
- GREEN: `node --test tests/unit/node/uc01_real_provider_smoke.test.mjs` passed `7/7`.
- RED: `python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py -q` failed because the report-generation RocketMQ callback swallowed worker exceptions without logs.
- GREEN: `python -m pytest tests/unit/python/test_rocketmq_worker_adapter.py -q` passed `6/6`.
- Runtime diagnostic: `UC01_SMOKE_POSTGRES_CONTAINER=ir-postgres UC01_SMOKE_STRICT_AUDIT=true node scripts/uc01-real-provider-smoke.mjs` still failed strict audit, but now printed `provider=local-fallback`, `status=fallback_succeeded`, and `errorMessage=<urlopen error [SSL: UNEXPECTED_EOF_WHILE_READING] EOF occurred in violation of protocol (_ssl.c:1010)>`.
- Container probe: the report generation worker container has a CA bundle and DNS resolution for `dashscope.aliyuncs.com`, but a direct Python HTTPS request to the DashScope compatible endpoint reproduces the same SSL EOF error.

## Files

- `scripts/uc01-real-provider-smoke-lib.mjs`
- `scripts/uc01-real-provider-smoke.mjs`
- `tests/unit/node/uc01_real_provider_smoke.test.mjs`
- `backend/python-ai-service/app/report_generation/application/rocketmq_worker.py`
- `tests/unit/python/test_rocketmq_worker_adapter.py`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- Full one-command delivery smoke is still blocked by the local Docker-to-provider TLS failure.
- The next closure should either provide a local configurable network/proxy path for the worker container or add a production-like provider connectivity preflight before spending the full P0-P3 smoke budget.
