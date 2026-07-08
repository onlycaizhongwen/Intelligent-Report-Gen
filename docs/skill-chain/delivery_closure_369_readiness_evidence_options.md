# Delivery Closure 369: Readiness Evidence Options

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit.mjs`
- Boundary: customer-readable evidence options for production readiness blockers

## Result

The delivery readiness action plan now distinguishes mandatory inputs, optional inputs, and alternative evidence paths.

Behavior:

- TLS no longer presents `HIGRESS_TLS_CA_FILE` as always required. The required inputs are `HIGRESS_TLS_GATEWAY_HOST` and `HIGRESS_TLS_SERVER_NAME`; `HIGRESS_TLS_CA_FILE` is listed as optional for customer/private CA chains only.
- OIDC endpoint security now exposes two valid evidence paths:
  - `customer-token-suite`: customer-provided accepted, wrong-issuer, and wrong-audience tokens
  - `signing-jwks-test-configuration`: private key or PEM, key id, issuer, and audience for generated RS256/JWKS probes
- Markdown readiness reports render optional inputs and input options so customer operators can choose the correct evidence path without reading source code.
- Existing `requiredInputs` remain present for backward compatibility with JSON consumers.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because TLS still required `HIGRESS_TLS_CA_FILE` and Markdown did not render optional inputs or input options.
- GREEN: the same command passed `12/12`.

## Remaining Risk

This improves evidence collection accuracy but does not close the production gates. Target-environment WAF plugin reachability, WAF blocking, trusted TLS, customer OIDC, and credentialed delivery smoke must still pass before `productionReady=true`.
