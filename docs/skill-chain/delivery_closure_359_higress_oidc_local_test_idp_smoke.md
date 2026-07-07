# Delivery Closure 359: Higress OIDC Local Test-IdP Smoke

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Runtime boundary: browser/client -> local Higress `http://127.0.0.1:18000` -> Java report core
- Security boundary: OIDC-compatible `RS256` token verification with JWKS, issuer, and audience checks

## Result

The local delivery environment now has a repeatable Higress-routed OIDC smoke using a temporary test IdP:

- `scripts/higress-oidc-local-smoke.mjs` starts a local JWKS endpoint with generated `RS256` key material.
- It starts an isolated temporary Java runtime on `18086` with `JWT_ALGORITHM=RS256`, `OIDC_JWKS_URL`, `OIDC_ISSUER`, and `OIDC_AUDIENCE`.
- It temporarily rewires the local Higress Java endpoint from the default Java runtime to the OIDC runtime, restarts Higress, and runs OIDC-only gateway probes.
- It restores the original Higress Java endpoint and removes the temporary Java container in `finally`.

This proves the local gateway can route an OIDC-compatible token to Java and that Java accepts only the configured issuer/audience while rejecting mismatches.

## Implementation Evidence

- Added `scripts/higress-oidc-local-smoke-lib.mjs`.
- Added `scripts/higress-oidc-local-smoke.mjs`.
- Added `tests/unit/node/higress_oidc_local_smoke.test.mjs`.
- Updated `scripts/higress-gateway-smoke-lib.mjs` and `scripts/higress-gateway-smoke.mjs` so `HIGRESS_GATEWAY_BASELINE_COVERAGE=false` can run only the OIDC probes against an `RS256` runtime.
- Updated `tests/unit/node/higress_gateway_smoke.test.mjs` to lock the OIDC-only probe mode.

## Verification Evidence

- Browser entrypoint: `GET http://127.0.0.1:5173/` returned `HTTP 200`.
- Java health: `GET http://127.0.0.1:18082/actuator/health` returned `HTTP 200`.
- Unit regression: `node --test tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/higress_oidc_local_smoke.test.mjs` passed `32/32`.
- Live OIDC smoke: `node scripts/higress-oidc-local-smoke.mjs` passed with three Higress-routed OIDC results:
  - accepted issuer/audience returned `200/code=200`
  - wrong issuer returned `401/code=401`
  - wrong audience returned `401/code=401`
- Default Higress smoke after restoration: `node scripts/higress-gateway-smoke.mjs` returned `passed=true`.

## Remaining Risk

This closes the local test-IdP OIDC route and Java verification loop. It does not claim that a customer production IdP is configured. Production readiness still requires customer-approved OIDC settings, trusted TLS on the real gateway hostname, and an activated WAF policy whose plugin image can be fetched or mirrored by the target Higress runtime.
