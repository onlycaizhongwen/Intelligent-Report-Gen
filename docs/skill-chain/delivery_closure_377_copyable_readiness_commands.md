# Delivery Closure 377: Copyable Production Readiness Commands

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit-lib.mjs`
- Boundary: production blocker action-plan command examples

## Result

Production readiness action plans now render copyable command examples with explicit environment placeholders for required inputs.

Behavior:

- WAF runtime preflight commands show `HIGRESS_WAF_PLUGIN_URL=<plugin-oci-url>`.
- WAF blocking commands show `HIGRESS_GATEWAY_BASE_URL=<target-gateway-url>` and `HIGRESS_WAF_BLOCKING_COVERAGE=true`.
- Trusted TLS commands show `HIGRESS_TLS_GATEWAY_HOST=<gateway-host>` and `HIGRESS_TLS_SERVER_NAME=<server-name>`.
- OIDC endpoint security commands show `HIGRESS_GATEWAY_BASE_URL=<target-gateway-url>`.
- Credentialed P0-P3 smoke commands show `DELIVERY_SMOKE_DASHSCOPE_API_KEY=<provider-api-key>`.
- Placeholders are descriptive and do not include real secrets or customer endpoints.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because WAF preflight and credentialed smoke commands were still bare `node ...` commands.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs` passed `19/19`.

## Remaining Risk

Copyable commands reduce handoff error but do not provide the target values. Customer/target environments still need live WAF runtime fetch, WAF blocking, trusted TLS, OIDC endpoint security, and credentialed P0-P3 runtime evidence.
