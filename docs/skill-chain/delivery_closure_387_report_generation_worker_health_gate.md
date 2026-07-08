# Closure 387: Report Generation Worker Health Gate

Date: 2026-07-08

## Scope

- Requirement: `REQ-REPORT-001`, UC-01 report generation and local customer handoff readiness.
- Delivery gate: the report generation worker must be explicit local readiness evidence, not only indirect evidence hidden inside the P0-P3 smoke bundle.
- Risk closed: delivery readiness could report local readiness without first-class evidence that `ir-report-generation-worker-smoke` was still running and healthy after the credentialed worker bootstrap path had been exercised.

## Change

- Added `scripts/report-generation-worker-smoke-lib.mjs` and `scripts/report-generation-worker-smoke.mjs`.
- The smoke inspects `ir-report-generation-worker-smoke` by default with `docker inspect --format '{{json .State}}'`.
- The worker must be `running` and expose Docker health status `healthy`.
- Failure output includes bounded Docker logs with sensitive values redacted.
- `buildDeliveryReadinessChecks(...)` now includes `report-generation-worker-health-smoke` as a required local gate after shared Docker dependency health and before UC-06 document parse worker health.
- Latest generated production readiness Markdown now renders report generation worker health under `Passed Local Evidence`.

## Verification

- RED: `node --test tests/unit/node/report_generation_worker_smoke.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` failed because the report generation worker smoke library, readiness gate, and generated handoff evidence were missing.
- GREEN partial: the same command passed all code-level tests after implementation, leaving only the generated Markdown freshness guard failing until live readiness regeneration.
- Runtime worker smoke: `node scripts/report-generation-worker-smoke.mjs` passed with `classification=report-generation-worker-healthy`, `containerName=ir-report-generation-worker-smoke`, and `state.healthStatus=healthy`.
- Runtime readiness regeneration: `DELIVERY_READINESS_OUTPUT=markdown DELIVERY_READINESS_REPORT_FILE=docs/skill-chain/generated/production-readiness-action-plan.latest.md DELIVERY_READINESS_ENV_TEMPLATE_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/delivery-readiness-audit.mjs` exited non-zero as expected for production blockers while reporting:
  - `Local ready: true`
  - `Passed local gates: frontend-browser-http, higress-default-security-smoke, higress-local-oidc-test-idp-smoke, local-docker-dependency-health-smoke, report-generation-worker-health-smoke, document-parse-worker-health-smoke`
  - `report-generation-worker-health-smoke classification=report-generation-worker-healthy`
  - `credentialed-delivery-smoke` passed with P0-P3 completed.

## Remaining Risk

- This improves local UC-01 worker health evidence and customer handoff diagnosis.
- It does not provide customer target WAF plugin reachability, active WAF blocking, trusted TLS certificate chain, customer OIDC token-suite/signing evidence, or target-environment production smoke evidence.
