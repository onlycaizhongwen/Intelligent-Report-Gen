# Delivery Closure 380: Readiness Env File Authoritative Evidence

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/delivery-readiness-audit-lib.mjs`
- Boundary: precedence between shell environment and customer-filled readiness `.env` files

## Result

Customer readiness `.env` files are now authoritative for production evidence values.

Behavior:

- Shell control keys still win for output behavior, such as `DELIVERY_READINESS_OUTPUT` and report-file paths.
- Evidence keys from the `.env` file now override stale shell evidence values.
- Blank evidence values in the `.env` file also override stale shell values, preventing a local developer shell from making an unfilled customer template appear ready.
- Required alternative evidence variables are rendered as active empty assignments, including `HIGRESS_OIDC_PRIVATE_KEY_PEM=` and `DASHSCOPE_API_KEY=`, so blank templates can clear stale shell alternatives during precheck.

## Evidence

- RED: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` failed because `HIGRESS_TLS_GATEWAY_HOST` and blank gateway/provider values from the `.env` file were overwritten by stale shell values.
- GREEN: `node --test tests/unit/node/delivery_readiness_audit.test.mjs` passed `22/22` after changing merge precedence.
- Runtime precheck: `PRODUCTION_READINESS_ENV_FILE=docs/skill-chain/generated/production-readiness.env.example node scripts/production-readiness-env-check.mjs` now reports `credentialed-delivery-smoke` missing `DELIVERY_SMOKE_DASHSCOPE_API_KEY or DASHSCOPE_API_KEY` even when the current shell may contain provider credentials.

## Remaining Risk

This prevents false-positive precheck input readiness, but it does not provide customer values. Production readiness still requires live WAF runtime reachability, WAF blocking, trusted TLS, OIDC endpoint security, and credentialed P0-P3 smoke evidence in the target environment.
