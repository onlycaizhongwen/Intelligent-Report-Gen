# Delivery Closure 376: Gateway Target Readiness Inputs

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit-lib.mjs`
- Boundary: require explicit target gateway URL for production WAF and OIDC readiness evidence

## Result

Production WAF and OIDC readiness evidence now requires the target gateway URL instead of silently relying on the local Higress default.

Behavior:

- `higress-waf-blocking-policy` requires `HIGRESS_GATEWAY_BASE_URL` and `HIGRESS_WAF_BLOCKING_COVERAGE`.
- `higress-oidc-endpoint-security` requires `HIGRESS_GATEWAY_BASE_URL` for both supported OIDC evidence paths.
- Readiness command examples now show `HIGRESS_GATEWAY_BASE_URL=<target-gateway-url>`.
- The generated production readiness `.env` example includes `HIGRESS_GATEWAY_BASE_URL` for WAF blocking and OIDC endpoint security.
- The production readiness precheck reports missing target gateway URL before long smokes run.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because WAF blocking did not require `HIGRESS_GATEWAY_BASE_URL`, and the precheck did not list the missing gateway target.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs` passed `18/18`.

## Remaining Risk

This closes an operator handoff gap, not the production evidence itself. Target environments still must run and pass WAF plugin runtime fetch, WAF blocking, trusted TLS, OIDC endpoint security, and credentialed P0-P3 smoke checks.
