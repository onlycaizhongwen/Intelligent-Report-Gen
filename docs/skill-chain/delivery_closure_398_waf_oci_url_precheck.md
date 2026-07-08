# Closure 398: WAF OCI URL Precheck

## Target

Move WAF plugin URL validation into the production readiness precheck so invalid customer mirror evidence fails before a long WAF runtime smoke.

## Change

- `validateProductionReadinessEnv(...)` now requires `HIGRESS_WAF_PLUGIN_URL` to use the supported `oci://registry/repository:tag` form.
- Embedded registry credentials such as `oci://user:secret@registry/...` are rejected during precheck and are not echoed in validation output.
- The existing runtime WAF preflight remains the authority for registry reachability; this change only blocks malformed evidence earlier.

## Verification

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `HIGRESS_WAF_PLUGIN_URL=https://...` still produced `ready=true`.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `29/29`.
- Regression: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/production_readiness_env_check.test.mjs` passed `33/33`.

## Remaining Risk

This validates the WAF plugin URL shape only. Production readiness still requires the mirrored WAF plugin manifest to be reachable from the Higress runtime container and the target gateway WAF blocking smoke to pass.
