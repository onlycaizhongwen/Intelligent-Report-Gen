# Delivery Closure 375: Delivery Audit Environment File Loading

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit.mjs`
- Boundary: reuse the same customer-filled production evidence `.env` file for the full delivery readiness audit

## Result

The full delivery readiness audit can now consume the same evidence `.env` file used by the production precheck.

Behavior:

- `DELIVERY_READINESS_ENV_FILE=<path> node scripts/delivery-readiness-audit.mjs` loads customer WAF, TLS, OIDC, and credentialed smoke inputs before building readiness checks.
- The loaded values flow into child smoke commands through the audit runtime environment.
- Shell control variables such as `DELIVERY_READINESS_OUTPUT`, `DELIVERY_READINESS_REPORT_FILE`, and `DELIVERY_READINESS_ENV_TEMPLATE_FILE` still win over values inside the evidence file.
- `production-readiness-env-check.mjs` now reuses the shared parser/merge logic from `delivery-readiness-audit-lib.mjs`.
- The generated production readiness `.env` template now shows both the fast precheck command and the full audit command.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `loadDeliveryReadinessEnv` did not exist.
- GREEN: the same command passed `17/17`.
- Regression: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs` passed `18/18`.

## Remaining Risk

Loading a customer evidence file does not make production readiness pass by itself. The target/customer environment still must prove WAF plugin reachability, WAF blocking, trusted TLS, OIDC endpoint security, and credentialed P0-P3 runtime behavior.
