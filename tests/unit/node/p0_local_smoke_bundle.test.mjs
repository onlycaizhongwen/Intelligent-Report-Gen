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
    providerRelayPort: '19091',
    proxyEnv: {
      HTTPS_PROXY: 'http://host.docker.internal:7890',
      NO_PROXY: 'localhost,127.0.0.1',
    },
  });

  assert.equal(steps.length, 13);
  assert.deepEqual(
    steps.map((step) => step.name),
    [
      'uc01-provider-host-relay',
      'uc01-provider-preflight',
      'uc01-worker',
      'uc01-strict-smoke',
      'uc06-document-parse-worker',
      'uc06-real-backend-e2e',
      'uc02-report-template-real-backend-e2e',
      'uc02-template-completion-smoke',
      'uc02-template-completion-higress-smoke',
      'uc02-report-template-higress-e2e',
      'uc04-higress-export-e2e',
      'uc04-enterprise-export-template-higress-e2e',
      'uc04-real-backend-e2e',
    ],
  );

  assert.equal(steps[0].command, 'node');
  assert.deepEqual(steps[0].args, ['scripts/uc01-provider-host-relay-start.mjs']);
  assert.equal(steps[0].env.UC01_PROVIDER_RELAY_PORT, '19091');

  assert.equal(steps[1].command, 'node');
  assert.deepEqual(steps[1].args, ['scripts/uc01-provider-connectivity-preflight.mjs']);
  assert.equal(steps[1].env.UC01_PROVIDER_PREFLIGHT_API_KEY, 'test-key');
  assert.equal(
    steps[1].env.UC01_PROVIDER_PREFLIGHT_LLM_BASE_URL,
    'http://host.docker.internal:19091/compatible-mode/v1',
  );
  assert.equal(steps[1].env.HTTPS_PROXY, 'http://host.docker.internal:7890');
  assert.equal(steps[1].env.NO_PROXY, 'localhost,127.0.0.1');

  assert.equal(steps[2].command, 'node');
  assert.deepEqual(steps[2].args, ['scripts/uc01-real-provider-worker.mjs']);
  assert.equal(steps[2].env.UC01_REAL_PROVIDER_API_KEY, 'test-key');
  assert.equal(
    steps[2].env.UC01_REAL_PROVIDER_LLM_BASE_URL,
    'http://host.docker.internal:19091/compatible-mode/v1',
  );
  assert.equal(steps[2].env.HTTPS_PROXY, 'http://host.docker.internal:7890');
  assert.equal(steps[2].env.NO_PROXY, 'localhost,127.0.0.1');

  assert.equal(steps[3].command, 'node');
  assert.deepEqual(steps[3].args, ['scripts/uc01-real-provider-smoke.mjs']);
  assert.equal(steps[3].env.UC01_SMOKE_POSTGRES_CONTAINER, 'ir-postgres');
  assert.equal(steps[3].env.UC01_SMOKE_STRICT_AUDIT, 'true');

  assert.equal(steps[4].command, 'node');
  assert.deepEqual(steps[4].args, ['scripts/document-parse-worker-smoke.mjs']);

  assert.equal(steps[5].workdir, 'frontend/web-console');
  assert.equal(steps[5].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
  assert.equal(steps[5].env.REAL_BACKEND_ORIGIN, 'http://127.0.0.1:18082');

  assert.equal(steps[8].env.UC02_TEMPLATE_COMPLETION_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[9].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:28000/api/v1');
  assert.equal(steps[12].env.REAL_BACKEND_API_BASE_URL, 'http://127.0.0.1:18082/api/v1');
});

test('buildP0SmokeSteps can disable the local host relay for direct provider validation', () => {
  const steps = buildP0SmokeSteps({
    dashscopeApiKey: 'test-key',
    providerRelayMode: 'none',
  });

  assert.equal(steps[0].name, 'uc01-provider-preflight');
  assert.equal(steps[0].env.UC01_PROVIDER_PREFLIGHT_LLM_BASE_URL, undefined);
  assert.equal(steps[1].name, 'uc01-worker');
  assert.equal(steps[1].env.UC01_REAL_PROVIDER_LLM_BASE_URL, undefined);
});
