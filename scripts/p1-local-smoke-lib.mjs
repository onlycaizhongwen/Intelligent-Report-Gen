export function buildP1SmokeSteps({
  realBackendApiBaseUrl = 'http://127.0.0.1:18082/api/v1',
  realBackendOrigin = 'http://127.0.0.1:18082',
} = {}) {
  const mavenWrapper = process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw';
  return [
    {
      name: 'uc03-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-citation-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc09-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/share-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc09-share-audit-postgres-it',
      command: mavenWrapper,
      args: [
        '-pl',
        'backend/java-report-core',
        '-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres+rateLimitsRepeatedInvalidSharePasswordAttemptsInPostgres+requiresShareChallengeAfterRepeatedInvalidPasswordAttemptsInPostgres+rateLimitsRepeatedInvalidSharePasswordAttemptsByRiskFingerprintInPostgres+persistsShareDownloadFormatScopeAndEnforcesItInPostgres+persistsShareMaxAccessCountAndEnforcesItInPostgres+persistsShareVisitorScopeAndEnforcesItInPostgres+persistsSingleUseShareAndEnforcesItInPostgres',
        'test',
      ],
      workdir: '.',
      env: {
        RUN_POSTGRES_INTEGRATION: 'true',
      },
    },
    {
      name: 'uc12-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/audit-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
  ];
}
