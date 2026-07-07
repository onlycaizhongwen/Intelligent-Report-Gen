# Delivery Closure 357: Delivery Readiness Audit Gate

Date: 2026-07-07

## Scope

- Requirements: cross-cutting delivery verification for the active prototype and requirement matrix
- Surface: local browser entrypoint, Higress security smoke, WAF policy gate, trusted TLS gate, OIDC gate, and credentialed P0-P3 delivery smoke gate
- Boundary: separate locally provable readiness from production/customer-environment readiness

## Result

Added an executable delivery readiness audit:

- `scripts/delivery-readiness-audit-lib.mjs` builds a six-check readiness matrix.
- `scripts/delivery-readiness-audit.mjs` runs local HTTP checks and command-based smoke checks, then emits a single JSON summary.
- Local gates are separated from production gates so the workflow does not confuse "local smoke passed" with "customer production ready".
- Credential-gated checks are marked `blocked` when required environment variables are missing instead of silently skipping.
- Command evidence is compacted to counts and failed item summaries so the audit output stays usable in automated loops.
- Secret-bearing environment values are represented as `<provided>` and are not serialized into the check plan.

## Readiness Checks

| Check | Scope | Evidence |
| --- | --- | --- |
| `frontend-browser-http` | local | Browser entrypoint must return HTTP success. |
| `higress-default-security-smoke` | local | Default Higress route/auth smoke must pass. |
| `higress-waf-blocking-policy` | production | SQLi/XSS/path-traversal/prompt-injection probes must be blocked by WAF. |
| `higress-trusted-tls-certificate` | production | Gateway certificate must be trusted and valid for the configured window. |
| `higress-oidc-endpoint-security` | production | OIDC RS256 endpoint probes require IdP signing configuration. |
| `credentialed-delivery-smoke` | production | Full P0-P3 smoke requires a real model provider key. |

## Verification

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `scripts/delivery-readiness-audit-lib.mjs` did not exist.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `4/4`.
- RED: evidence compaction test failed because nested smoke `results` were still serialized in full.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `5/5`.
- Live full-path audit with the current shell credentials ran `credentialed-delivery-smoke`; local readiness passed and credentialed P0-P3 completed, while production readiness stayed false because WAF, trusted TLS, and OIDC gates were not satisfied.
- Live no-key audit: `node scripts/delivery-readiness-audit.mjs` exited `2` with `localReady=true`, `productionReady=false`, `passed=2`, `failed=2`, `blocked=2`; WAF failed with four `waf-not-blocked` probes, TLS failed with `DEPTH_ZERO_SELF_SIGNED_CERT`, and OIDC/model-key gates were blocked by missing environment.

## Remaining Risk

- This closure improves continuous delivery truthfulness; it does not install a production WAF policy, managed TLS certificate, or customer IdP.
- Production readiness remains false until the WAF blocking contract, trusted TLS certificate smoke, OIDC endpoint smoke, and credentialed full delivery smoke pass in the target environment.
