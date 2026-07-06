# Closure 286: Higress Endpoint Security Smoke

## Scope

- Requirement chain: `REQ-AUTH-001`, `REQ-AI-001`
- Target slice: prove that the local Higress gateway does not only route `/api/v1/**` into Java, but also preserves Java authentication and permission outcomes for a protected endpoint.
- Runtime route: `GET /api/v1/roles/permission-matrix` through `http://127.0.0.1:18000`.

## Result

The Higress smoke now checks five gateway outcomes:

- `/api/v1/roles/permission-matrix` with an invalid token returns Java `401`.
- `/api/v1/chat` returns Java `401`, proving Python chat is not publicly exposed through the external gateway path.
- `/api/v1/roles/permission-matrix` with no token returns HTTP `401` and API `code=401`.
- `/api/v1/roles/permission-matrix` with a valid JWT lacking `permission:read` returns HTTP `403` and API `code=403`.
- `/api/v1/roles/permission-matrix` with a valid JWT containing `permission:read` returns HTTP `200`, API `code=200`, and a permission matrix payload containing `permission:read`.

This closes the prior generic "live Higress route checks" gap for the representative Java RBAC boundary. OIDC login, TLS, WAF, and full endpoint-by-endpoint gateway authorization remain production hardening work.

## Local Docker Evidence

Fresh `docker ps` evidence showed these local dependencies running before the smoke:

- `ir-java-smoke` on host port `18082`
- `ir-higress` on host ports `18000`, `18001`, and `18443`
- `ir-opensearch`
- `ir-milvus`
- `ir-minio`
- `ir-postgres`
- `ir-redis`
- RocketMQ nameserver and broker containers

No new Docker dependency was deployed in this closure.

## Code Evidence

- `scripts/higress-gateway-smoke.mjs`
- `scripts/higress-gateway-smoke-lib.mjs`
- `scripts/p2-local-smoke-lib.mjs`
- `tests/unit/node/higress_gateway_smoke.test.mjs`
- `tests/unit/node/p2_local_smoke_bundle.test.mjs`

## TDD Evidence

RED:

```text
node --test tests/unit/node/higress_gateway_smoke.test.mjs

failed with:
SyntaxError: The requested module '../../../scripts/higress-gateway-smoke-lib.mjs' does not provide an export named 'buildGatewaySecurityJwt'
```

P2 bundle RED:

```text
node --test tests/unit/node/p2_local_smoke_bundle.test.mjs

failed with:
3 !== 4
```

GREEN:

```text
node --test tests/unit/node/higress_gateway_smoke.test.mjs tests/unit/node/p2_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs

7 passed
```

Runtime smoke:

```text
node scripts/higress-gateway-smoke.mjs

passed = true
permission-matrix-missing-token-through-higress: status=401, code=401
permission-matrix-insufficient-permission-through-higress: status=403, code=403
permission-matrix-authorized-through-higress: status=200, code=200
python-chat-not-public-through-higress: status=401, classification=java-auth-required
```

## Remaining Gaps

- OIDC browser login through Higress is not covered by this closure.
- TLS certificate, WAF, and production K8s Gateway API behavior are not covered by this closure.
- This closure validates one representative RBAC endpoint through Higress. Full endpoint-by-endpoint gateway authorization can be expanded from the same smoke pattern.
