# Delivery Closure 341: Delivery Smoke Gateway Override Propagation

## Scope

- Requirement: `REQ-REPORT-001`, `REQ-REPORT-002`, `REQ-REPORT-004`, `REQ-AUTH-001`
- User journey: one-command delivery validation across P0-P3 local smoke bundles
- Runtime boundary: delivery smoke runner -> P0 smoke runner -> Higress-routed browser and API smoke steps

## Result

The top-level delivery smoke runner now carries local Higress gateway overrides into the P0 bundle:

- `buildDeliverySmokeSteps(...)` accepts `gatewayApiBaseUrl` and `gatewayOrigin`.
- The `p0-local-smoke` step receives `P0_SMOKE_GATEWAY_API_BASE_URL` and `P0_SMOKE_GATEWAY_ORIGIN`.
- `scripts/delivery-local-smoke.mjs` reads `DELIVERY_SMOKE_GATEWAY_API_BASE_URL` and `DELIVERY_SMOKE_GATEWAY_ORIGIN`, defaulting to the local Higress ports already used by P0.

## Evidence

- RED: `node --test tests/unit/node/delivery_local_smoke_bundle.test.mjs` failed because `P0_SMOKE_GATEWAY_API_BASE_URL` was `undefined`.
- GREEN: `node --test tests/unit/node/delivery_local_smoke_bundle.test.mjs` passed `1/1`.
- Adjacent regression: `node --test tests/unit/node/p0_local_smoke_bundle.test.mjs` passed `1/1`.

## Files

- `scripts/delivery-local-smoke-lib.mjs`
- `scripts/delivery-local-smoke.mjs`
- `tests/unit/node/delivery_local_smoke_bundle.test.mjs`

## Remaining Risk

- Full `scripts/delivery-local-smoke.mjs` execution still requires a valid `DASHSCOPE_API_KEY` for UC-01 real provider validation.
- This closure proves configuration propagation. It does not change the underlying P0-P3 smoke semantics.
