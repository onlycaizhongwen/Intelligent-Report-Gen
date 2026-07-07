# Delivery Closure 358: Higress WAF Candidate and Local Gateway Recovery

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Runtime boundary: browser/client -> local Higress `http://127.0.0.1:18000` -> Java report core
- Related production gate: Higress WAF blocking policy in `scripts/delivery-readiness-audit.mjs`

## Result

The local active Higress route no longer enables the WAF plugin by default. The WAF policy is preserved as a non-active candidate at `config/higress/waf/intelligent-report-waf.candidate.yaml` so it can be reviewed, mirrored/preloaded, and activated in a production-like gateway environment without breaking the local development gateway.

## Root Cause

An active local WAF configuration caused Higress/Envoy to fetch `oci://higress-registry.cn-hangzhou.cr.aliyuncs.com/plugins/waf:2.0.0`. The local runtime could not fetch the OCI manifest and returned:

```text
cannot fetch Wasm module oci://higress-registry.cn-hangzhou.cr.aliyuncs.com/plugins/waf:2.0.0: could not fetch Wasm OCI image: could not fetch manifest: Get "https://higress-registry.cn-hangzhou.cr.aliyuncs.com/v2/": EOF
```

After the ECDS error, the local gateway closed client connections instead of returning Java business responses.

## Implementation Evidence

- Added `config/higress/waf/intelligent-report-waf.candidate.yaml` with the official Higress WAF plugin URL, CRS enabled, and explicit SQLi/XSS/path-traversal/prompt-injection rules for the readiness audit probes.
- Removed `higress.io/enable-waf: "true"` from the active local Ingress data under `config/higress/local-data/ingresses/intelligent-report-routes.yaml`.
- Removed `higress.io/enable-waf: "true"` from the route template under `config/higress/routes.yaml`.
- Added `tests/unit/node/higress_waf_policy_manifest.test.mjs` so the active local WAF manifest must remain absent and the candidate policy must retain the readiness probe rules.

## Verification Evidence

- RED: `node --test tests/unit/node/higress_waf_policy_manifest.test.mjs` failed because the candidate policy was missing and the active local route still enabled WAF.
- GREEN: `node --test tests/unit/node/higress_waf_policy_manifest.test.mjs` passed `3/3`.
- Runtime recovery: restarted `ir-higress`; `GET http://127.0.0.1:18000/api/v1/roles/permission-matrix` returned `HTTP 401` instead of a socket close.
- Runtime gateway smoke: `node scripts/higress-gateway-smoke.mjs` returned `passed=true`.
- Browser entrypoint: `GET http://127.0.0.1:5173/` returned `HTTP 200`.
- Delivery readiness audit: `node scripts/delivery-readiness-audit.mjs` returned `localReady=true`, `productionReady=false`, default Higress smoke `resultCount=80/failedResults=[]`, and credentialed P0-P3 delivery smoke completed `p0-local-smoke`, `p1-local-smoke`, `p2-local-smoke`, and `p3-local-smoke`.

## Remaining Risk

This does not close the production WAF blocking gate. To close it, mirror or prefetch the Higress WAF plugin image for the target gateway, activate the candidate policy in that environment, then run `HIGRESS_WAF_BLOCKING_COVERAGE=true node scripts/higress-gateway-smoke.mjs` and the delivery readiness audit until the WAF blocking gate passes.

The latest readiness audit still reports these production blockers:

- `higress-waf-blocking-policy`: four attack probes reached Java/application fallback instead of being blocked by WAF.
- `higress-trusted-tls-certificate`: local TLS is self-signed (`DEPTH_ZERO_SELF_SIGNED_CERT`).
- `higress-oidc-endpoint-security`: customer/test IdP signing configuration is not provided.
