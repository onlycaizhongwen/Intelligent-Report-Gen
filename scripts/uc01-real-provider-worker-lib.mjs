export function buildWorkerConfig({
  apiKey,
  containerName = 'ir-report-generation-worker-smoke',
  network = 'intelligent-report-infra_default',
  image = 'intelligent-report-system-python-ai-service',
  entrypoint = 'python',
  command = ['-m', 'app.report_generation.worker_main'],
  consumerGroup = 'python-ai-report-generation-smoke-real',
  javaServiceUrl = 'http://ir-java-smoke:8080',
  databaseUrl = 'postgresql://report:report123@postgres:5432/intelligent_report',
  opensearchUrl = 'http://ir-opensearch:9200',
  milvusHost = 'ir-milvus',
  milvusPort = '19530',
  rocketmqEndpoint = 'rocketmq-namesrv:9876',
  topic = 'report_generation_outline_confirmed',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
  llmProvider = 'openai-compatible',
  llmModel = 'qwen-plus',
  llmBaseUrl = 'https://dashscope.aliyuncs.com/compatible-mode/v1',
} = {}) {
  if (!apiKey) {
    throw new Error('UC01 real provider worker requires apiKey');
  }

  return {
    containerName,
    network,
    image,
    entrypoint,
    command,
    env: {
      REPORT_GENERATION_WORKER_PROVIDER: 'rocketmq',
      ROCKETMQ_ENDPOINT: rocketmqEndpoint,
      ROCKETMQ_REPORT_GENERATION_TOPIC: topic,
      ROCKETMQ_REPORT_GENERATION_CONSUMER_GROUP: consumerGroup,
      JWT_SECRET: jwtSecret,
      JAVA_SERVICE_URL: javaServiceUrl,
      DATABASE_URL: databaseUrl,
      MILVUS_HOST: milvusHost,
      MILVUS_PORT: milvusPort,
      OPENSEARCH_URL: opensearchUrl,
      LLM_PROVIDER: llmProvider,
      LLM_MODEL: llmModel,
      LLM_BASE_URL: llmBaseUrl,
      LLM_API_KEY: apiKey,
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
