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
