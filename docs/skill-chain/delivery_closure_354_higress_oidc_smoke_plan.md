# Higress OIDC Smoke Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in Higress-routed OIDC/RS256 smoke contract for `REQ-AUTH-001`.

**Architecture:** Keep default local Higress smoke unchanged for HS256 development. When OIDC smoke inputs are explicitly provided, generate RS256 JWT probes for `/api/v1/auth/me` through Higress and verify authorized, wrong issuer, and wrong audience boundaries.

**Tech Stack:** Node.js built-in `node:test`, `node:crypto`, existing Higress smoke harness.

---

### Task 1: OIDC/RS256 Gateway Probe Contract

**Files:**
- Modify: `tests/unit/node/higress_gateway_smoke.test.mjs`
- Modify: `scripts/higress-gateway-smoke-lib.mjs`
- Modify: `scripts/higress-gateway-smoke.mjs`

- [x] **Step 1: Establish baseline**

Run: `node --test tests/unit/node/higress_gateway_smoke.test.mjs`

Expected: PASS for existing Higress contract.

- [x] **Step 2: Write failing OIDC tests**

Add tests that expect:
- `buildGatewayOidcSecurityJwt(...)` creates `RS256` JWTs with `kid`, `iss`, `aud`, roles, permissions, and status.
- `buildHigressOidcEndpointSecurityChecks(...)` creates three Higress-routed `/api/v1/auth/me` probes: authorized, wrong issuer, wrong audience.
- `runHigressGatewaySmoke(...)` appends OIDC probes only when an OIDC config is provided.

- [x] **Step 3: Run RED**

Run: `node --test tests/unit/node/higress_gateway_smoke.test.mjs`

Expected: FAIL because OIDC exports/config are missing.

- [x] **Step 4: Implement minimal OIDC smoke support**

Add the missing builder functions and optional `oidcEndpointSecurityConfig` support.

- [x] **Step 5: Run GREEN**

Run: `node --test tests/unit/node/higress_gateway_smoke.test.mjs`

Expected: PASS with existing and new OIDC contract tests.
