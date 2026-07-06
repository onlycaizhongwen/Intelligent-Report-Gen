# Closure 285: UC-02 Template Completion Controlled Worker Smoke

## Scope

- Requirement chain: `REQ-REPORT-001`, `REQ-REPORT-002`, `REQ-REPORT-003`
- Use case: `UC-02`
- Target slice: prove that a template-created report can move past outline confirmation into completed report persistence through the same Java worker completion callback boundary.
- Runtime route: `GET /api/v1/report-templates` -> `POST /api/v1/reports/template-generation-tasks` -> `PUT /api/v1/reports/generation-tasks/{taskId}/outline` -> `POST /api/v1/reports/generation-tasks/{taskId}/completion` -> `GET /api/v1/reports/{reportId}` -> PostgreSQL `model_invocations`.

## Result

UC-02 now has a repeatable local completion smoke that does not require an external LLM API key. The smoke uses the real Java backend and PostgreSQL, creates a real template task, confirms its outline, sends a controlled worker-style completion callback, then verifies:

- report status is `completed`
- `currentVersionId` is present
- report sections are persisted
- at least one citation is persisted
- `model_invocations` contains the controlled worker audit row with tokens and trace id

This narrows the prior UC-02 gap from "template-generated reports have no completion evidence" to "external provider worker completion still needs credentialed P0 evidence."

## Code Evidence

- `scripts/uc02-template-completion-smoke.mjs`
- `scripts/uc02-template-completion-smoke-lib.mjs`
- `scripts/p0-local-smoke-lib.mjs`
- `tests/unit/node/uc02_template_completion_smoke.test.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`

## TDD Evidence

RED:

```text
node --test tests/unit/node/uc02_template_completion_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs

failed with:
ERR_MODULE_NOT_FOUND scripts/uc02-template-completion-smoke-lib.mjs
5 !== 6
```

First real-smoke RED after implementation:

```text
node scripts/uc02-template-completion-smoke.mjs

reportSummary.status = completed
reportSummary.sectionCount = 2
reportSummary.citationCount = 1
failures = ["MODEL_INVOCATION_MISSING"]
```

Root cause:

The smoke initially proved report completion but did not query the local PostgreSQL container by default, so model audit evidence was not verified.

GREEN:

```text
node scripts/uc02-template-completion-smoke.mjs

reportSummary.status = completed
reportSummary.currentVersionId = 269
reportSummary.sectionCount = 2
reportSummary.citationCount = 1
modelInvocation.provider = local-controlled-worker
modelInvocation.modelName = uc02-template-completion-smoke
modelInvocation.status = succeeded
modelInvocation.totalTokens = 30
failures = []
```

Additional regression evidence:

```text
node --test tests/unit/node/uc02_template_completion_smoke.test.mjs tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/delivery_local_smoke_bundle.test.mjs
6 passed

python -m pytest tests/unit/python/test_report_generation_worker.py tests/unit/python/test_llm_provider.py -q
15 passed

RUN_REAL_BACKEND_E2E=true npm run e2e:real-backend -- tests/e2e/report-template-real-backend.spec.ts
2 passed

npm run typecheck
vue-tsc --noEmit passed

npm run test -- src/api/apiContracts.test.ts
30 passed
```

## Remaining Gaps

- This closure uses a controlled local worker-style callback. It does not prove external LLM provider completion.
- Full P0 smoke still requires `DASHSCOPE_API_KEY` for `uc01-worker` and `uc01-strict-smoke`.
- Higress-authenticated browser coverage for this exact UC-02 completion path remains future hardening.
