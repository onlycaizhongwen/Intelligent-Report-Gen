# Delivery Closure 336: Higress Endpoint Authorization Live Sample

## Scope

- Requirement: `REQ-AUTH-001`
- Journey: cross-UC gateway authorization hardening
- Runtime boundary: client -> Higress -> Java controller permission aspect

## Result

Higress endpoint authorization coverage now has an optional live execution subset for the endpoint-by-endpoint controller matrix:

- `runHigressGatewaySmoke(...)` accepts `controllerMatrix` and `controllerEndpointAuthorizationSampleLimit`.
- `HIGRESS_CONTROLLER_ENDPOINT_AUTH_SAMPLE_LIMIT` enables the sample from `scripts/higress-gateway-smoke.mjs`.
- The sample appends only missing-token and insufficient-permission probes for the selected controller endpoints.
- Authorized endpoint probes remain generated in the matrix document but are not added to the live sample by default, avoiding write-side effects from broad POST/PUT/DELETE endpoint execution.
- Default behavior remains unchanged because the sample limit defaults to `0`.

## Evidence

- RED: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` failed because `runHigressGatewaySmoke(...)` did not append `controller-endpoint-0-missing-token-through-higress`.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `11/11`.
- Runtime: existing local Docker dependencies were reused; no new infrastructure was deployed.
- Runtime: `HIGRESS_CONTROLLER_ENDPOINT_AUTH_SAMPLE_LIMIT=1 node scripts/higress-gateway-smoke.mjs` returned `passed=true`.
- Runtime sample evidence: `controller-endpoint-0-missing-token-through-higress` returned `401`, and `controller-endpoint-0-insufficient-permission-through-higress` returned `403`, both through `http://127.0.0.1:18000`.

## Files

- `scripts/higress-gateway-smoke-lib.mjs`
- `scripts/higress-gateway-smoke.mjs`
- `tests/unit/node/higress_gateway_smoke.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure proves a safe live subset of endpoint-level authorization probes, not live execution of all 294 generated endpoint probes.
- Authorized endpoint live probes still need endpoint-specific fixtures and side-effect controls before broad execution.
- Production OIDC login, TLS certificates, and WAF behavior remain future hardening work.
