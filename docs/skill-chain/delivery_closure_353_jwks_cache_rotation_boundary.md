# Delivery Closure 353: JWKS Cache And Rotation Boundary

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Java RS256 OIDC token verification
- Boundary: JWKS lookup performance and key rotation behavior

## Result

Java RS256 verification now caches resolved JWKS RSA public keys by `kid` for a configurable TTL:

- `OIDC_JWKS_CACHE_TTL_SECONDS` defaults to `300`.
- Repeated verification of tokens signed by the same `kid` reuses the cached key instead of calling the JWKS endpoint every time.
- When a token arrives with a `kid` that is missing from the current cache, Java refreshes the JWKS immediately and retries key resolution, supporting normal OIDC key rotation without waiting for TTL expiry.

Local development remains compatible with `HS256 + JWT_SECRET`; the new TTL setting is passed through Docker Compose but only affects `RS256`.

## Verification

- Baseline: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest,JwtAuthenticationFilterTest,ReportCoreProdProfileContextTest" test` passed `12/12`.
- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest" test` failed because two parses of the same RS256 token produced two JWKS HTTP requests instead of one.
- GREEN: `JwtTokenProviderTest` passed `9/9`, covering repeated-key JWKS cache reuse and cache refresh on rotated `kid`.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest,JwtAuthenticationFilterTest,ContractSurfaceTest,SecurityRuntimeContractTest,ReportCoreProdProfileContextTest" test` passed `55/55`.
- Config: `OIDC_JWKS_CACHE_TTL_SECONDS` is available in Spring config, `.env.example`, and `docker-compose.yml`.
- Compose: `docker compose --env-file .env.example config --quiet` passed.
- Browser smoke: `Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/` returned `HTTP 200 OK`.
- Hygiene: `git diff --check` passed.

## Remaining Risk

- This closure validates local JWKS cache and rotation behavior with a controlled JWKS HTTP endpoint. It does not complete a live external IdP browser login through Higress.
- Production cache invalidation policy may still need adjustment for a customer IdP's published key rotation cadence.
