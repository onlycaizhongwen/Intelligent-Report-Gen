# Closure 385: Readiness Local Worker Gate

Date: 2026-07-08

## Scope

- Requirement: `REQ-KB-001`, `REQ-KB-002`, UC-06 knowledge upload and document parsing.
- Delivery gate: customer handoff readiness evidence.
- Risk closed: delivery readiness could report `localReady=true` without explicitly running the UC-06 document parse worker health smoke, even though P0 depends on that worker for the knowledge upload and parsing journey.

## Change

- `buildDeliveryReadinessChecks(...)` now includes a required local `document-parse-worker-health-smoke` command.
- The new gate runs `node scripts/document-parse-worker-smoke.mjs` with a 90 second timeout and healthcheck wait settings.
- Credentialed P0-P3 delivery smoke timeout increased from 300 seconds to 600 seconds, because the full bundle can legitimately run just over five minutes on the local Docker stack.
- Readiness Markdown now renders `Passed local gates` and a `Passed Local Evidence` section.
- Nested evidence values now render as JSON instead of `[object Object]`, so OIDC probe arrays and worker Docker state remain inspectable in generated handoff documents.

## Verification

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because the local worker gate was missing.
- RED: the same test failed because credentialed delivery timeout remained `300000` and local evidence did not render in Markdown.
- RED: the Markdown evidence test failed because worker `state` rendered as `[object Object]`.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/document_parse_worker_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs` passed `34/34`.
- Runtime readiness: `DELIVERY_READINESS_OUTPUT=markdown DELIVERY_READINESS_REPORT_FILE=docs/skill-chain/generated/production-readiness-action-plan.latest.md DELIVERY_READINESS_ENV_TEMPLATE_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/delivery-readiness-audit.mjs` exited `1` as expected for remaining production blockers, while reporting:
  - `Local ready: true`
  - `Passed local gates: frontend-browser-http, higress-default-security-smoke, higress-local-oidc-test-idp-smoke, document-parse-worker-health-smoke`
  - `document-parse-worker-health-smoke classification=document-parse-worker-healthy`
  - `state.healthStatus=healthy`
  - `Passed production gates: credentialed-delivery-smoke`
  - Production blockers limited to WAF runtime preflight, WAF blocking policy, trusted TLS, and OIDC endpoint security.

## Remaining Risk

- This improves local/customer handoff evidence and confirms the credentialed P0-P3 bundle can complete under the wider timeout. It does not provide customer target WAF plugin reachability, active WAF blocking, trusted TLS certificate chain, or customer OIDC token-suite/signing evidence.
