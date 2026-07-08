# Closure 399: TLS Local Target Precheck

## Target

Prevent production trusted TLS evidence prechecks from accepting local Higress targets such as `127.0.0.1` or `localhost`.

## Change

- `validateProductionReadinessEnv(...)` now rejects `HIGRESS_TLS_GATEWAY_HOST` values that point to local loopback hosts.
- `validateProductionReadinessEnv(...)` now rejects `HIGRESS_TLS_SERVER_NAME` values that point to local loopback names.
- The delivery readiness check builder uses the same TLS target guard, so local self-signed Higress cannot be promoted into an executable production TLS smoke by accident.

## Verification

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `HIGRESS_TLS_GATEWAY_HOST=127.0.0.1` and `HIGRESS_TLS_SERVER_NAME=localhost` still produced `ready=true`.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `30/30`.
- Regression: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs tests/unit/node/higress_tls_certificate_smoke.test.mjs` passed `43/43`.

## Remaining Risk

This blocks local TLS targets during precheck. Production readiness still requires a customer hostname, trusted certificate chain, and a live `higress-trusted-tls-certificate` smoke result with `passed=true/classification=tls-trusted`.
