# Closure 319: Java controller endpoint authorization matrix

## Scope

- Requirement: `REQ-AUTH-001`
- User journey: every Java REST controller endpoint must have an explicit security boundary before it can be considered production-deliverable.
- Production gap: Closure 318 proved one Higress-routed probe per RBAC catalog permission, but did not generate an endpoint-by-endpoint inventory from every controller method.

## Result

- Added `scripts/controller-authorization-matrix-lib.mjs` to build a static endpoint authorization matrix from Java `*Controller.java` files.
- The parser reads class-level `@RequestMapping`, method-level Spring mappings, and the local security annotations:
  - `@RequiresPermission(...)`
  - `@AuthenticatedEndpoint(...)`
  - `@PublicEndpoint(...)`
- Generated `docs/skill-chain/java_controller_authorization_matrix.md` with 104 controller endpoints.
- Added drift protection in `tests/unit/node/controller_authorization_matrix.test.mjs`:
  - every mapped controller method must be classified as `permission`, `authenticated`, or `public`;
  - duplicate `method + path` entries fail the test;
  - the generated Markdown matrix must stay synchronized with the current controller source.

## Verification

- RED parser guard:
  - `node --test tests/unit/node/controller_authorization_matrix.test.mjs`
  - failed because class-level `@RequestMapping` was incorrectly treated as constructor endpoint mappings such as `/api/v1/api/v1`.
- RED document guard:
  - `node --test tests/unit/node/controller_authorization_matrix.test.mjs`
  - failed with `ENOENT` for `docs/skill-chain/java_controller_authorization_matrix.md`.
- GREEN:
  - `node --test tests/unit/node/controller_authorization_matrix.test.mjs`
  - result: `3/3` passed.
- Gateway regression:
  - `node --test tests/unit/node/higress_gateway_smoke.test.mjs`
  - result: `8/8` passed.
- Live Higress smoke:
  - `node scripts/higress-gateway-smoke.mjs`
  - result: `passed=true`.
  - The live bundle still proves `/api/v1/chat` is not publicly exposed through Higress and that permission-catalog probes keep `401/403/authorized` outcomes through the gateway.

## Remaining Risk

- This is a static controller-source guard. Runtime gateway behavior is still proven by the Higress smoke bundles from Closure 316 and Closure 318.
- Production OIDC login, TLS certificates, and WAF policy behavior remain separate production-hardening work.
