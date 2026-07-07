# Delivery Closure 356: Higress Trusted TLS Certificate Contract

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Higress HTTPS gateway boundary
- Boundary: production trusted TLS certificate chain and minimum validity window

## Result

The local TLS gateway smoke already proves that `https://127.0.0.1:18443` reaches the Java API when certificate verification is disabled for local self-signed development certificates. This closure adds a separate production-oriented certificate contract:

- `scripts/higress-tls-certificate-smoke-lib.mjs` evaluates the peer certificate with TLS verification enabled.
- `scripts/higress-tls-certificate-smoke.mjs` connects with `rejectUnauthorized=true`.
- The smoke passes only when the certificate chain is trusted by Node and the leaf certificate has at least `HIGRESS_TLS_MIN_VALID_DAYS` remaining.
- IP hosts omit SNI unless `HIGRESS_TLS_SERVER_NAME` is explicitly provided, avoiding false local warnings.
- `.env.example` documents `HIGRESS_TLS_GATEWAY_HOST`, `HIGRESS_TLS_GATEWAY_PORT`, `HIGRESS_TLS_SERVER_NAME`, and `HIGRESS_TLS_MIN_VALID_DAYS`.

## Verification

- RED: `node --test tests/unit/node/higress_tls_certificate_smoke.test.mjs` failed because `scripts/higress-tls-certificate-smoke-lib.mjs` did not exist.
- GREEN: `node --test tests/unit/node/higress_tls_certificate_smoke.test.mjs` passed `4/4`, covering trusted, untrusted, expiring, and verified connection options.
- Runtime RED: `node scripts/higress-tls-certificate-smoke.mjs` initially threw an uncaught `DEPTH_ZERO_SELF_SIGNED_CERT` error for the local self-signed certificate.
- Fix RED: the targeted test then failed because TLS verification errors were not structured and IP SNI was still sent.
- GREEN: `node --test tests/unit/node/higress_tls_certificate_smoke.test.mjs` passed `6/6`.
- Live local status: `node scripts/higress-tls-certificate-smoke.mjs` returned structured JSON with `passed=false`, `classification=tls-untrusted`, and `authorizationError=DEPTH_ZERO_SELF_SIGNED_CERT`.

## Remaining Risk

- This closure adds an executable trusted TLS contract. It does not claim the local Higress certificate is production trusted.
- Production readiness still requires a managed certificate installed on the real gateway host, then a live run with `HIGRESS_TLS_GATEWAY_HOST`, `HIGRESS_TLS_SERVER_NAME`, and `HIGRESS_TLS_MIN_VALID_DAYS` set for that environment returning `passed=true`.
