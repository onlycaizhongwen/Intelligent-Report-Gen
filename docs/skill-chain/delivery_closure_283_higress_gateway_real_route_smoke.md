# Closure 283: Higress Gateway Real Route Smoke

## Scope

- Requirement chain: `REQ-AUTH-001`, `REQ-AI-001`
- Target slice: local Higress Gateway route boundary for external `/api/v1/**` traffic.
- Runtime route: `http://127.0.0.1:18000/api/v1/**` -> Higress all-in-one -> `java-report-core`.

## Result

Local Higress is no longer only "container reachable" evidence. The gateway route now has a repeatable smoke script proving that external `/api/v1/**` traffic reaches the Java authentication boundary.

The smoke also checks that `/api/v1/chat` is not publicly exposed as a Python AI service response. In the current local route, `/api/v1/chat` returns the same Java `401` envelope, which means it is controlled by the Java business API boundary instead of bypassing RBAC, task state, and audit closure.

## Evidence

- Runtime smoke: `scripts/higress-gateway-smoke.mjs`
- Smoke library: `scripts/higress-gateway-smoke-lib.mjs`
- Unit contract: `tests/unit/node/higress_gateway_smoke.test.mjs`
- Local Higress data:
  - `config/higress/local-data/services/java-report-core.yaml`
  - `config/higress/local-data/endpoints/java-report-core.yaml`
  - `config/higress/local-data/ingresses/intelligent-report-routes.yaml`

## TDD Evidence

RED:

```text
node --test tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs

Error [ERR_MODULE_NOT_FOUND]: Cannot find module 'scripts/higress-gateway-smoke-lib.mjs'
```

GREEN:

```text
node --test tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs

tests 4
pass 4
fail 0
```

Runtime:

```text
node scripts/higress-gateway-smoke.mjs

passed: true
java-permission-matrix-through-higress: status 401, classification java-auth-required
python-chat-not-public-through-higress: status 401, classification java-auth-required
```

## Remaining Gaps

- This proves local HTTP gateway routing and Python chat non-exposure. It does not prove OIDC login, TLS certificate handling, WAF policy behavior, or production Kubernetes Gateway API objects.
- Endpoint-level 403/200 browser coverage through Higress remains a broader security hardening stream.
