import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildP3SmokeSteps,
} from '../../../scripts/p3-local-smoke-lib.mjs';

test('buildP3SmokeSteps returns the expected UC-13, UC-07 and UC-08 command sequence', () => {
  const steps = buildP3SmokeSteps({
    gatewayApiBaseUrl: 'http://127.0.0.1:28000/api/v1',
    gatewayOrigin: 'http://127.0.0.1:28000',
  });

  assert.equal(steps.length, 10);

  assert.equal(steps[0].name, 'uc13-real-backend-e2e');
  assert.equal(steps[0].command, 'npm');
  assert.deepEqual(steps[0].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/dashboard-real-backend.spec.ts',
  ]);
  assert.equal(steps[0].workdir, 'frontend/web-console');
  assert.equal(steps[0].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[0].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[0].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[1].name, 'uc07-real-backend-e2e');
  assert.equal(steps[1].command, 'npm');
  assert.deepEqual(steps[1].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/data-source-sync-real-backend.spec.ts',
  ]);
  assert.equal(steps[1].workdir, 'frontend/web-console');
  assert.equal(steps[1].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[1].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[1].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[2].name, 'uc07-enterprise-data-source-docker-smoke');
  assert.equal(steps[2].command, 'node');
  assert.deepEqual(steps[2].args, ['scripts/data-source-enterprise-docker-smoke.mjs']);
  assert.equal(steps[2].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[2].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[3].name, 'uc08-real-backend-e2e');
  assert.equal(steps[3].command, 'npm');
  assert.deepEqual(steps[3].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/approval-inbox-real-backend.spec.ts',
  ]);
  assert.equal(steps[3].workdir, 'frontend/web-console');
  assert.equal(steps[3].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[3].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[3].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[4].name, 'uc08-rule-runtime-real-backend-e2e');
  assert.equal(steps[4].command, 'npm');
  assert.deepEqual(steps[4].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/rule-runtime-real-backend.spec.ts',
  ]);
  assert.equal(steps[4].workdir, 'frontend/web-console');
  assert.equal(steps[4].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[4].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[4].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[5].name, 'uc08-rule-runtime-higress-compensation-e2e');
  assert.equal(steps[5].command, 'npm');
  assert.deepEqual(steps[5].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/rule-runtime-real-backend.spec.ts',
    '-g',
    'webhook compensation',
  ]);
  assert.equal(steps[5].workdir, 'frontend/web-console');
  assert.equal(steps[5].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[5].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[5].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[6].name, 'uc08-rule-runtime-higress-replay-exhaustion-e2e');
  assert.equal(steps[6].command, 'npm');
  assert.deepEqual(steps[6].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/rule-runtime-real-backend.spec.ts',
    '-g',
    'automatic webhook replay exhaustion',
  ]);
  assert.equal(steps[6].workdir, 'frontend/web-console');
  assert.equal(steps[6].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[6].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[6].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');
  assert.equal(steps[6].env.RULE_WEBHOOK_REPLAY_WORKER_ENABLED, 'true');

  assert.equal(steps[7].name, 'uc08-delegate-rules-real-backend-e2e');
  assert.equal(steps[7].command, 'npm');
  assert.deepEqual(steps[7].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/approval-delegate-rules-real-backend.spec.ts',
  ]);
  assert.equal(steps[7].workdir, 'frontend/web-console');
  assert.equal(steps[7].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[7].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[7].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[8].name, 'uc08-approval-supplement-upload-real-backend-e2e');
  assert.equal(steps[8].command, 'npm');
  assert.deepEqual(steps[8].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/approval-inbox-real-backend.spec.ts',
    '-g',
    'approval supplement attachment upload',
  ]);
  assert.equal(steps[8].workdir, 'frontend/web-console');
  assert.equal(steps[8].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[8].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[8].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[9].name, 'uc08-approval-supplement-upload-higress-e2e');
  assert.equal(steps[9].command, 'npm');
  assert.deepEqual(steps[9].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/approval-inbox-real-backend.spec.ts',
    '-g',
    'approval supplement attachment upload',
  ]);
  assert.equal(steps[9].workdir, 'frontend/web-console');
  assert.equal(steps[9].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[9].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[9].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');
});
