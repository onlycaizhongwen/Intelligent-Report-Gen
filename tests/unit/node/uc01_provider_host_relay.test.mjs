import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildHostRelayConfig,
  buildHostRelayStartStep,
  isAllowedRelayPath,
  targetUrlForRelayRequest,
  validateHostRelayRuntime,
} from '../../../scripts/uc01-provider-host-relay-lib.mjs';

test('buildHostRelayConfig exposes a container-reachable OpenAI-compatible base URL', () => {
  const config = buildHostRelayConfig({
    port: '19091',
    targetOrigin: 'https://dashscope.aliyuncs.com',
  });

  assert.equal(config.listenHost, '0.0.0.0');
  assert.equal(config.port, '19091');
  assert.equal(config.healthUrl, 'http://127.0.0.1:19091/health');
  assert.equal(
    config.containerBaseUrl,
    'http://host.docker.internal:19091/compatible-mode/v1',
  );
  assert.equal(config.targetOrigin, 'https://dashscope.aliyuncs.com');
});

test('relay only allows the OpenAI-compatible provider path prefix and health endpoint', () => {
  assert.equal(isAllowedRelayPath('/health'), true);
  assert.equal(isAllowedRelayPath('/compatible-mode/v1/models'), true);
  assert.equal(isAllowedRelayPath('/compatible-mode/v1/chat/completions'), true);
  assert.equal(isAllowedRelayPath('/api/v1/internal'), false);
  assert.equal(isAllowedRelayPath('/compatible-mode/v2/models'), false);
});

test('targetUrlForRelayRequest preserves provider path and query on the target origin', () => {
  const targetUrl = targetUrlForRelayRequest(
    '/compatible-mode/v1/models?limit=1',
    'https://dashscope.aliyuncs.com',
  );

  assert.equal(
    targetUrl,
    'https://dashscope.aliyuncs.com/compatible-mode/v1/models?limit=1',
  );
});

test('buildHostRelayStartStep returns a bounded local smoke startup command', () => {
  const step = buildHostRelayStartStep({
    port: '19091',
    targetOrigin: 'https://dashscope.aliyuncs.com',
  });

  assert.equal(step.name, 'uc01-provider-host-relay');
  assert.equal(step.command, 'node');
  assert.deepEqual(step.args, ['scripts/uc01-provider-host-relay-start.mjs']);
  assert.equal(step.env.UC01_PROVIDER_RELAY_PORT, '19091');
  assert.equal(step.env.UC01_PROVIDER_RELAY_TARGET_ORIGIN, 'https://dashscope.aliyuncs.com');
});

test('validateHostRelayRuntime rejects production profiles by default', () => {
  assert.throws(
    () => validateHostRelayRuntime({ nodeEnv: 'production' }),
    /local smoke only/,
  );
  assert.throws(
    () => validateHostRelayRuntime({ springProfilesActive: 'prod,metrics' }),
    /local smoke only/,
  );
  assert.doesNotThrow(() => validateHostRelayRuntime({ nodeEnv: 'local-smoke' }));
});
