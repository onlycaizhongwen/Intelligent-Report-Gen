# Delivery Closure 365: Higress WAF Plugin Mirror Override

Date: 2026-07-08

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Higress production WAF readiness
- Boundary: WAF OCI plugin image selection, delivery readiness audit, customer/private registry adaptation

## Result

The WAF runtime preflight can now use `HIGRESS_WAF_PLUGIN_URL` to probe a customer/private mirrored WAF plugin image without editing the candidate `WasmPlugin` manifest.

This keeps the default candidate policy stable while giving production and restricted local environments a clean override path:

- default: read `spec.url` from `config/higress/waf/intelligent-report-waf.candidate.yaml`
- override: set `HIGRESS_WAF_PLUGIN_URL=oci://<reachable-registry>/<repository>:<tag>`
- readiness report: marks the override as `<provided>` instead of serializing the raw value
- credential boundary: OCI URLs with `user:password@registry` are rejected and the error message redacts the userinfo

The override only changes the preflight target. It does not activate WAF by itself and does not claim blocking behavior. The WAF blocking gate still requires `HIGRESS_WAF_BLOCKING_COVERAGE=true node scripts/higress-gateway-smoke.mjs` to pass after the real policy is installed.

## Evidence

- RED: `node --test tests/unit/node/higress_waf_runtime_preflight.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` failed because `pluginUrlOverride` was ignored, readiness did not mark `HIGRESS_WAF_PLUGIN_URL` as provided, and parser validation did not reject URL userinfo.
- GREEN: the same command passed `16/16`.

## Remaining Risk

Production WAF readiness still depends on target-environment evidence:

- The configured registry must be reachable from the Higress runtime container.
- The mirrored image must be the approved Higress WAF plugin version.
- The active WAF policy must be enabled only after the runtime preflight passes.
- Gateway-level attack probes must be blocked by WAF, not merely handled by Java fallback logic.
