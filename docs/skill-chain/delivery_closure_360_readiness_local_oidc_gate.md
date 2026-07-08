# Delivery Closure 360: Readiness Local OIDC Gate

Date: 2026-07-08

## Scope

- Requirement: `REQ-AUTH-001`
- Gate: `scripts/delivery-readiness-audit.mjs`
- Runtime boundary: local Higress -> temporary Java `RS256` runtime -> local JWKS test IdP

## Result

The delivery readiness audit now includes local OIDC evidence as a required local gate:

- `higress-local-oidc-test-idp-smoke` runs `scripts/higress-oidc-local-smoke.mjs`.
- The check proves accepted issuer/audience and wrong issuer/audience rejection through local Higress.
- The existing `higress-oidc-endpoint-security` production gate remains separate and blocked until customer or production IdP signing configuration is provided.

This keeps readiness reporting precise: local OIDC behavior is now executable evidence, while customer production OIDC remains a production configuration gate.

## Implementation Evidence

- Updated `scripts/delivery-readiness-audit-lib.mjs` to add `higress-local-oidc-test-idp-smoke` after the default local Higress security smoke.
- Compacted OIDC local smoke evidence in the readiness output so `oidcResults` keep only status, code, classification, and pass/fail fields.
- Updated `tests/unit/node/delivery_readiness_audit.test.mjs` to lock check ordering, local scope, evidence compaction, and production OIDC blocked behavior when customer IdP settings are missing.

## Verification Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `higress-local-oidc-test-idp-smoke` was missing from `buildDeliveryReadinessChecks(...)`.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `6/6`.
- Regression: `node --test tests/unit/node/higress_oidc_local_smoke.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs` passed `32/32`.
- Compose: `docker compose --env-file .env.example config --quiet` passed.
- Hygiene: `git diff --check` passed with only Windows CRLF conversion warnings.
- Live audit: `node scripts/delivery-readiness-audit.mjs` returned `localReady=true`, `productionReady=false`, `total=7`, `passed=4`, `failed=2`, `blocked=1`. The new `higress-local-oidc-test-idp-smoke` passed with accepted issuer/audience `200/code=200`, wrong issuer `401/code=401`, and wrong audience `401/code=401`; the production blockers remained `higress-waf-blocking-policy`, `higress-trusted-tls-certificate`, and `higress-oidc-endpoint-security`.

## Remaining Risk

This does not make the system production-ready by itself. The production readiness audit can still fail or block on customer IdP settings, trusted TLS, and WAF blocking policy until those target-environment controls are configured and verified.
