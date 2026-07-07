# Delivery Closure 338: Higress WAF Attack Parameter Fallback

## Scope

- Requirement: `REQ-AUTH-001`
- Journey: cross-UC gateway production hardening
- Runtime boundary: client -> Higress -> Java controller parameter binding -> global error response

## Result

Local Higress WAF probing showed that the current local gateway configuration does not block representative SQLi/XSS-shaped payloads before they reach Java. To close the immediate customer-facing failure mode, Java now maps malformed request parameter type conversion to the standard structured `400` response instead of leaking a generic `500`.

The Higress gateway smoke now includes an authenticated malformed pagination probe:

- `GET /api/v1/reports?page=1'%20or%20'1'%3D'1&pageSize=1`
- JWT permission: `report:read`
- Expected result: HTTP `400`, API code `400`

This does not claim production-grade WAF blocking. It proves that even when local Higress passes an attack-shaped malformed parameter through, the application boundary fails closed with a structured client error.

## Evidence

- RED: `GlobalExceptionHandlerTest` first failed because `handleTypeMismatch(...)` did not exist.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core "-Dtest=com.company.report.shared.error.GlobalExceptionHandlerTest" test` passed `6/6`.
- RED: `higress_gateway_smoke.test.mjs` first failed because the attack fallback check export was absent.
- GREEN: `node --test tests/unit/node/higress_gateway_smoke.test.mjs` passed `12/12`.
- Runtime RED: `node scripts/higress-gateway-smoke.mjs` returned `500/code=500` for the malformed pagination attack probe before refreshing the Java container.
- Runtime GREEN: after refreshing `ir-java-smoke` from the current Java jar, `node scripts/higress-gateway-smoke.mjs` returned `passed=true`; the malformed pagination attack probe returned `400/code=400` with message `请求参数错误`.

## Files

- `backend/java-report-core/src/main/java/com/company/report/shared/error/GlobalExceptionHandler.java`
- `backend/java-report-core/src/test/java/com/company/report/shared/error/GlobalExceptionHandlerTest.java`
- `scripts/higress-gateway-smoke-lib.mjs`
- `tests/unit/node/higress_gateway_smoke.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- Production WAF blocking rules are still not proven by this closure.
- OIDC login behavior remains separate production hardening work.
- Production trusted TLS certificate chain and renewal remain separate hardening work.
