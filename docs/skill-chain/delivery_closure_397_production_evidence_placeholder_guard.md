# Closure 397: Production Evidence Placeholder Guard

## Target

Prevent customer production readiness prechecks and child smoke commands from treating copyable command placeholders as real evidence.

## Change

- Production evidence validation now treats angle-bracket placeholders such as `<plugin-oci-url>`, `<target-gateway-url>`, `<gateway-host>`, and `<provider-api-key>` as missing values.
- Production gateway targets must be absolute `http` or `https` URLs before WAF blocking or OIDC endpoint-security checks can be considered input-ready.
- Child smoke command environments now filter placeholder-shaped values so copied command examples cannot leak into runtime probes as if they were configured secrets or endpoints.

## Verification

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `validateProductionReadinessEnv(...)` returned `ready=true` for copyable `<...>` placeholders.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `28/28`.
- Regression: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs` passed `31/31`.
- CLI placeholder precheck: running `node scripts/production-readiness-env-check.mjs` with WAF, gateway, TLS, OIDC, and provider env values set to `<...>` placeholders exited `1` and listed all five production gates as missing.
- CLI precheck: `PRODUCTION_READINESS_ENV_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/production-readiness-env-check.mjs` exited `1` with all production evidence gates listed as missing, proving the generated empty customer template does not pass precheck.

## Remaining Risk

This prevents false-positive prechecks from placeholder values. It does not provide the real customer target evidence still required for production readiness:

- WAF plugin image reachable from the Higress runtime container.
- WAF blocking smoke on the target gateway.
- Trusted TLS smoke for the customer hostname/certificate chain.
- OIDC endpoint security smoke with customer token-suite or signing/JWKS evidence.
