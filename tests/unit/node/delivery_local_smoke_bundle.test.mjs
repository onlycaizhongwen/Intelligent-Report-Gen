import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDeliverySmokeSteps,
} from '../../../scripts/delivery-local-smoke-lib.mjs';

test('buildDeliverySmokeSteps returns the expected P0-P3 command sequence', () => {
  const steps = buildDeliverySmokeSteps({
    dashscopeApiKey: 'bundle-key',
    realBackendApiBaseUrl: 'http://127.0.0.1:28082/api/v1',
    realBackendOrigin: 'http://127.0.0.1:28082',
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    tlsGatewayBaseUrl: 'https://127.0.0.1:28443',
    gatewayApiBaseUrl: 'http://127.0.0.1:28000/api/v1',
    gatewayOrigin: 'http://127.0.0.1:28000',
    postgresContainer: 'bundle-postgres',
  });

  assert.equal(steps.length, 4);

  assert.deepEqual(
    steps.map((step) => step.name),
    ['p0-local-smoke', 'p1-local-smoke', 'p2-local-smoke', 'p3-local-smoke'],
  );

  assert.equal(steps[0].command, 'node');
  assert.deepEqual(steps[0].args, ['scripts/p0-local-smoke.mjs']);
  assert.equal(steps[0].env.P0_SMOKE_DASHSCOPE_API_KEY, 'bundle-key');
  assert.equal(steps[0].env.P0_SMOKE_REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28082/api/v1');
  assert.equal(steps[0].env.P0_SMOKE_REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28082');
  assert.equal(steps[0].env.P0_SMOKE_GATEWAY_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[0].env.P0_SMOKE_GATEWAY_ORIGIN, 'http://127.0.0.1:28000');
  assert.equal(steps[0].env.P0_SMOKE_POSTGRES_CONTAINER, 'bundle-postgres');

  assert.equal(steps[1].command, 'node');
  assert.deepEqual(steps[1].args, ['scripts/p1-local-smoke.mjs']);
  assert.equal(steps[1].env.P1_SMOKE_REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28082/api/v1');
  assert.equal(steps[1].env.P1_SMOKE_REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28082');
  assert.equal(steps[1].env.P1_SMOKE_HIGRESS_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[1].env.P1_SMOKE_HIGRESS_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[2].command, 'node');
  assert.deepEqual(steps[2].args, ['scripts/p2-local-smoke.mjs']);
  assert.equal(steps[2].env.P2_SMOKE_REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28082/api/v1');
  assert.equal(steps[2].env.P2_SMOKE_REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28082');
  assert.equal(steps[2].env.P2_SMOKE_HIGRESS_GATEWAY_BASE_URL, 'http://127.0.0.1:28000');
  assert.equal(steps[2].env.P2_SMOKE_HIGRESS_TLS_GATEWAY_BASE_URL, 'https://127.0.0.1:28443');
  assert.equal(steps[2].env.P2_SMOKE_HIGRESS_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[2].env.P2_SMOKE_HIGRESS_ORIGIN, 'http://127.0.0.1:28000');

  assert.equal(steps[3].command, 'node');
  assert.deepEqual(steps[3].args, ['scripts/p3-local-smoke.mjs']);
  assert.equal(steps[3].env.P3_SMOKE_REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28082/api/v1');
  assert.equal(steps[3].env.P3_SMOKE_REAL_BACKEND_ORIGIN, 'http://127.0.0.1:28082');
  assert.equal(steps[3].env.P3_SMOKE_HIGRESS_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[3].env.P3_SMOKE_HIGRESS_ORIGIN, 'http://127.0.0.1:28000');
});
