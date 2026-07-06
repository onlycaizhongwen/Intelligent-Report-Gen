import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildP1SmokeSteps,
} from '../../../scripts/p1-local-smoke-lib.mjs';

test('buildP1SmokeSteps returns the expected UC-03, UC-09 and UC-12 command sequence', () => {
  const steps = buildP1SmokeSteps();

  assert.equal(steps.length, 4);

  assert.equal(steps[0].name, 'uc03-real-backend-e2e');
  assert.equal(steps[0].command, 'npm');
  assert.deepEqual(steps[0].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-citation-real-backend.spec.ts',
  ]);
  assert.equal(steps[0].workdir, 'frontend/web-console');
  assert.equal(steps[0].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[0].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[0].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[1].name, 'uc09-real-backend-e2e');
  assert.equal(steps[1].command, 'npm');
  assert.deepEqual(steps[1].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/share-real-backend.spec.ts',
  ]);
  assert.equal(steps[1].workdir, 'frontend/web-console');
  assert.equal(steps[1].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[1].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[1].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[2].name, 'uc09-share-audit-postgres-it');
  assert.equal(steps[2].command, process.platform === 'win32' ? '.\\mvnw.cmd' : './mvnw');
  assert.deepEqual(steps[2].args, [
    '-pl',
    'backend/java-report-core',
    '-Dtest=JdbcReportGenerationTaskRepositoryPostgresIT#writesShareViewAndDownloadAuditEvidenceForExternalAccessInPostgres+rateLimitsRepeatedInvalidSharePasswordAttemptsInPostgres+requiresShareChallengeAfterRepeatedInvalidPasswordAttemptsInPostgres+rateLimitsRepeatedInvalidSharePasswordAttemptsByRiskFingerprintInPostgres+persistsShareDownloadFormatScopeAndEnforcesItInPostgres+persistsShareMaxAccessCountAndEnforcesItInPostgres+persistsShareVisitorScopeAndEnforcesItInPostgres+persistsSingleUseShareAndEnforcesItInPostgres',
    'test',
  ]);
  assert.equal(steps[2].workdir, '.');
  assert.equal(steps[2].env.RUN_POSTGRES_INTEGRATION, 'true');

  assert.equal(steps[3].name, 'uc12-real-backend-e2e');
  assert.equal(steps[3].command, 'npm');
  assert.deepEqual(steps[3].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/audit-real-backend.spec.ts',
  ]);
  assert.equal(steps[3].workdir, 'frontend/web-console');
  assert.equal(steps[3].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[3].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[3].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');
});
