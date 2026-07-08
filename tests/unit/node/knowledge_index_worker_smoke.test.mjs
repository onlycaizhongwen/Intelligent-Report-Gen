import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDockerInspectArgs,
  buildDockerLogsArgs,
  buildDockerRunArgs,
  buildKnowledgeIndexWorkerSmokeConfigs,
  parseContainerState,
  summarizeKnowledgeIndexWorkers,
} from '../../../scripts/knowledge-index-worker-smoke-lib.mjs';

test('buildKnowledgeIndexWorkerSmokeConfigs targets UC-05 local Docker worker containers', () => {
  const configs = buildKnowledgeIndexWorkerSmokeConfigs();

  assert.deepEqual(configs.map((config) => config.containerName), [
    'ir-knowledge-index-cleanup-worker-smoke',
    'ir-knowledge-item-index-worker-smoke',
  ]);
  assert.deepEqual(configs.map((config) => config.role), [
    'knowledge-index-cleanup',
    'knowledge-item-index',
  ]);
  assert.deepEqual(configs.map((config) => config.command.join(' ')), [
    '-m app.document_processing.cleanup_worker_main',
    '-m app.document_processing.item_index_worker_main',
  ]);
  assert.equal(configs[0].network, 'intelligent-report-infra_default');
  assert.equal(configs[0].image, 'intelligent-report-system-python-ai-service');
  assert.equal(configs[0].env.ROCKETMQ_ENDPOINT, 'rocketmq-namesrv:9876');
  assert.equal(configs[0].env.DATABASE_URL, 'postgresql://report:report123@postgres:5432/intelligent_report');
  assert.equal(configs[0].env.OPENSEARCH_URL, 'http://ir-opensearch:9200');
  assert.equal(configs[0].env.MILVUS_HOST, 'ir-milvus');
  assert.equal(configs[1].env.ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC, 'knowledge_item_index_requested');
});

test('buildDockerRunArgs serializes bounded smoke worker startup', () => {
  const [cleanupConfig] = buildKnowledgeIndexWorkerSmokeConfigs();
  const args = buildDockerRunArgs(cleanupConfig);

  assert.deepEqual(args.slice(0, 7), [
    'run',
    '-d',
    '--name',
    'ir-knowledge-index-cleanup-worker-smoke',
    '--network',
    'intelligent-report-infra_default',
    '--entrypoint',
  ]);
  assert.ok(args.includes('KNOWLEDGE_CLEANUP_WORKER_PROVIDER=rocketmq'));
  assert.ok(args.includes('OPENSEARCH_URL=http://ir-opensearch:9200'));
  assert.ok(args.slice(-2).join(' ') === '-m app.document_processing.cleanup_worker_main');
});

test('buildDockerInspectArgs and buildDockerLogsArgs inspect bounded diagnostics', () => {
  assert.deepEqual(buildDockerInspectArgs('ir-knowledge-item-index-worker-smoke'), [
    'inspect',
    '--format',
    '{{json .State}}',
    'ir-knowledge-item-index-worker-smoke',
  ]);
  assert.deepEqual(buildDockerLogsArgs('ir-knowledge-item-index-worker-smoke'), [
    'logs',
    '--tail',
    '80',
    'ir-knowledge-item-index-worker-smoke',
  ]);
});

test('parseContainerState extracts Docker runtime state', () => {
  assert.deepEqual(
    parseContainerState('{"Status":"running","Running":true,"ExitCode":0,"Error":"","Health":{"Status":"healthy","FailingStreak":0}}'),
    {
      status: 'running',
      running: true,
      exitCode: 0,
      error: '',
      healthStatus: 'healthy',
      healthFailingStreak: 0,
    },
  );
});

test('summarizeKnowledgeIndexWorkers passes only when both UC-05 workers remain running', () => {
  const [cleanupConfig, itemIndexConfig] = buildKnowledgeIndexWorkerSmokeConfigs();
  const summary = summarizeKnowledgeIndexWorkers({
    workers: [
      {
        config: cleanupConfig,
        removedExistingContainer: false,
        containerId: 'cleanup123',
        state: { status: 'running', running: true, exitCode: 0, error: '' },
      },
      {
        config: itemIndexConfig,
        removedExistingContainer: true,
        containerId: 'index123',
        state: { status: 'running', running: true, exitCode: 0, error: '', healthStatus: 'healthy' },
      },
    ],
  });

  assert.equal(summary.passed, true);
  assert.equal(summary.classification, 'knowledge-index-workers-running');
  assert.deepEqual(summary.workerCount, 2);
  assert.deepEqual(summary.workers.map((worker) => worker.role), [
    'knowledge-index-cleanup',
    'knowledge-item-index',
  ]);
});

test('summarizeKnowledgeIndexWorkers fails closed and redacts logs when a worker exits', () => {
  const [cleanupConfig, itemIndexConfig] = buildKnowledgeIndexWorkerSmokeConfigs();
  const summary = summarizeKnowledgeIndexWorkers({
    workers: [
      {
        config: cleanupConfig,
        removedExistingContainer: false,
        containerId: 'cleanup123',
        state: { status: 'running', running: true, exitCode: 0, error: '' },
      },
      {
        config: itemIndexConfig,
        removedExistingContainer: false,
        containerId: 'index123',
        state: { status: 'exited', running: false, exitCode: 1, error: 'boom' },
        logs: `DATABASE_URL=postgresql://report:report123@postgres:5432/intelligent_report\nLLM_API_KEY=secret-token\nTraceback`,
      },
    ],
  });

  assert.equal(summary.passed, false);
  assert.equal(summary.classification, 'knowledge-index-workers-not-running');
  assert.equal(summary.workers[1].classification, 'knowledge-item-index-not-running');
  assert.equal(summary.workers[1].logsTail.includes('report123'), false);
  assert.equal(summary.workers[1].logsTail.includes('secret-token'), false);
  assert.match(summary.workers[1].logsTail, /DATABASE_URL=<redacted>/);
});
