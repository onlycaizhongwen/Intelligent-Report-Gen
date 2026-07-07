# Delivery Closure 351: Java RS256 JWKS Auth Boundary

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Java JWT verification for Higress/OIDC integration
- Boundary: local `HS256` development tokens and production `RS256` OIDC/JWKS tokens

## Result

Java JWT verification now supports an explicit `security.jwt.algorithm` setting:

- `HS256`: local development path using `JWT_SECRET`, preserving existing smoke and test tokens.
- `RS256`: production OIDC-compatible path using `OIDC_JWKS_URL` to fetch the matching RSA public key by `kid`.

When `security.jwt.algorithm=RS256` and no JWKS URL is configured, `JwtTokenProvider` fails closed during construction with `OIDC JWKS URL is required...` instead of silently falling back to local shared-secret tokens.

`docker-compose.yml` now passes `JWT_ALGORITHM` and `OIDC_JWKS_URL` into `java-report-core`. Local compose defaults remain `HS256` with an empty JWKS URL so existing developer smoke flows keep working.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest" test` failed because `JwtTokenProvider(String, long, String, String)` and RS256/JWKS verification did not exist.
- GREEN: `JwtTokenProviderTest` passed `3/3`, covering existing HS256 status parsing, RS256 token verification against a local JWKS HTTP endpoint, and RS256 missing-JWKS fail-closed behavior.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest,JwtAuthenticationFilterTest,ContractSurfaceTest,SecurityRuntimeContractTest,ReportCoreProdProfileContextTest" test` passed `49/49`.
- Config: `docker compose --env-file .env.example config --quiet` passed.
- Browser smoke: `Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/` returned `HTTP 200 OK`.
- Hygiene: `git diff --check` passed.

## Remaining Risk

- This closure validates Java-side RS256/JWKS token verification with a local JWKS endpoint. It does not complete a live production IdP login flow through Higress.
- JWKS caching, key rotation windows, issuer/audience checks, and Higress OIDC browser login acceptance remain follow-up production hardening.
