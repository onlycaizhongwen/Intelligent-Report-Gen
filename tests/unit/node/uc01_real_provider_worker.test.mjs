import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildWorkerConfig,
  buildDockerRunArgs,
  buildProviderPreflightConfig,
} from '../../../scripts/uc01-real-provider-worker-lib.mjs';

test('buildWorkerConfig produces the validated UC-01 real-provider worker config', () => {
  const config = buildWorkerConfig({
    apiKey: 'test-key',
  });

  assert.equal(config.containerName, 'ir-report-generation-worker-smoke');
  assert.equal(config.network, 'intelligent-report-infra_default');
  assert.equal(config.image, 'intelligent-report-system-python-ai-service');
  assert.equal(config.entrypoint, 'python');
  assert.equal(config.command.join(' '), '-m app.report_generation.worker_main');
  assert.equal(config.env.LLM_PROVIDER, 'openai-compatible');
  assert.equal(config.env.LLM_MODEL, 'qwen-plus');
  assert.equal(
    config.env.LLM_BASE_URL,
    'https://dashscope.aliyuncs.com/compatible-mode/v1',
  );
  assert.equal(config.env.LLM_API_KEY, 'test-key');
  assert.equal(
    config.env.ROCKETMQ_REPORT_GENERATION_CONSUMER_GROUP,
    'python-ai-report-generation-smoke-real',
  );
});

test('buildWorkerConfig allows overriding local runtime coordinates without changing core provider defaults', () => {
  const config = buildWorkerConfig({
    apiKey: 'test-key',
    javaServiceUrl: 'http://custom-java:8080',
    databaseUrl: 'postgresql://demo:demo@custom-postgres:5432/demo',
    opensearchUrl: 'http://custom-opensearch:9200',
    milvusHost: 'custom-milvus',
    network: 'custom-net',
  });

  assert.equal(config.network, 'custom-net');
  assert.equal(config.env.JAVA_SERVICE_URL, 'http://custom-java:8080');
  assert.equal(
    config.env.DATABASE_URL,
    'postgresql://demo:demo@custom-postgres:5432/demo',
  );
  assert.equal(config.env.OPENSEARCH_URL, 'http://custom-opensearch:9200');
  assert.equal(config.env.MILVUS_HOST, 'custom-milvus');
  assert.equal(config.env.LLM_PROVIDER, 'openai-compatible');
});

test('buildWorkerConfig forwards optional host proxy settings into the worker container', () => {
  const config = buildWorkerConfig({
    apiKey: 'test-key',
    proxyEnv: {
      HTTPS_PROXY: 'http://127.0.0.1:7890',
      HTTP_PROXY: '',
      NO_PROXY: 'localhost,127.0.0.1,.local',
    },
  });

  assert.equal(config.env.HTTPS_PROXY, 'http://127.0.0.1:7890');
  assert.equal(config.env.NO_PROXY, 'localhost,127.0.0.1,.local');
  assert.equal(Object.hasOwn(config.env, 'HTTP_PROXY'), false);
});

test('buildDockerRunArgs serializes the config into a docker run command shape', () => {
  const args = buildDockerRunArgs(
    buildWorkerConfig({
      apiKey: 'test-key',
    }),
  );

  assert.deepEqual(args.slice(0, 7), [
    'run',
    '-d',
    '--name',
    'ir-report-generation-worker-smoke',
    '--network',
    'intelligent-report-infra_default',
    '--entrypoint',
  ]);
  assert.ok(args.includes('python'));
  assert.ok(args.includes('intelligent-report-system-python-ai-service'));
  assert.ok(args.includes('LLM_PROVIDER=openai-compatible'));
  assert.ok(args.includes('LLM_MODEL=qwen-plus'));
  assert.ok(args.includes('LLM_API_KEY=test-key'));
  assert.ok(args.slice(-2).join(' ') === '-m app.report_generation.worker_main');
});

test('buildDockerRunArgs can reference sensitive env vars without serializing secret values', () => {
  const args = buildDockerRunArgs(
    buildWorkerConfig({
      apiKey: 'secret-key',
    }),
    {
      referenceEnvKeys: ['LLM_API_KEY'],
    },
  );

  assert.ok(args.includes('LLM_API_KEY'));
  assert.equal(args.includes('LLM_API_KEY=secret-key'), false);
});

test('buildProviderPreflightConfig probes the provider from the same Docker network and image', () => {
  const config = buildProviderPreflightConfig({
    apiKey: 'test-key',
    network: 'custom-net',
    proxyEnv: {
      HTTPS_PROXY: 'http://host.docker.internal:7890',
    },
  });
  const args = buildDockerRunArgs(config);

  assert.equal(config.containerName, 'ir-uc01-provider-preflight');
  assert.equal(config.network, 'custom-net');
  assert.equal(config.env.LLM_API_KEY, 'test-key');
  assert.equal(config.env.HTTPS_PROXY, 'http://host.docker.internal:7890');
  assert.ok(args.includes('--rm'));
  assert.ok(args.some((arg) => arg.includes('UC01 provider preflight')));
  assert.ok(args.slice(-2).join(' ').includes('urllib.request'));
});
