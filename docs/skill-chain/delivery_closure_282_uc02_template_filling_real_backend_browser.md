# Closure 282: UC-02 Template Filling Real Backend Browser Acceptance

## Scope

- Requirement chain: `REQ-REPORT-001`, `REQ-REPORT-002`
- Use case: `UC-02`
- Target slice: template-driven report creation from the Vue browser page against the real Java backend.
- Runtime route: `/reports/create` -> `GET /api/v1/report-templates` -> user-filled schema fields -> `POST /api/v1/reports/template-generation-tasks` -> PostgreSQL-backed task/report persistence.

## Result

Template filling now has browser-level real-backend acceptance evidence. The create-report page reads the active template schema from the Java backend, renders fields dynamically, submits user-entered field values, and displays customer-checkable evidence:

- `模板任务 {taskId}`
- `报告 {reportId}`
- `模板 {templateId}`
- `版本 {version}`
- submitted `templateSnapshot.parameters` key/value pairs

The E2E also calls the real report detail API after submission and verifies the persisted report is addressable by `reportId`.

## Evidence

- Browser E2E: `frontend/web-console/tests/e2e/report-template-real-backend.spec.ts`
- Frontend result display: `frontend/web-console/src/pages/reports/ReportCreate.vue`
- API typing: `frontend/web-console/src/api/reportApi.ts`
- P0 smoke bundle entry: `scripts/p0-local-smoke-lib.mjs`, step `uc02-report-template-real-backend-e2e`

## TDD Evidence

RED:

```text
RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/report-template-real-backend.spec.ts

failed waiting for:
getByText('模板任务 295')
```

Root cause:

The page submitted the real template task successfully but only rendered the compact `taskId status` string. It did not expose `reportId`, template id/version, or submitted snapshot parameters for browser-level customer acceptance.

GREEN:

```text
RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/report-template-real-backend.spec.ts

1 passed
```

## Remaining Gaps

- This closure proves template task creation and snapshot persistence evidence, not the downstream AI worker completion for template-generated reports.
- Full P0 smoke was not rerun because it requires an external `DASHSCOPE_API_KEY`; only the new target E2E and smoke step registration should be treated as verified here.
- Live Higress route smoke remains open.
