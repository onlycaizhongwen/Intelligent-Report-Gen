# Delivery Closure 352: OIDC Issuer And Audience Boundary

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Java RS256 OIDC token verification
- Boundary: production OIDC tokens must match the configured issuer and audience after JWKS signature verification

## Result

RS256 JWT verification now requires three production OIDC settings:

- `OIDC_JWKS_URL`: JWKS endpoint used to resolve the RSA public key by `kid`.
- `OIDC_ISSUER`: expected token `iss`.
- `OIDC_AUDIENCE`: expected token `aud`.

When `security.jwt.algorithm=RS256`, `JwtTokenProvider` fails closed during construction if any of these settings are missing. After signature and expiration checks, Java now rejects tokens whose `iss` or `aud` do not match the configured values.

Local development remains compatible with `HS256 + JWT_SECRET`; the new issuer/audience settings are passed through Docker Compose but default to empty because local compose defaults to `JWT_ALGORITHM=HS256`.

## Verification

- Baseline: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest,JwtAuthenticationFilterTest,ReportCoreProdProfileContextTest" test` passed `8/8` before the change in the isolated worktree.
- RED: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest" test` failed at test compilation because `JwtTokenProvider(String, long, String, String, String, String)` did not exist.
- GREEN: `JwtTokenProviderTest` passed `7/7`, covering matching issuer/audience, missing issuer fail-closed, missing audience fail-closed, issuer mismatch rejection, and audience mismatch rejection.
- Regression: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=JwtTokenProviderTest,JwtAuthenticationFilterTest,ContractSurfaceTest,SecurityRuntimeContractTest,ReportCoreProdProfileContextTest" test` passed `53/53`.
- Config: `docker compose --env-file .env.example config --quiet` passed.

## Remaining Risk

- This closure validates Java-side OIDC issuer/audience enforcement with local signed RS256 tokens. It does not complete a live external IdP browser login through Higress.
- JWKS caching and key rotation windows remain follow-up production hardening.
