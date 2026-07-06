# Closure 291: UC-04 Managed Export Template Selection

## Scope

- Requirement chain: `REQ-REPORT-004`
- Use case: `UC-04`
- Target slice: connect managed enterprise export templates from Closure 290 back into the report detail export workflow.

## Result

Report detail export can now consume governed enterprise export templates:

- The report detail page loads active enterprise export templates.
- Users can select a managed template from `已治理企业模板`.
- The page previews the selected template version and brand snapshot.
- When a managed template is selected, the export request sends `format` and `templateId` only.
- The page no longer re-sends inline `brand` for managed-template export, allowing Java to resolve the governed `brandSnapshot`.
- Manual brand entry remains supported when no managed template is selected.

## Code Evidence

- `frontend/web-console/src/pages/reports/ReportDetail.vue`
- `frontend/web-console/tests/e2e/report-generation.spec.ts`

## TDD Evidence

RED:

```text
npm run e2e -- tests/e2e/report-generation.spec.ts -g "已治理企业导出模板"

failed waiting for:
getByLabel('已治理企业模板')
```

GREEN:

```text
npm run e2e -- tests/e2e/report-generation.spec.ts -g "已治理企业导出模板"

1 passed
```

Regression evidence:

```text
npm run e2e -- tests/e2e/report-generation.spec.ts -g "报告详情支持企业 Word"

1 passed
```

```text
npm run e2e -- tests/e2e/report-generation.spec.ts

9 passed
```

```text
npm run typecheck

vue-tsc --noEmit passed
```

## Remaining Gaps

- Managed template selection and export request shape are closed in the browser UI.
- Real backend/Higress export with a selected managed template should be added to the existing UC-04 real-backend spec in the next closure.
- Rich visual template preview and usage impact analysis remain future production hardening work.
