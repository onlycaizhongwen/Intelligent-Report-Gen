# Delivery Closure 337: Higress TLS Gateway Smoke

## Scope

- Requirement: `REQ-AUTH-001`
- Journey: cross-UC gateway production hardening
- Runtime boundary: client -> Higress HTTPS listener -> Java controller permission aspect

## Result

The P2 local smoke bundle now includes an explicit Higress TLS gateway security step:

- `buildP2SmokeSteps(...)` returns `higress-tls-endpoint-security-smoke` immediately after the existing HTTP Higress smoke.
- The TLS step runs `scripts/higress-gateway-smoke.mjs` against `https://127.0.0.1:18443` by default.
- `P2_SMOKE_HIGRESS_TLS_GATEWAY_BASE_URL` can override the local TLS gateway URL.
- The local TLS step sets `NODE_TLS_REJECT_UNAUTHORIZED=0` because the local Higress listener uses a self-signed/dev certificate path. This is limited to local smoke execution and is not a production recommendation.
- Existing HTTP gateway, real-backend, and Higress-routed browser E2E steps remain in the bundle.

## Evidence

- RED: `node --test tests/unit/node/p2_local_smoke_bundle.test.mjs` failed because the P2 bundle had `5` steps instead of the expected `6`.
- GREEN: `node --test tests/unit/node/p2_local_smoke_bundle.test.mjs` passed `1/1`.
- Regression: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `11/11`.
- Runtime: `curl.exe -k -i https://127.0.0.1:18443/api/v1/roles/permission-matrix` returned Java `401`.
- Runtime: `HIGRESS_GATEWAY_BASE_URL=https://127.0.0.1:18443 NODE_TLS_REJECT_UNAUTHORIZED=0 node scripts/higress-gateway-smoke.mjs` returned `passed=true`.

## Files

- `scripts/p2-local-smoke-lib.mjs`
- `scripts/p2-local-smoke.mjs`
- `tests/unit/node/p2_local_smoke_bundle.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure proves the local HTTPS listener routes through Higress to Java and preserves RBAC outcomes.
- It does not validate production trusted CA chains, browser trust-store behavior, certificate renewal, OIDC login, or WAF blocking rules.
