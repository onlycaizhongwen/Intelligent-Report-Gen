# Delivery Closure 349: Higress Read-Only Authorized Endpoint Coverage

Date: 2026-07-07

## Scope

- Requirement: `REQ-AUTH-001`
- Surface: local Higress gateway endpoint authorization smoke
- Boundary: safe authorized execution for permission-protected controller `GET` endpoints

## Result

The Higress gateway smoke can now opt into authorized live probes for read-only controller endpoints through `HIGRESS_CONTROLLER_ENDPOINT_AUTH_READONLY_AUTHORIZED_COVERAGE=true`.

The probe builder keeps only `boundary=permission` and `method=GET` controller endpoints, excludes write and multipart endpoints, and reuses the generated authorization matrix so each probe carries a permission-bearing JWT. A read-only authorized probe passes only when the gateway and Java application do not return `401` or `403`, and the response is not `5xx`.

Diff endpoints now receive required query parameters in generated probe URLs:

- `/api/v1/reports/{reportId}/versions/diff?baseVersionId=999999998&targetVersionId=999999999`
- `/api/v1/rules/approval-templates/{templateId}/versions/diff?baseVersion=999999998&targetVersion=999999999`

## Verification

- RED: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` failed when the read-only authorized builder export was absent.
- RED: the first implementation exposed two gaps: version diff probes lacked required query parameters, and `5xx` responses were incorrectly accepted.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `17/17`.
- Live local Higress smoke: `HIGRESS_CONTROLLER_ENDPOINT_AUTH_READONLY_AUTHORIZED_COVERAGE=true node scripts/higress-gateway-smoke.mjs` returned `passed=true`, including 36 authorized read-only controller endpoint probes with `endpoint-authorized-readonly-accepted`.

## Remaining Risk

- Authorized write-side probes remain intentionally excluded from the safe live path.
- Production OIDC, trusted TLS, and WAF policy hardening remain separate delivery closures.
- This closure proves local Higress-to-Java authorized read-only routing behavior; it does not replace production identity-provider acceptance testing.
