# Delivery Closure 382: Production Gateway Target Guard

Date: 2026-07-08

## Scope

- Requirement: `REQ-AUTH-001` and cross-cutting customer production readiness
- Surface: `scripts/delivery-readiness-audit.mjs`, `scripts/production-readiness-env-check.mjs`
- Boundary: production WAF and OIDC evidence must target an explicit customer/production gateway, not the local Higress default

## Result

Production WAF blocking and production OIDC endpoint-security checks now fail closed unless `HIGRESS_GATEWAY_BASE_URL` is set to a non-local target URL.

The readiness audit no longer allows those production checks to run with the child smoke script's default `http://127.0.0.1:18000` gateway. If the gateway URL is missing or points at `localhost`, `127.0.0.1`, `::1`, or `0.0.0.0`, the production check is reported as blocked with `HIGRESS_GATEWAY_BASE_URL (non-local target URL)`.

The production readiness precheck CLI applies the same rule, so a customer handoff `.env` file that fills every other WAF/OIDC/TLS/model value still returns `ready=false` when the gateway target is local.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` first failed because WAF blocking and OIDC production checks were still built as executable commands when the target gateway was absent or local.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `24/24`.
- Regression: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs tests/unit/node/higress_waf_runtime_preflight.test.mjs tests/unit/node/higress_waf_policy_manifest.test.mjs tests/unit/node/higress_tls_certificate_smoke.test.mjs tests/unit/node/higress_oidc_local_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs` passed `80/80`.
- CLI precheck: `PRODUCTION_READINESS_ENV_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/production-readiness-env-check.mjs` returned `ready=false` with the expected WAF, gateway, TLS, OIDC, and credentialed smoke inputs missing.
- CLI precheck with a filled temporary env that used `HIGRESS_GATEWAY_BASE_URL=http://127.0.0.1:18000` returned `ready=false` with only `higress-waf-blocking-policy` and `higress-oidc-endpoint-security` blocked by `HIGRESS_GATEWAY_BASE_URL (non-local target URL)`, without echoing token/provider placeholders.
- Latest handoff refresh: `DELIVERY_READINESS_OUTPUT=markdown DELIVERY_READINESS_REPORT_FILE=docs/skill-chain/generated/production-readiness-action-plan.latest.md DELIVERY_READINESS_ENV_TEMPLATE_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/delivery-readiness-audit.mjs` exited `2` with `localReady=true` and `productionReady=false`; the generated action plan now reports WAF/OIDC production gateway checks as blocked instead of executing against the local default.
- Post-refresh runtime check: `node scripts/higress-gateway-smoke.mjs` returned `passed=true`, and `Invoke-WebRequest http://127.0.0.1:5173/` returned `HTTP 200`.

## Remaining Risk

This prevents local-gateway false positives, but does not supply the customer target evidence. Production readiness still requires a reachable WAF plugin image, active WAF blocking evidence, trusted TLS certificate evidence, customer OIDC endpoint evidence, and credentialed P0-P3 delivery smoke in the target environment.
