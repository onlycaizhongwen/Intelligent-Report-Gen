import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDockerRunArgs,
  buildDocumentParseWorkerConfig,
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
