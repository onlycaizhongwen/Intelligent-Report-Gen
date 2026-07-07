# Delivery Closure 355: Higress WAF Blocking Smoke Contract

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: Higress-routed Java API boundary
- Boundary: executable WAF blocking contract for SQLi, XSS, path traversal, and prompt-injection-shaped traffic

## Result

The Higress gateway smoke harness now separates two security claims:

- Default smoke still proves the Java fallback behavior for malformed attack-shaped parameters, including structured `400/code=400` instead of `500`.
- Opt-in WAF smoke proves gateway-level blocking when a real Higress WAF policy is configured.

New executable contract:

- `buildHigressWafBlockingChecks(...)` creates authenticated, no-side-effect probes for:
  - SQLi-shaped report list pagination
  - XSS-shaped report search keyword
  - encoded path traversal against document routes
  - prompt-injection-shaped report generation-task body with an invalid blank topic, so no task is created if WAF is not yet blocking
- Each probe expects Higress/WAF to stop the request before normal Java handling with one of `403`, `406`, or `429`.
- `runHigressGatewaySmoke(...)` appends these checks only when `wafBlockingCoverage=true`.
- `scripts/higress-gateway-smoke.mjs` can enable the contract with `HIGRESS_WAF_BLOCKING_COVERAGE=true`.
- `.env.example` defaults `HIGRESS_WAF_BLOCKING_COVERAGE=false`, so local development does not falsely fail until an actual blocking policy is installed.

## Verification

- RED: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` failed because `buildHigressWafBlockingChecks` was not exported.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `22/22`, covering WAF probe generation and opt-in smoke-chain inclusion.
- Live opt-in status check: `wafBlockingCoverage=true` against local Higress returned `passed=false`; SQLi returned Java `400`, XSS returned Java `200`, encoded path traversal returned Tomcat `400`, and prompt-injection generation-task returned Java `400`. This confirms the local policy still needs real WAF blocking rules before the opt-in contract can pass.

## Remaining Risk

- This closure adds the executable WAF blocking contract. It does not claim the current local Higress rules already block these payloads.
- Production readiness still requires a customer-approved Higress WAF policy, then a live run with `HIGRESS_WAF_BLOCKING_COVERAGE=true` returning `passed=true`.
