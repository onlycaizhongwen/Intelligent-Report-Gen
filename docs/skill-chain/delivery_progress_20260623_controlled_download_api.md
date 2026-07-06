# 2026-06-23 Controlled Download API Increment

Target: avoid exposing raw MinIO presigned URLs from report export status and provide a controlled file download URL boundary.

Completed:

- Added `ReportExportStorage.createDownloadUrl(objectKey)` and implemented it in `MinioReportExportStorage`.
- Added `ReportExportFileRepository.findByExportFileId(exportFileId)` and implemented it in JDBC persistence.
- Updated report export creation to store a controlled business URL:
  - `/api/v1/files/report-exports/{exportFileId}/download-url`
- Added `ReportApplicationService.createExportDownloadUrl(exportFileId)` to look up export metadata and refresh a short-lived MinIO presigned URL.
- Added `FileController` endpoint:
  - `GET /api/v1/files/report-exports/{exportFileId}/download-url`
- Extended PostgreSQL integration coverage so export metadata can be read by `exportFileId` alone.

Verification evidence:

- RED first: `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest#createControlledDownloadUrlForExportFile test` initially failed because `createExportDownloadUrl()` and storage refresh support were missing.
- Note: one targeted method filter later matched 0 tests, so the valid verification was the full class run.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportApplicationServiceTest test`: 11 tests, 0 failures.
- `RUN_POSTGRES_INTEGRATION=true .\mvnw.cmd -pl backend/java-report-core -Dtest=JdbcReportGenerationTaskRepositoryPostgresIT test`: 8 tests, 0 failures.
- `.\mvnw.cmd -pl backend/java-report-core -Dtest=ReportCoreApplicationContextTest test`: 1 test, 0 failures; Spring registered 41 request mappings.
- `.\mvnw.cmd -pl backend/java-report-core test`: 27 tests, 0 failures.

Still needed:

- Download access audit log entry.
- Report ownership/permission checks in the file URL endpoint beyond current permission aspect.
- Expired URL refresh and failure handling policy.
