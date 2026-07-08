export function buildDocumentParseWorkerConfig({
  containerName = 'ir-document-parse-worker-smoke',
  network = 'intelligent-report-infra_default',
  image = 'intelligent-report-system-python-ai-service',
  entrypoint = 'python',
  command = ['-m', 'app.document_processing.worker_main'],
  rocketmqEndpoint = 'rocketmq-namesrv:9876',
  topic = 'document_parse_requested',
  consumerGroup = 'python-ai-document-parse-smoke',
  databaseUrl = 'postgresql://report:report123@postgres:5432/intelligent_report',
  minioEndpoint = 'http://ir-minio:9000',
  minioRootUser = 'minioadmin',
  minioRootPassword = 'minioadmin123',
  minioBucket = 'report-artifacts',
  opensearchUrl = 'http://ir-opensearch:9200',
  milvusHost = 'ir-milvus',
  milvusPort = '19530',
} = {}) {
  return {
    containerName,
    network,
    image,
    entrypoint,
    command,
    env: {
      DOCUMENT_PARSE_WORKER_PROVIDER: 'rocketmq',
      ROCKETMQ_ENDPOINT: rocketmqEndpoint,
      ROCKETMQ_DOCUMENT_TOPIC: topic,
      ROCKETMQ_DOCUMENT_CONSUMER_GROUP: consumerGroup,
      DATABASE_URL: databaseUrl,
      MILVUS_HOST: milvusHost,
      MILVUS_PORT: milvusPort,
      OPENSEARCH_URL: opensearchUrl,
      MINIO_ENDPOINT: minioEndpoint,
      MINIO_ROOT_USER: minioRootUser,
      MINIO_ROOT_PASSWORD: minioRootPassword,
      MINIO_BUCKET: minioBucket,
    },
  };
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
  };
}

function redactSensitiveText(value, config) {
  let redacted = String(value ?? '');
  const sensitiveValues = [
    config.env.MINIO_ROOT_PASSWORD,
    config.env.DATABASE_URL,
  ].filter(Boolean);

  for (const sensitiveValue of sensitiveValues) {
    redacted = redacted.split(sensitiveValue).join('<redacted>');
  }

  redacted = redacted.replace(
    /(PASSWORD|SECRET|TOKEN|KEY|DATABASE_URL)=([^\s]+)/gi,
    '$1=<redacted>',
  );
  return redacted;
}

export function summarizeWorkerStartup({
  config,
  removedExistingContainer,
  containerId,
  state,
  logs = '',
}) {
  const passed = state.running === true;
  return {
    passed,
    classification: passed
      ? 'document-parse-worker-running'
      : 'document-parse-worker-not-running',
    removedExistingContainer,
    containerName: config.containerName,
    containerId,
    network: config.network,
    topic: config.env.ROCKETMQ_DOCUMENT_TOPIC,
    consumerGroup: config.env.ROCKETMQ_DOCUMENT_CONSUMER_GROUP,
    minioEndpoint: config.env.MINIO_ENDPOINT,
    opensearchUrl: config.env.OPENSEARCH_URL,
    milvusHost: config.env.MILVUS_HOST,
    state,
    ...(logs ? { logsTail: redactSensitiveText(logs, config) } : {}),
  };
}
