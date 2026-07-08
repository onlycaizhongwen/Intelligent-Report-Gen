# Delivery Closure 362: Higress OIDC Customer Token Suite

Date: 2026-07-08

## Scope

- Requirement: `REQ-AUTH-001`
- Gate: `higress-oidc-endpoint-security`
- Runtime boundary: customer production IdP validation through Higress and Java `/api/v1/auth/me`

## Result

The production OIDC smoke can now run without asking the customer for private signing material:

- Preferred production path: provide a customer pre-signed token suite:
  - `HIGRESS_OIDC_ACCEPTED_TOKEN`
  - `HIGRESS_OIDC_WRONG_ISSUER_TOKEN`
  - `HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN`
- Existing local/test path remains available with:
  - `HIGRESS_OIDC_PRIVATE_KEY_PEM` or `HIGRESS_OIDC_PRIVATE_KEY_FILE`
  - `HIGRESS_OIDC_KEY_ID`
  - `OIDC_ISSUER`
  - `OIDC_AUDIENCE`
- The readiness audit unblocks the production OIDC gate when either full evidence path is present.
- Readiness output reports token and key inputs as `<provided>` only; token values are not serialized.

## Implementation Evidence

- `buildHigressOidcEndpointSecurityChecks(...)` accepts a customer token suite and still supports generated `RS256` probes.
- `scripts/higress-gateway-smoke.mjs` prefers the token suite when all three token variables are present.
- `buildDeliveryReadinessChecks(...)` treats either token-suite evidence or signing configuration as sufficient to run the production OIDC command gate.
- `.env.example` documents the customer token-suite variables.

## Verification Evidence

- RED: `node --test tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` failed `3` tests because token-suite OIDC probes attempted to sign without a private key and readiness kept the production OIDC gate blocked.
- GREEN: the same command passed `32/32`, covering token-suite probe construction, token-suite smoke execution, readiness unblocking, and secret redaction.

## Remaining Risk

This change makes the production OIDC gate operable with customer-provided black-box evidence. Production readiness still requires the customer gateway and Java runtime to be configured with the real issuer, audience, JWKS, and route policy, then the smoke must return `passed=true`.
