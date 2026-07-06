# Closure 284: UC-02 Template Outline Confirmation Real Backend Browser Acceptance

## Scope

- Requirement chain: `REQ-REPORT-001`, `REQ-REPORT-002`, `REQ-AI-001`
- Use case: `UC-02`
- Target slice: continue the template-driven report flow from task creation into outline confirmation through the Vue browser page against the real Java backend.
- Runtime route: `/reports/create` -> `POST /api/v1/reports/template-generation-tasks` -> `查看大纲` -> `/reports/{taskId}/outline` -> `PUT /api/v1/reports/generation-tasks/{taskId}/outline` -> optional SSE generation stream.

## Result

Template filling no longer stops at task creation evidence. After a real template task is created, the browser now exposes a `查看大纲` link based on the returned `taskId`. The outline page calls the real Java outline confirmation API before opening the generation stream, then displays customer-checkable confirmation evidence:

- `大纲已确认：{taskId}`
- `下一阶段：retrieval`

This closes the browser-visible handoff from template parameters to the report generation lifecycle stage that triggers downstream worker processing.

## Code Evidence

- `frontend/web-console/src/pages/reports/ReportCreate.vue`
- `frontend/web-console/src/pages/reports/ReportOutline.vue`
- `frontend/web-console/src/api/reportApi.ts`
- `frontend/web-console/tests/e2e/report-template-real-backend.spec.ts`
- `frontend/web-console/tests/e2e/report-generation.spec.ts`
- `frontend/web-console/src/api/apiContracts.test.ts`

## TDD Evidence

RED:

```text
RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/report-template-real-backend.spec.ts

failed waiting for:
getByRole('link', { name: '查看大纲' })
```

Root cause:

The browser could create and display a template task, but the page had no customer-facing navigation into the outline confirmation step. The outline page also only opened the SSE stream and did not call the existing Java `PUT /reports/generation-tasks/{taskId}/outline` contract.

GREEN:

```text
RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/report-template-real-backend.spec.ts

2 passed
```

Additional regression evidence:

```text
npm run typecheck
vue-tsc --noEmit passed

npm run test -- src/api/apiContracts.test.ts
30 passed

npm run e2e -- tests/e2e/report-generation.spec.ts
8 passed
```

## Remaining Gaps

- This closure confirms the real Java outline confirmation boundary and UI handoff, but it does not prove external LLM worker completion for template-generated reports.
- Full P0 smoke was not rerun because it requires `DASHSCOPE_API_KEY`.
- Higress route smoke for this exact authenticated browser flow remains separate from the existing gateway route smoke.
