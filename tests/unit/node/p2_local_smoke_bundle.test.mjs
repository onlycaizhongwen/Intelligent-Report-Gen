import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildP2SmokeSteps,
} from '../../../scripts/p2-local-smoke-lib.mjs';

test('buildP2SmokeSteps returns the expected gateway security, UC-10, UC-11 and UC-14 command sequence', () => {
  const steps = buildP2SmokeSteps({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    tlsGatewayBaseUrl: 'https://127.0.0.1:28443',
    gatewayApiBaseUrl: 'http://127.0.0.1:28000/api/v1',
    gatewayOrigin: 'http://127.0.0.1:28000',
  });

  assert.equal(steps.length, 6);

  assert.equal(steps[0].name, 'higress-endpoint-security-smoke');
  assert.equal(steps[0].command, 'node');
  assert.deepEqual(steps[0].args, ['scripts/higress-gateway-smoke.mjs']);
  assert.equal(steps[0].env.HIGRESS_GATEWAY_BASE_URL, 'http://127.0.0.1:28000');

  assert.equal(steps[1].name, 'higress-tls-endpoint-security-smoke');
  assert.equal(steps[1].command, 'node');
  assert.deepEqual(steps[1].args, ['scripts/higress-gateway-smoke.mjs']);
  assert.equal(steps[1].env.HIGRESS_GATEWAY_BASE_URL, 'https://127.0.0.1:28443');
  assert.equal(steps[1].env.NODE_TLS_REJECT_UNAUTHORIZED, '0');

  assert.equal(steps[2].name, 'uc10-real-backend-e2e');
  assert.equal(steps[2].command, 'npm');
  assert.deepEqual(steps[2].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-collaboration-real-backend.spec.ts',
  ]);
  assert.equal(steps[2].workdir, 'frontend/web-console');
  assert.equal(steps[2].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[2].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[2].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[3].name, 'uc11-real-backend-e2e');
  assert.equal(steps[3].command, 'npm');
  assert.deepEqual(steps[3].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/user-rbac-real-backend.spec.ts',
  ]);
  assert.equal(steps[3].workdir, 'frontend/web-console');
  assert.equal(steps[3].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[3].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[3].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[4].name, 'uc11-higress-rbac-e2e');
  assert.equal(steps[4].command, 'npm');
  assert.deepEqual(steps[4].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/user-rbac-real-backend.spec.ts',
  ]);
  assert.equal(steps[4].workdir, 'frontend/web-console');
  assert.equal(steps[4].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[4].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[4].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[5].name, 'uc14-real-backend-e2e');
  assert.equal(steps[5].command, 'npm');
  assert.deepEqual(steps[5].args, [
    'run',
    'e2e:real-backend',
    '--',
    'tests/e2e/report-version-real-backend.spec.ts',
  ]);
  assert.equal(steps[5].workdir, 'frontend/web-console');
  assert.equal(steps[5].env.RUN_REAL_BACKEND_E2E, 'true');
  assert.equal(steps[5].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[5].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');
});
