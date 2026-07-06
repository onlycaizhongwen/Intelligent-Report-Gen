# Delivery Progress - Model Invocation Audit

Date: 2026-06-23

## Scope

- Replaced fixed model invocation demo response with persisted audit log lookup.
- Restricted lookup to `operationType = model_invocation`.
- Preserved existing audit list/history behavior.

## Changed Behavior

- `GET /api/v1/audit/model-invocations/{invocationId}` now returns persisted operation log fields and detail payload:
  - `invocationId`
  - `actorUserId`
  - `operationType`
  - `resourceType`
  - `resourceId`
  - `result`
  - model invocation detail fields such as `model`, `provider`, `promptHash`, `latencyMs`, `tokens`, `traceId`
  - `createdAt`
- Non-model audit log ids now fail with `model invocation audit log not found`.

## Verification

- RED: `.\mvnw.cmd -pl backend/java-report-core -Dtest=AuditApplicationServiceTest test`
  - Failed as expected on fixed `gpt-online-demo` response and missing type guard.
- GREEN: `.\mvnw.cmd -pl backend/java-report-core -Dtest=AuditApplicationServiceTest test`
  - 2 tests, 0 failures.
- Module regression: `.\mvnw.cmd -pl backend/java-report-core test`
  - 36 tests, 0 failures.

## Remaining Risk

- Model invocation write path still depends on callers creating `operation_logs` entries. A later slice should ensure every AI service call writes a `model_invocation` log with prompt hash, model route, token usage, latency, and failure details.
