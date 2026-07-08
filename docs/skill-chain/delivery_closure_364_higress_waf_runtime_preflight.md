# Delivery Closure 364: Higress WAF Runtime Preflight

Date: 2026-07-08

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Higress production WAF readiness
- Boundary: candidate WAF policy, local Higress runtime container, OCI plugin registry, readiness audit

## Result

The production readiness audit now separates WAF plugin runtime availability from WAF blocking behavior:

- `higress-waf-runtime-preflight` verifies that the configured Higress WAF OCI plugin can be reached from the `ir-higress` runtime container before the policy is activated.
- `higress-waf-blocking-policy` remains the actual blocking contract for SQLi, XSS, path traversal, and prompt-injection probes.

This prevents candidate-policy presence from being mistaken for production WAF readiness. A temporary local activation test reproduced the unsafe failure mode: adding the candidate `WasmPlugin` to active local Higress data caused gateway socket-close behavior because the runtime could not fetch `oci://higress-registry.cn-hangzhou.cr.aliyuncs.com/plugins/waf:2.0.0`.

## Evidence

- RED: `node --test tests/unit/node/higress_waf_runtime_preflight.test.mjs tests/unit/node/delivery_readiness_audit.test.mjs` failed because `scripts/higress-waf-runtime-preflight-lib.mjs` did not exist and readiness lacked `higress-waf-runtime-preflight`.
- GREEN: the same command passed `13/13`.
- Live preflight: `node scripts/higress-waf-runtime-preflight.mjs` returned `passed=false`, `classification=waf-plugin-container-registry-unreachable`, `containerName=ir-higress`, `status=35`, and `stderr="curl: (35) ... unexpected eof while reading"`.
- Recovery: after removing the temporary active WAF manifest and restarting Higress, `node scripts/higress-gateway-smoke.mjs` returned `passed=true`.

## Remaining Risk

Production WAF readiness still requires one of these verified conditions:

- The Higress runtime can pull the official WAF OCI plugin from the configured registry.
- The WAF plugin image is mirrored/preloaded into a registry reachable by the production gateway, and the candidate manifest is updated to that reachable image.

Only after `higress-waf-runtime-preflight` passes should the active WAF policy be enabled and `HIGRESS_WAF_BLOCKING_COVERAGE=true node scripts/higress-gateway-smoke.mjs` be used as production blocking evidence.
