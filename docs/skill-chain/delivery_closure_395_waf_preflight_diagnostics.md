# Delivery Closure 395: WAF Runtime Preflight Diagnostics

Date: 2026-07-08

## Scope

Improved the production WAF runtime preflight so the readiness gate distinguishes:

- Higress runtime container registry reachability.
- Host-side registry HTTP reachability.
- Host-side OCI manifest availability or authorization failure.

This prevents the production readiness report from collapsing every WAF plugin failure into a generic registry-unreachable message. It now shows whether the next action is container network/proxy repair, plugin mirroring, or registry authentication.

## Runtime Evidence

Current local production-gate probe still fails, but now reports actionable evidence:

- `classification=waf-plugin-container-registry-unreachable`
- `containerRegistryReachable=false`
- `hostRegistryReachable=true`
- `hostManifestReachable=false`
- `hostManifestStatus=401`
- `containerProbe.status=35`

The required next action remains: mirror or authenticate the WAF plugin manifest in a registry reachable from the Higress runtime container, then rerun the WAF runtime preflight before enabling the blocking policy.

## Verification

- RED: `node --test tests/unit/node/higress_waf_runtime_preflight.test.mjs` failed because container-failure output lacked `hostManifestReachable`, and host manifest `401` was classified as registry-unreachable.
- GREEN: `node --test tests/unit/node/higress_waf_runtime_preflight.test.mjs` passed `10/10`.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `26/26`, proving the readiness action plan preserves `containerRegistryReachable`, `hostRegistryReachable`, `hostManifestReachable`, and WAF `nextAction`.
- Runtime: `node scripts/higress-waf-runtime-preflight.mjs` returned exit `1` with the richer WAF diagnostic fields above.

## Remaining Risk

This is a diagnostic and handoff improvement only. It does not close the production WAF gate. Production readiness still requires a reachable/mirrored WAF plugin, active blocking policy, trusted TLS certificate, customer OIDC endpoint evidence, and rerun readiness audit evidence in the target environment.
