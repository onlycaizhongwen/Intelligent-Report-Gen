# Delivery Closure 253: UC-01 Model Route Governance

Date: 2026-07-01

## Target

Close the UC-01 gap for higher-level model routing by tenant, scenario, and cost policy while keeping existing generation-mode and template routing.

## Changes

- Python LLM provider now resolves model candidates from:
  - tenant route
  - scenario route
  - cost policy route
  - generation mode route
  - template route
  - global candidate chain fallback
- Python generation result now carries `routing_policy`.
- Report generation worker now forwards `routingPolicy` in `modelInvocation`.
- Java report completion now merges `routingPolicy` into `model_invocations.parameters`, so model audit queries can explain why a route was selected.

## Verification

- RED confirmed missing behavior in Python provider, worker payload, and Java audit persistence.
- GREEN:
  - `python -m pytest tests/unit/python/test_llm_provider.py tests/unit/python/test_report_generation_worker.py -q`: 15 passed.
  - `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#completesTaskFromControlledAiCallbackWithReferencesAndModelAuditSummary test`: 1 passed.

## Remaining UC-01 Work

- Full P0 real provider smoke still requires live local infrastructure and a valid external provider key.
- More advanced fallback governance can still add budget ceilings, per-tenant quota, and provider health scoring.
