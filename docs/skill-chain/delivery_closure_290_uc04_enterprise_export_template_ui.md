# Closure 290: UC-04 Enterprise Export Template Management UI

## Scope

- Requirement chain: `REQ-REPORT-004`, `REQ-AUTH-001`
- Use case: `UC-04`
- Target slice: close the centralized enterprise export template management UI gap after backend/API contract support already existed.
- Runtime route: browser -> Vite `/api/v1` proxy -> Higress `http://127.0.0.1:18000` -> Java report core -> PostgreSQL.

## Result

UC-04 now has a customer-visible enterprise export template management flow:

- The web console navigation exposes `Enterprise export templates`.
- The page lists managed templates with template id, status, version, company, header, footer, font, color, logo object key, and layout snapshot.
- Authorized users can create a managed brand/layout template.
- Authorized users can update a template, creating a new version through the backend.
- Authorized users can disable and re-enable a template.
- Authorized users can view version history and compare current `v2` style fields with the prior `v1` snapshot.
- P0 local smoke now includes `uc04-enterprise-export-template-higress-e2e`, so the UC-04 delivery tier checks both gateway-routed export download and gateway-routed template governance.

## Code Evidence

- `frontend/web-console/src/pages/reports/EnterpriseExportTemplates.vue`
- `frontend/web-console/src/router/index.ts`
- `frontend/web-console/src/App.vue`
- `frontend/web-console/tests/e2e/enterprise-export-templates.spec.ts`
- `frontend/web-console/tests/e2e/enterprise-export-templates-real-backend.spec.ts`
- `scripts/p0-local-smoke-lib.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`

## TDD And Runtime Evidence

RED, page entry:

```text
npm run e2e -- tests/e2e/enterprise-export-templates.spec.ts

failed waiting for:
getByRole('menuitem', { name: 'Enterprise export templates' })
```

RED, P0 smoke registration:

```text
node --test tests/unit/node/p0_local_smoke_bundle.test.mjs

failed with:
8 !== 9
```

GREEN, mock browser UI:

```text
npm run e2e -- tests/e2e/enterprise-export-templates.spec.ts

1 passed
```

GREEN, real Java backend browser:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18082
REAL_BACKEND_FRONTEND_PORT=5180
npm run e2e:real-backend -- tests/e2e/enterprise-export-templates-real-backend.spec.ts

1 passed
```

GREEN, Higress-routed real browser:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18000
REAL_BACKEND_FRONTEND_PORT=5181
npm run e2e:real-backend -- tests/e2e/enterprise-export-templates-real-backend.spec.ts

1 passed
```

Regression evidence:

```text
node --test tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/higress_gateway_smoke.test.mjs

6 passed
```

```text
npm run typecheck

vue-tsc --noEmit passed
```

```text
npm run test -- src/api/apiContracts.test.ts

30 passed
```

## Remaining Gaps

- Central management UI and real browser management flow are closed for the current enterprise export template lifecycle.
- Remaining UC-04 production hardening is now more focused on high-fidelity DOCX/PDF/PPTX rendering governance, richer template preview, and enterprise template usage impact analysis.
- OIDC login, TLS certificates, WAF behavior, production K8s Gateway API resources, and a full endpoint-by-endpoint gateway authorization matrix remain broader gateway hardening work.
