# 2026-06-23 Generation Failure And Retry Increment

Target: make report generation failure visible and retryable through persisted task state and SSE error events.

Completed:

- Added domain transitions `ReportGenerationTask.fail()` and `ReportGenerationTask.retry()`.
- Exposed `failureReason` in task responses.
- Added `ReportApplicationService.failGenerationTask()` to persist `retryable` or `failed` state and append an SSE `error` event.
- Added `ReportApplicationService.retryGenerationTask()` to move failed/retryable tasks back to `running` and append a retry `stage` event.
- Added controlled REST endpoints:
  - `POST /api/v1/reports/generation-tasks/{taskId}/failure`
  - `POST /api/v1/reports/generation-tasks/{taskId}/retry`
- Added PostgreSQL integration coverage for persisted `failure_reason` and replayable error event.

Verification evidence:

- RED first: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#marksGenerationTaskFailedWithPersistedErrorEventAndAllowsRetry test` initially failed because failure/retry methods were missing.
- GREEN: same targeted test passed: 1 test, 0 failures.
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#persistsFailureAndRetryableGenerationTaskStateWithErrorEvent test`: 1 test, 0 failures.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`: 10 tests, 0 failures.
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`: 8 tests, 0 failures.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`: 1 test, 0 failures; Spring registered 40 request mappings.
- `.\mvnw.cmd -pl backend/java-report-core test`: 26 tests, 0 failures.

Still needed:

- Worker callback authentication and route hardening for the failure endpoint.
- Retry attempt counters and max retry policy.
- Python RAG/GPT worker integration to call failure/success callbacks from real generation execution.
