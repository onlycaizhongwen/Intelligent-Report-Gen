# Delivery Closure 374: Production Environment Evidence Precheck

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/production-readiness-env-check.mjs`
- Boundary: executable precheck for customer-filled production readiness evidence inputs

## Result

Customer production handoff now includes a fast precheck before running long readiness smokes.

Behavior:

- `validateProductionReadinessEnv(...)` checks the production evidence input contract without echoing real values.
- `scripts/production-readiness-env-check.mjs` prints a JSON readiness precheck result and exits `0` only when required WAF, TLS, OIDC, and credentialed smoke inputs are present.
- `PRODUCTION_READINESS_ENV_FILE=<path>` lets the precheck read the generated `.env` handoff file directly.
- OIDC evidence accepts either the customer token suite or the signing/JWKS test configuration.
- The generated env template now tells customers how to run the precheck after filling values.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `validateProductionReadinessEnv` did not exist.
- GREEN: the same command passed `15/15`.
- RED: `node --test tests/unit/node/production_readiness_env_check.test.mjs` failed because the CLI did not exist.
- GREEN: the CLI test passed `1/1`.

## Remaining Risk

The precheck proves required inputs are present, not that the target environment passes WAF runtime fetch, WAF blocking, trusted TLS chain validation, OIDC endpoint security, or the credentialed P0-P3 smoke. Those checks still require customer/target runtime evidence.
