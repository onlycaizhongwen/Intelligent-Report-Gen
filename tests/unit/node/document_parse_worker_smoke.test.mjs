import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDockerInspectArgs,
  buildDockerLogsArgs,
  buildDockerRunArgs,
  buildDocumentParseWorkerConfig,
  parseContainerState,
  summarizeWorkerStartup,
} from '../../../scripts/document-parse-worker-smoke-lib.mjs';

test('buildDocumentParseWorkerConfig targets existing local Docker dependencies', () => {
  const config = buildDocumentParseWorkerConfig();

  assert.equal(config.containerName, 'ir-document-parse-worker-smoke');
  assert.equal(config.network, 'intelligent-report-infra_default');
  assert.equal(config.image, 'intelligent-report-system-python-ai-service');
  assert.equal(config.entrypoint, 'python');
  assert.deepEqual(config.command, ['-m', 'app.document_processing.worker_main']);
  assert.equal(config.env.DOCUMENT_PARSE_WORKER_PROVIDER, 'rocketmq');
  assert.equal(config.env.ROCKETMQ_ENDPOINT, 'rocketmq-namesrv:9876');
  assert.equal(config.env.ROCKETMQ_DOCUMENT_TOPIC, 'document_parse_requested');
  assert.equal(config.env.DATABASE_URL, 'postgresql://report:report123@postgres:5432/intelligent_report');
  assert.equal(config.env.MINIO_ENDPOINT, 'http://ir-minio:9000');
  assert.equal(config.env.OPENSEARCH_URL, 'http://ir-opensearch:9200');
  assert.equal(config.env.MILVUS_HOST, 'ir-milvus');
});

test('buildDockerRunArgs serializes document parse worker smoke container startup', () => {
  const args = buildDockerRunArgs(buildDocumentParseWorkerConfig());

  assert.deepEqual(args.slice(0, 7), [
    'run',
    '-d',
    '--name',
    'ir-document-parse-worker-smoke',
    '--network',
    'intelligent-report-infra_default',
    '--entrypoint',
  ]);
  assert.ok(args.includes('DOCUMENT_PARSE_WORKER_PROVIDER=rocketmq'));
  assert.ok(args.includes('MINIO_ENDPOINT=http://ir-minio:9000'));
  assert.ok(args.slice(-2).join(' ') === '-m app.document_processing.worker_main');
});

test('buildDockerInspectArgs targets the smoke worker container state', () => {
  assert.deepEqual(buildDockerInspectArgs('ir-document-parse-worker-smoke'), [
    'inspect',
    '--format',
    '{{json .State}}',
    'ir-document-parse-worker-smoke',
  ]);
});

test('buildDockerLogsArgs limits failure diagnostics without leaking full container history', () => {
  assert.deepEqual(buildDockerLogsArgs('ir-document-parse-worker-smoke'), [
    'logs',
    '--tail',
    '80',
    'ir-document-parse-worker-smoke',
  ]);
});

test('parseContainerState reads docker inspect state JSON', () => {
  assert.deepEqual(
    parseContainerState('{"Status":"exited","Running":false,"ExitCode":2,"Error":"boom","Health":{"Status":"unhealthy","FailingStreak":3}}'),
    {
      status: 'exited',
      running: false,
      exitCode: 2,
      error: 'boom',
      healthStatus: 'unhealthy',
      healthFailingStreak: 3,
    },
  );
});

test('summarizeWorkerStartup fails closed when worker exits after docker run', () => {
  const config = buildDocumentParseWorkerConfig();
  const summary = summarizeWorkerStartup({
    config,
    removedExistingContainer: true,
    containerId: 'abc123',
    state: {
      status: 'exited',
      running: false,
      exitCode: 1,
      error: '',
    },
    logs: 'MINIO_ROOT_PASSWORD=minioadmin123\nTraceback: connection refused',
  });

  assert.equal(summary.passed, false);
  assert.equal(summary.classification, 'document-parse-worker-not-running');
  assert.equal(summary.containerName, 'ir-document-parse-worker-smoke');
  assert.equal(summary.state.status, 'exited');
  assert.equal(summary.state.exitCode, 1);
  assert.equal(summary.logsTail.includes('connection refused'), true);
  assert.equal(summary.logsTail.includes('minioadmin123'), false);
  assert.equal(summary.logsTail.includes('<redacted>'), true);
});

test('summarizeWorkerStartup passes only when worker remains running', () => {
  const config = buildDocumentParseWorkerConfig();
  const summary = summarizeWorkerStartup({
    config,
    removedExistingContainer: false,
    containerId: 'abc123',
    state: {
      status: 'running',
      running: true,
      exitCode: 0,
      error: '',
      healthStatus: 'healthy',
      healthFailingStreak: 0,
    },
  });

  assert.equal(summary.passed, true);
  assert.equal(summary.classification, 'document-parse-worker-healthy');
  assert.equal(summary.opensearchUrl, 'http://ir-opensearch:9200');
  assert.equal(summary.milvusHost, 'ir-milvus');
});

test('summarizeWorkerStartup fails closed when worker healthcheck is unhealthy', () => {
  const config = buildDocumentParseWorkerConfig();
  const summary = summarizeWorkerStartup({
    config,
    removedExistingContainer: false,
    containerId: 'abc123',
    state: {
      status: 'running',
      running: true,
      exitCode: 0,
      error: '',
      healthStatus: 'unhealthy',
      healthFailingStreak: 4,
    },
    logs: 'health probe failed',
  });

  assert.equal(summary.passed, false);
  assert.equal(summary.classification, 'document-parse-worker-unhealthy');
  assert.equal(summary.state.healthStatus, 'unhealthy');
  assert.equal(summary.state.healthFailingStreak, 4);
  assert.equal(summary.logsTail, 'health probe failed');
});
