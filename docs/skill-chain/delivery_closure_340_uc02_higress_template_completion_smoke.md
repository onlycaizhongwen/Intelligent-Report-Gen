# Delivery Closure 340: UC-02 Higress Template Completion Smoke

## Scope

- Requirement: `REQ-REPORT-001`, `REQ-REPORT-002`, `REQ-REPORT-003`, `REQ-AI-001`
- User journey: template-driven report creation, outline confirmation, controlled worker completion, report detail verification
- Runtime boundary: browser/client -> Higress -> Java report core -> PostgreSQL model audit and report content persistence

## Result

Template-driven report generation now has a repeatable Higress-routed completion smoke, not only a creation and outline-confirmation browser flow:

- `buildP0SmokeSteps(...)` now includes `uc02-template-completion-higress-smoke`.
- The new P0 step runs `scripts/uc02-template-completion-smoke.mjs` with `UC02_TEMPLATE_COMPLETION_BASE_URL` pointed at the Higress gateway API base URL.
- The smoke creates a real template task, confirms the outline, posts a controlled worker completion payload through Higress, verifies the completed report detail, and checks `model_invocations` in local PostgreSQL.
- `scripts/p0-local-smoke.mjs` now accepts `P0_SMOKE_GATEWAY_API_BASE_URL` and `P0_SMOKE_GATEWAY_ORIGIN` so the P0 bundle can follow local gateway port changes without code edits.

## Evidence

- RED: `node --test tests/unit/node/p0_local_smoke_bundle.test.mjs` failed with `9 !== 10`, proving the P0 bundle did not include a Higress-routed template completion step.
- GREEN: `node --test tests/unit/node/p0_local_smoke_bundle.test.mjs tests/unit/node/uc02_template_completion_smoke.test.mjs` passed `5/5`.
- Direct Java runtime: `UC02_TEMPLATE_COMPLETION_BASE_URL=http://127.0.0.1:18082/api/v1 node scripts/uc02-template-completion-smoke.mjs` returned `failures=[]`, `taskId=330`, `reportId=521`, `status=completed`, `currentVersionId=287`, `sectionCount=2`, `citationCount=1`, `provider=local-controlled-worker`, `modelName=uc02-template-completion-smoke`, and `totalTokens=30`.
- Higress runtime: `UC02_TEMPLATE_COMPLETION_BASE_URL=http://127.0.0.1:18000/api/v1 node scripts/uc02-template-completion-smoke.mjs` returned `failures=[]`, `taskId=331`, `reportId=522`, `status=completed`, `currentVersionId=288`, `sectionCount=2`, `citationCount=1`, `provider=local-controlled-worker`, `modelName=uc02-template-completion-smoke`, and `totalTokens=30`.

## Files

- `scripts/p0-local-smoke-lib.mjs`
- `scripts/p0-local-smoke.mjs`
- `tests/unit/node/p0_local_smoke_bundle.test.mjs`
- `docs/skill-chain/prototype_requirement_acceptance_matrix.md`

## Remaining Risk

- This closure proves the Higress-routed controlled-worker completion path. It does not require or prove an external LLM provider key.
- Full credentialed P0 smoke with `DASHSCOPE_API_KEY`, provider budget governance, OIDC, trusted TLS, and production WAF remain separate production hardening work.
