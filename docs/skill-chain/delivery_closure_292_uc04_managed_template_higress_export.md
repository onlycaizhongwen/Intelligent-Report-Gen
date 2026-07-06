# Closure 292: UC-04 Managed Template Higress Export

## Scope

- Requirement chain: `REQ-REPORT-004`, `REQ-AUTH-001`
- Use case: `UC-04`
- Target slice: prove managed enterprise export template selection works against the real backend and local Higress, not only mock browser tests.
- Runtime route: browser -> Vite `/api/v1` proxy -> Higress `http://127.0.0.1:18000` -> Java report core -> PostgreSQL + MinIO.

## Result

Managed enterprise template export is now verified end to end:

- The test creates a managed enterprise export template through the real Java API.
- The test creates and completes a real report task.
- The browser opens report detail, selects the managed template, and exports PDF.
- The browser export request sends `format=pdf` and `templateId` only.
- The request does not include inline `brand`, so Java resolves the governed template snapshot.
- The Java response includes `brandSnapshot.templateId` and `brandSnapshot.templateVersion=v1`.
- The controlled download URL resolves and the MinIO artifact download returns a non-empty file.

## Code Evidence

- `frontend/web-console/src/pages/reports/ReportDetail.vue`
- `frontend/web-console/tests/e2e/report-export-real-backend.spec.ts`
- `frontend/web-console/tests/e2e/report-generation.spec.ts`
- `docs/skill-chain/delivery_closure_291_uc04_managed_template_export_selection.md`

## Runtime Evidence

Direct Java:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18082/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18082
REAL_BACKEND_FRONTEND_PORT=5182
npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts -g managed

1 passed
```

Higress routed:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18000
REAL_BACKEND_FRONTEND_PORT=5183
npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts -g managed

1 passed
```

Full UC-04 real export regression through Higress:

```text
RUN_REAL_BACKEND_E2E=true
REAL_BACKEND_API_BASE_URL=http://127.0.0.1:18000/api/v1
REAL_BACKEND_ORIGIN=http://127.0.0.1:18000
REAL_BACKEND_FRONTEND_PORT=5184
npm run e2e:real-backend -- tests/e2e/report-export-real-backend.spec.ts

3 passed
```

## Remaining Gaps

- Real backend/Higress export with selected managed template is closed.
- Remaining UC-04 production hardening is now richer visual preview, usage impact analysis, high-fidelity rendering governance, and broader production gateway concerns such as OIDC/TLS/WAF.
