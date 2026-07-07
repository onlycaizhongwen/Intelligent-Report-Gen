# Delivery Closure 342: Delivery Smoke Downstream Higress Overrides

## Scope

- Requirement: `REQ-AUTH-001`, `REQ-RULE-001`, `REQ-DASH-001`, `REQ-KB-003`
- User journey: one-command delivery validation across P2/P3 gateway-routed smoke steps
- Runtime boundary: delivery smoke runner -> P2/P3 smoke runners -> Higress-routed gateway, browser, and rule-runtime checks

## Result

The top-level delivery smoke runner now propagates local Higress overrides beyond P0:

- `buildDeliverySmokeSteps(...)` accepts `gatewayBaseUrl` and `tlsGatewayBaseUrl` for P2 gateway security checks.
- The P2 step receives `P2_SMOKE_HIGRESS_GATEWAY_BASE_URL`, `P2_SMOKE_HIGRESS_TLS_GATEWAY_BASE_URL`, `P2_SMOKE_HIGRESS_API_BASE_URL`, and `P2_SMOKE_HIGRESS_ORIGIN`.
- `buildP3SmokeSteps(...)` accepts `gatewayApiBaseUrl` and `gatewayOrigin` instead of hardcoding `http://127.0.0.1:18000`.
- The P3 step receives `P3_SMOKE_HIGRESS_API_BASE_URL` and `P3_SMOKE_HIGRESS_ORIGIN`.
- `scripts/delivery-local-smoke.mjs` reads `DELIVERY_SMOKE_GATEWAY_BASE_URL`, `DELIVERY_SMOKE_TLS_GATEWAY_BASE_URL`, `DELIVERY_SMOKE_GATEWAY_API_BASE_URL`, and `DELIVERY_SMOKE_GATEWAY_ORIGIN`.

## Evidence

- RED: `node --test tests/unit/node/delivery_local_smoke_bundle.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs` failed because P2 gateway env was `undefined` and P3 Higress steps still used `http://127.0.0.1:18000/api/v1`.
- GREEN: `node --test tests/unit/node/delivery_local_smoke_bundle.test.mjs tests/unit/node/p3_local_smoke_bundle.test.mjs` passed `2/2`.
- Adjacent regression: `node --test tests/unit/node/p2_local_smoke_bundle.test.mjs` passed `1/1`.

## Files

- `scripts/delivery-local-smoke-lib.mjs`
- `scripts/delivery-local-smoke.mjs`
- `scripts/p3-local-smoke-lib.mjs`
- `scripts/p3-local-smoke.mjs`
- `tests/unit/node/delivery_local_smoke_bundle.test.mjs`
- `tests/unit/node/p3_local_smoke_bundle.test.mjs`

## Remaining Risk

- This closure proves smoke wrapper configuration propagation. Full runtime P2/P3 execution still depends on the local Java, Higress, PostgreSQL, MinIO, RocketMQ, and frontend runtime state.
- Production OIDC, trusted TLS certificates, and WAF rules remain separate hardening work.
