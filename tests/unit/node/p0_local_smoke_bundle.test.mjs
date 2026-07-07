import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildP0SmokeSteps,
} from '../../../scripts/p0-local-smoke-lib.mjs';

test('buildP0SmokeSteps returns the expected UC-01, UC-02, UC-06 and UC-04 command sequence', () => {
  const steps = buildP0SmokeSteps({
    dashscopeApiKey: 'test-key',
    gatewayApiBaseUrl: 'http://127.0.0.1:28000/api/v1',
    gatewayOrigin: 'http://127.0.0.1:28000',
  });

  assert.equal(steps.length, 10);

  assert.equal(steps[0].name, 'uc01-worker');
  assert.equal(steps[0].command, 'node');
  assert.ok(steps[0].args.includes('scripts/uc01-real-provider-worker.mjs'));
  assert.equal(steps[0].env.UC01_REAL_PROVIDER_API_KEY, 'test-key');

  assert.equal(steps[1].name, 'uc01-strict-smoke');
  assert.equal(steps[1].command, 'node');
  assert.ok(steps[1].args.includes('scripts/uc01-real-provider-smoke.mjs'));
  assert.equal(steps[1].env.UC01_SMOKE_POSTGRES_CONTAINER, 'ir-postgres');
  assert.equal(steps[1].env.UC01_SMOKE_STRICT_AUDIT, 'true');

  assert.equal(steps[2].name, 'uc06-real-backend-e2e');
  assert.equal(steps[2].command, 'npm');
  assert.deepEqual(steps[2].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/knowledge-upload-real-backend.spec.ts',
  ]);
  assert.equal(steps[2].workdir, 'frontend/web-console');
  assert.equal(steps[2].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[2].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[2].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[3].name, 'uc02-report-template-real-backend-e2e');
  assert.equal(steps[3].command, 'npm');
  assert.deepEqual(steps[3].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-template-real-backend.spec.ts',
  ]);
  assert.equal(steps[3].workdir, 'frontend/web-console');
  assert.equal(steps[3].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[3].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[3].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[4].name, 'uc02-template-completion-smoke');
  assert.equal(steps[4].command, 'node');
  assert.deepEqual(steps[4].args, ['scripts/uc02-template-completion-smoke.mjs']);
  assert.equal(steps[4].env.UC02_TEMPLATE_COMPLETION_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[4].env.UC02_TEMPLATE_COMPLETION_POSTGRES_CONTAINER, 'ir-postgres');

  assert.equal(steps[5].name, 'uc02-template-completion-higress-smoke');
  assert.equal(steps[5].command, 'node');
  assert.deepEqual(steps[5].args, ['scripts/uc02-template-completion-smoke.mjs']);
  assert.equal(steps[5].env.UC02_TEMPLATE_COMPLETION_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[5].env.UC02_TEMPLATE_COMPLETION_POSTGRES_CONTAINER, 'ir-postgres');

  assert.equal(steps[6].name, 'uc02-report-template-higress-e2e');
  assert.equal(steps[6].command, 'npm');
  assert.deepEqual(steps[6].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-template-real-backend.spec.ts',
  ]);
  assert.equal(steps[6].workdir, 'frontend/web-console');
  assert.equal(steps[6].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[6].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[6].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[7].name, 'uc04-higress-export-e2e');
  assert.equal(steps[7].command, 'npm');
  assert.deepEqual(steps[7].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-export-real-backend.spec.ts',
  ]);
  assert.equal(steps[7].workdir, 'frontend/web-console');
  assert.equal(steps[7].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[7].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[7].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[8].name, 'uc04-enterprise-export-template-higress-e2e');
  assert.equal(steps[8].command, 'npm');
  assert.deepEqual(steps[8].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/enterprise-export-templates-real-backend.spec.ts',
  ]);
  assert.equal(steps[8].workdir, 'frontend/web-console');
  assert.equal(steps[8].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[8].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[8].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[9].name, 'uc04-real-backend-e2e');
  assert.equal(steps[9].command, 'npm');
  assert.deepEqual(steps[9].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-export-real-backend.spec.ts',
  ]);
  assert.equal(steps[9].workdir, 'frontend/web-console');
  assert.equal(steps[9].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[9].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[9].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');
});
