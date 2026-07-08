# Delivery Closure 354: Higress OIDC Endpoint Smoke Contract

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Higress-routed Java authentication boundary
- Boundary: OIDC-compatible `RS256` token acceptance and issuer/audience rejection through `/api/v1/auth/me`

## Result

The Higress gateway smoke harness now supports an opt-in OIDC endpoint security contract:

- `buildGatewayOidcSecurityJwt(...)` creates Java-compatible `RS256` tokens with `kid`, `iss`, `aud`, roles, permissions, status, `iat`, and `exp` claims.
- `buildHigressOidcEndpointSecurityChecks(...)` creates three Higress-routed `/api/v1/auth/me` probes:
  - accepted OIDC token returns `200/code=200`
  - wrong issuer returns `401/code=401`
  - wrong audience returns `401/code=401`
- `runHigressGatewaySmoke(...)` appends the OIDC probes only when `oidcEndpointSecurityConfig` is explicitly provided, so default local `HS256` smoke behavior remains unchanged.
- `scripts/higress-gateway-smoke.mjs` can enable the contract with:
  - `HIGRESS_OIDC_ENDPOINT_SECURITY_COVERAGE=true`
  - preferred production customer token suite: `HIGRESS_OIDC_ACCEPTED_TOKEN`, `HIGRESS_OIDC_WRONG_ISSUER_TOKEN`, `HIGRESS_OIDC_WRONG_AUDIENCE_TOKEN`
  - or local/test signing configuration:
  - `HIGRESS_OIDC_PRIVATE_KEY_PEM` or `HIGRESS_OIDC_PRIVATE_KEY_FILE`
  - `HIGRESS_OIDC_KEY_ID`
  - `OIDC_ISSUER`
  - `OIDC_AUDIENCE`

## Verification

- Baseline: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `17/17` after normalizing the existing matrix document comparison for Windows CRLF reads.
- RED: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` failed because `buildGatewayOidcSecurityJwt` was not exported.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `20/20`, covering RS256 signing, OIDC probe generation, and optional smoke-chain inclusion.
- Compose: `docker compose --env-file .env.example config --quiet` passed.
- Default runtime: `node scripts/higress-gateway-smoke.mjs` returned `passed=true` through local Higress with default `HS256` behavior unchanged.
- Browser smoke: `Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/` returned `HTTP 200 OK`.
- Hygiene: `git diff --check` passed with only Windows CRLF conversion warnings.

## Remaining Risk

- This closure proves the local OIDC token shape and Higress smoke contract with controlled RSA keys. It does not complete a browser login against a live external IdP.
- A live OIDC run still requires the Java service to be started with matching `JWT_ALGORITHM=RS256`, `OIDC_JWKS_URL`, `OIDC_ISSUER`, and `OIDC_AUDIENCE`, plus a JWKS endpoint containing the public key for `HIGRESS_OIDC_KEY_ID`.
