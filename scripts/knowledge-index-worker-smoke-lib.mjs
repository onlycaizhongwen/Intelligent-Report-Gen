export function buildKnowledgeIndexWorkerSmokeConfigs({
  network = 'intelligent-report-infra_default',
  image = 'intelligent-report-system-python-ai-service',
  entrypoint = 'python',
  rocketmqEndpoint = 'rocketmq-namesrv:9876',
  databaseUrl = 'postgresql://report:report123@postgres:5432/intelligent_report',
  opensearchUrl = 'http://ir-opensearch:9200',
  milvusHost = 'ir-milvus',
  milvusPort = '19530',
} = {}) {
  return [
    {
      role: 'knowledge-index-cleanup',
      containerName: 'ir-knowledge-index-cleanup-worker-smoke',
      network,
      image,
      entrypoint,
      command: ['-m', 'app.document_processing.cleanup_worker_main'],
      env: {
        KNOWLEDGE_CLEANUP_WORKER_PROVIDER: 'rocketmq',
        ROCKETMQ_ENDPOINT: rocketmqEndpoint,
        ROCKETMQ_KNOWLEDGE_ITEM_DELETED_TOPIC: 'knowledge_item_deleted',
        ROCKETMQ_KNOWLEDGE_CLEANUP_CONSUMER_GROUP: 'python-ai-knowledge-cleanup-smoke',
        DATABASE_URL: databaseUrl,
        MILVUS_HOST: milvusHost,
        MILVUS_PORT: milvusPort,
        OPENSEARCH_URL: opensearchUrl,
      },
    },
    {
      role: 'knowledge-item-index',
      containerName: 'ir-knowledge-item-index-worker-smoke',
      network,
      image,
      entrypoint,
      command: ['-m', 'app.document_processing.item_index_worker_main'],
      env: {
        KNOWLEDGE_INDEX_WORKER_PROVIDER: 'rocketmq',
        ROCKETMQ_ENDPOINT: rocketmqEndpoint,
        ROCKETMQ_KNOWLEDGE_ITEM_INDEX_REQUESTED_TOPIC: 'knowledge_item_index_requested',
        ROCKETMQ_KNOWLEDGE_INDEX_CONSUMER_GROUP: 'python-ai-knowledge-index-smoke',
        DATABASE_URL: databaseUrl,
        MILVUS_HOST: milvusHost,
        MILVUS_PORT: milvusPort,
        OPENSEARCH_URL: opensearchUrl,
      },
    },
  ];
}

export function buildDockerRunArgs(config) {
  const args = [
    'run',
    '-d',
    '--name',
    config.containerName,
    '--network',
    config.network,
    '--entrypoint',
    config.entrypoint,
  ];

  for (const [key, value] of Object.entries(config.env)) {
    args.push('-e', `${key}=${value}`);
  }

  args.push(config.image, ...config.command);
  return args;
}

export function buildDockerInspectArgs(containerName) {
  return ['inspect', '--format', '{{json .State}}', containerName];
}

export function buildDockerLogsArgs(containerName, tail = 80) {
  return ['logs', '--tail', String(tail), containerName];
}

export function parseContainerState(rawState) {
  const parsed = JSON.parse(rawState);
  return {
    status: parsed.Status ?? 'unknown',
    running: parsed.Running === true,
    exitCode: Number.isInteger(parsed.ExitCode) ? parsed.ExitCode : null,
    error: parsed.Error ?? '',
    ...(parsed.Health
      ? {
          healthStatus: parsed.Health.Status ?? 'unknown',
          healthFailingStreak: Number.isInteger(parsed.Health.FailingStreak)
            ? parsed.Health.FailingStreak
            : null,
        }
      : {}),
  };
}

function redactSensitiveText(value) {
  return String(value ?? '')
    .replace(
      /(PASSWORD|SECRET|TOKEN|KEY|DATABASE_URL|LLM_API_KEY)=([^\s]+)/gi,
      '$1=<redacted>',
    )
    .replace(/postgresql:\/\/[^\s]+/gi, 'postgresql://<redacted>');
}

function summarizeWorker({ config, removedExistingContainer, containerId, state, logs = '' }) {
  const running = state?.running === true;
  const healthy = !state?.healthStatus || state.healthStatus === 'healthy';
  const passed = running && healthy;
  const classification = (() => {
    if (!running) {
      return `${config.role}-not-running`;
    }
    if (!healthy) {
      return `${config.role}-unhealthy`;
    }
    return state.healthStatus === 'healthy'
      ? `${config.role}-healthy`
      : `${config.role}-running`;
  })();

  return {
    role: config.role,
    passed,
    classification,
    removedExistingContainer,
    containerName: config.containerName,
    containerId,
    network: config.network,
    opensearchUrl: config.env.OPENSEARCH_URL,
    milvusHost: config.env.MILVUS_HOST,
    state,
    ...(logs && !passed ? { logsTail: redactSensitiveText(logs) } : {}),
  };
}

export function summarizeKnowledgeIndexWorkers({ workers = [] } = {}) {
  const workerSummaries = workers.map(summarizeWorker);
  const passed = workerSummaries.length === 2
    && workerSummaries.every((worker) => worker.passed);
  const anyUnhealthy = workerSummaries.some((worker) => worker.classification.endsWith('-unhealthy'));
  const anyNotRunning = workerSummaries.some((worker) => worker.classification.endsWith('-not-running'));

  const classification = (() => {
    if (passed) {
      return 'knowledge-index-workers-running';
    }
    if (anyNotRunning) {
      return 'knowledge-index-workers-not-running';
    }
    if (anyUnhealthy) {
      return 'knowledge-index-workers-unhealthy';
    }
    return 'knowledge-index-workers-incomplete';
  })();

  return {
    passed,
    classification,
    workerCount: workerSummaries.length,
    workers: workerSummaries,
  };
}
