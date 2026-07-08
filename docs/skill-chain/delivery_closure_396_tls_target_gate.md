# Closure 396: TLS Target Input Gate

## Target

Prevent the production trusted TLS readiness gate from silently using the local Higress default when customer target inputs are absent.

## Change

- `buildDeliveryReadinessChecks(...)` now blocks `higress-trusted-tls-certificate` unless both `HIGRESS_TLS_GATEWAY_HOST` and `HIGRESS_TLS_SERVER_NAME` are provided.
- When the TLS target is provided, the executable check still runs `scripts/higress-tls-certificate-smoke.mjs` and marks `HIGRESS_TLS_GATEWAY_HOST`, `HIGRESS_TLS_SERVER_NAME`, and optional `HIGRESS_TLS_CA_FILE` as `<provided>` in the check metadata.
- The latest generated readiness action plan now reports TLS as `blocked` with missing target inputs instead of `failed/tls-untrusted` against local self-signed Higress.

## Verification

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `higress-trusted-tls-certificate` was still built as `kind=command` when TLS target inputs were missing.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `26/26`.
- Live readiness regeneration: `DELIVERY_READINESS_OUTPUT=markdown DELIVERY_READINESS_REPORT_FILE=docs/skill-chain/generated/production-readiness-action-plan.latest.md DELIVERY_READINESS_ENV_TEMPLATE_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/delivery-readiness-audit.mjs` exited `2` with `Local ready: true`, frontend `HTTP 200`, `credentialed-delivery-smoke` passed P0-P3, and production blockers limited to WAF runtime preflight, WAF blocking, trusted TLS, and OIDC endpoint security.

## Remaining Risk

Production readiness still requires customer target evidence:

- WAF plugin image reachable from the Higress runtime container.
- WAF blocking smoke passing on the target gateway.
- Trusted TLS smoke passing with the customer hostname and certificate chain.
- OIDC endpoint security smoke passing with customer token-suite or signing/JWKS evidence.
