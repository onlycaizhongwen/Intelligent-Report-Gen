# Delivery Closure 343: P1 Higress Browser Smoke

## Scope

- Requirement: `REQ-REPORT-003`, `REQ-COLLAB-001`, `REQ-AUDIT-001`
- User journey: citation traceability, external share access with controlled download, personal history and audit boundary
- Runtime boundary: browser/client -> Higress -> Java report core -> PostgreSQL and MinIO-backed report/share/export evidence

## Result

P1 local smoke now proves the core browser journeys through Higress as well as the direct Java backend:

- `buildP1SmokeSteps(...)` includes Higress-routed browser variants for UC-03 citation traceability, UC-09 share access, and UC-12 audit/history.
- `scripts/p1-local-smoke.mjs` accepts `P1_SMOKE_HIGRESS_API_BASE_URL` and `P1_SMOKE_HIGRESS_ORIGIN` for local gateway port overrides.
- `buildDeliverySmokeSteps(...)` passes the top-level delivery gateway API base URL and origin into the P1 smoke bundle.
- `share-real-backend.spec.ts` now creates a per-run managed enterprise export template before generating the share-download artifact, so UC-09 no longer depends on pre-seeded `enterprise-default` data.

## Evidence

- RED: `node --test tests/unit/node/p1_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs` failed because P1 had only four steps and delivery did not pass `P1_SMOKE_HIGRESS_API_BASE_URL` / `P1_SMOKE_HIGRESS_ORIGIN`.
- GREEN: `node --test tests/unit/node/p1_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs` passed `2/2`.
- Runtime RED: Higress browser smoke initially failed in `share-real-backend.spec.ts` with `404 enterprise export template not found or inactive: enterprise-default`.
- Runtime GREEN: `RUN_REAL_BACKEND_E2E=true REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1 REAL_BACKEND_ORIGIN=http://127.0.0.1:18000 npm run e2e:real-backend -- tests/e2e/report-citation-real-backend.spec.ts tests/e2e/share-real-backend.spec.ts tests/e2e/audit-real-backend.spec.ts` passed `3/3`.

## Files

- `scripts/p1-local-smoke-lib.mjs`
- `scripts/p1-local-smoke.mjs`
- `scripts/delivery-local-smoke-lib.mjs`
- `tests/unit/node/p1_local_smoke_bundle.test.mjs`
- `tests/unit/node/delivery_local_smoke_bundle.test.mjs`
- `frontend/web-console/tests/e2e/share-real-backend.spec.ts`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure proves the P1 browser journeys through local Higress with existing local Docker services.
- Production OIDC, trusted TLS, WAF policy, and full one-command delivery smoke remain separate hardening work.
