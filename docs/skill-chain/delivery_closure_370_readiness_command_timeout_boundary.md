# Delivery Closure 370: Readiness Command Timeout Boundary

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit.mjs`
- Boundary: readiness audit command execution must not wait forever without actionable evidence

## Result

The delivery readiness audit now gives each command check an explicit timeout and records timeout failures as structured evidence.

Behavior:

- Local Higress security smoke timeout: `60s`
- Local Higress OIDC test-IdP smoke timeout: `180s`
- Higress WAF runtime preflight timeout: `45s`
- Higress WAF blocking policy timeout: `60s`
- Trusted TLS certificate smoke timeout: `45s`
- Production OIDC endpoint security timeout: `120s`
- Credentialed P0-P3 delivery smoke timeout: `300s`
- Timed-out command checks now return `classification=command-timeout`, `timedOut=true`, `signal`, and `timeoutMs` in readiness evidence.
- The audit writes command start/end progress to stderr so long local or credentialed smoke checks are visible even when stdout is reserved for JSON or Markdown reports.

## Evidence

- Root-cause probe: `higress-default-security-smoke` completed in `443ms`; `higress-local-oidc-test-idp-smoke` exceeded the 60-second diagnostic boundary; WAF/TLS production blockers returned quickly; `credentialed-delivery-smoke` also exceeded the 60-second diagnostic boundary.
- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because command checks had no `timeoutMs` and timeout classification produced empty evidence.
- GREEN: the same command passed `13/13`.
- Regression: `node --test tests/unit/node/delivery_readiness_audit.test.mjs tests/unit/node/higress_waf_runtime_preflight.test.mjs tests/unit/node/higress_waf_policy_manifest.test.mjs` passed `24/24`.
- Live Markdown readiness without model credentials exited `2` as expected, emitted stderr progress for each command, reported `Local ready: true`, `Production ready: false`, and rendered TLS optional CA plus OIDC input options in the customer handoff checklist.

## Remaining Risk

This prevents indefinite readiness waits and improves operator diagnosis, but it does not make long-running local OIDC or credentialed P0-P3 smoke evidence pass. Production readiness still requires target-environment WAF plugin reachability, WAF blocking, trusted TLS, customer OIDC, and credentialed full delivery proof.
