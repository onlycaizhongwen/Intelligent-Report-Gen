# Delivery Closure 361: Higress TLS Customer CA Bundle

Date: 2026-07-08

## Scope

- Requirement: `REQ-AUTH-001`
- Gate: `higress-trusted-tls-certificate`
- Runtime boundary: Higress HTTPS certificate verification with customer or enterprise private CA chains

## Result

The trusted TLS certificate smoke now supports customer CA bundles without disabling verification:

- `HIGRESS_TLS_CA_FILE` points to a PEM CA bundle.
- `scripts/higress-tls-certificate-smoke.mjs` reads the CA bundle and passes it to `tls.connect(...)` with `rejectUnauthorized=true`.
- The smoke output reports only `caConfigured=true/false`; it does not serialize the CA file contents.
- The existing trust-store behavior remains unchanged when `HIGRESS_TLS_CA_FILE` is not set.

This makes the production TLS gate usable for public managed certificates and private enterprise CA certificates while preserving fail-closed verification.

## Implementation Evidence

- Added `readTlsCertificateAuthority(...)` in `scripts/higress-tls-certificate-smoke-lib.mjs`.
- Added `ca` support to `runHigressTlsCertificateSmoke(...)`.
- Updated `scripts/higress-tls-certificate-smoke.mjs` to read `HIGRESS_TLS_CA_FILE`.
- Documented `HIGRESS_TLS_CA_FILE` in `.env.example`.

## Verification Evidence

- RED: `node --test tests/unit/node/higress_tls_certificate_smoke.test.mjs` failed because `readTlsCertificateAuthority` was not exported and custom CA support did not exist.
- GREEN: `node --test tests/unit/node/higress_tls_certificate_smoke.test.mjs` passed `8/8`, covering trusted certificates, untrusted chains, expiry, verified connection options, custom CA bundle propagation, optional CA file reading, structured TLS verification failure, and IP-host SNI omission.
- Regression: `node --test tests/unit/node/higress_tls_certificate_smoke.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` passed `14/14`.
- Compose: `docker compose --env-file .env.example config --quiet` passed.
- Hygiene: `git diff --check` passed with only Windows CRLF conversion warnings.
- Live local TLS status: `node scripts/higress-tls-certificate-smoke.mjs` returned `passed=false`, `classification=tls-untrusted`, `authorizationError=DEPTH_ZERO_SELF_SIGNED_CERT`, and `caConfigured=false`, confirming local self-signed TLS still fails closed.
- Live readiness audit: `node scripts/delivery-readiness-audit.mjs` returned `localReady=true`, `productionReady=false`, `total=7`, `passed=4`, `failed=2`, `blocked=1`; the TLS production gate remained failed with `caConfigured=false`, while credentialed P0-P3 delivery smoke and local OIDC test-IdP smoke passed.

## Remaining Risk

This does not make the local self-signed Higress listener production trusted. Production readiness still requires the target gateway certificate chain and hostname to be provided, then `scripts/higress-tls-certificate-smoke.mjs` must return `passed=true` with `classification=tls-trusted`.
