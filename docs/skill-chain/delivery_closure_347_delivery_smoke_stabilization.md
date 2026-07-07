# Delivery Closure 347: Delivery Smoke Stabilization

## Scope

- Requirements: `REQ-REPORT-004`, `REQ-RULE-001`, `REQ-AUTH-001`
- User journeys: UC-04 report export with governed enterprise templates, UC-08 rule runtime compensation, P2 collaboration/RBAC
- Runtime boundary: local browser E2E -> Java backend on `18082` -> Higress on `18000` -> PostgreSQL/MinIO/RocketMQ and worker containers

## Result

The full local delivery smoke is green after stabilizing three acceptance gaps:

- P2 collaboration fixture now includes `collaboration:write`, matching the backend `@RequiresPermission("collaboration:write")` boundary for submitting report comments.
- UC-08 manual webhook retry now disables automatic async replay only for the manual-retry scenario, preventing the replay worker from racing the row-level retry action while preserving the separate automatic replay exhaustion coverage.
- UC-04 report detail now loads active governed enterprise export templates across pages instead of only reading the first 20 templates, so older local databases do not hide the newly created template from the browser selector.

## Evidence

- Targeted collaboration: `npm run e2e:real-backend -- tests/e2e/report-collaboration-real-backend.spec.ts` passed `1/1`.
- P2 aggregate: `node scripts/p2-local-smoke.mjs` passed all `6` planned steps.
- Targeted rule runtime manual retry: `npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts -g "retries failed webhook compensation successfully"` passed `1/1`.
- Rule runtime regression: `npm run e2e:real-backend -- tests/e2e/rule-runtime-real-backend.spec.ts` passed `4/4`.
- P3 aggregate: `node scripts/p3-local-smoke.mjs` passed all `10` planned steps, including direct Java and Higress rule-runtime paths.
- Governed template pagination: `npm run e2e -- tests/e2e/report-generation.spec.ts -g "加载多页已治理企业导出模板"` passed `1/1`.
- Real backend export: `npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts` passed `3/3`.
- P0 aggregate: `node scripts/p0-local-smoke.mjs` passed all `13` planned steps.
- Full delivery: `node scripts/delivery-local-smoke.mjs` passed P0, P1, P2, and P3 end to end with exit code `0`.

## Files

- `frontend/web-console/tests/e2e/report-collaboration-real-backend.spec.ts`
- `frontend/web-console/tests/e2e/rule-runtime-real-backend.spec.ts`
- `frontend/web-console/tests/e2e/report-generation.spec.ts`
- `frontend/web-console/src/pages/reports/ReportDetail.vue`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- The local UC-01 host relay remains a development smoke workaround for the Docker-to-DashScope TLS boundary and must not become production LLM egress architecture.
- Production OIDC, managed TLS certificates, WAF tuning, and cloud egress policies remain outside this local delivery smoke.
