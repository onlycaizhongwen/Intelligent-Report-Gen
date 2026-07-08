# Delivery Closure 381: Readiness Env Precheck CLI Authority Regression

Date: 2026-07-08

## Scope

- Requirement: cross-cutting customer production handoff
- Surface: `scripts/production-readiness-env-check.mjs`
- Boundary: command-line precheck behavior when a customer `.env` file is supplied

## Result

The production readiness precheck CLI now has regression coverage proving that a customer `.env` file remains authoritative over stale shell evidence in the real command path.

The new CLI test creates a blank customer evidence file, runs `scripts/production-readiness-env-check.mjs` with stale WAF, gateway, TLS, OIDC, and provider values in the shell, and verifies the command still reports all production evidence inputs as missing.

This protects the handoff workflow where customers start from `docs/skill-chain/generated/production-readiness.env.example`: an unfilled file must not pass merely because the operator shell already contains unrelated values.

## Evidence

- CLI regression: `node --test tests/unit/node/production_readiness_env_check.test.mjs` passed `2/2`.
- The blank-file case returns `ready=false` and includes `credentialed-delivery-smoke` in `missingItems` even when `DELIVERY_SMOKE_DASHSCOPE_API_KEY` and `DASHSCOPE_API_KEY` are present in the shell.
- The output does not echo stale provider or token values.

## Remaining Risk

This proves precheck input authority and redaction, but not production runtime behavior. Target/customer environments still need live WAF runtime reachability, WAF blocking, trusted TLS, OIDC endpoint security, and credentialed P0-P3 smoke evidence.
