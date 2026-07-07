import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildDockerRunArgs,
  buildDocumentParseWorkerConfig,
} from './document-parse-worker-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

const containerName =
  process.env.DOCUMENT_PARSE_WORKER_CONTAINER ?? 'ir-document-parse-worker-smoke';

async function removeExistingContainer(name) {
  try {
    await execFileAsync('docker', ['rm', '-f', name]);
    return true;
  } catch (error) {
    const output = `${error.stdout ?? ''}${error.stderr ?? ''}`;
    if (output.includes('No such container')) {
      return false;
    }
    throw error;
  }
}

async function main() {
  const config = buildDocumentParseWorkerConfig({
    containerName,
    network: process.env.DOCUMENT_PARSE_WORKER_NETWORK,
    image: process.env.DOCUMENT_PARSE_WORKER_IMAGE,
    rocketmqEndpoint: process.env.DOCUMENT_PARSE_WORKER_ROCKETMQ_ENDPOINT,
    topic: process.env.DOCUMENT_PARSE_WORKER_TOPIC,
    consumerGroup: process.env.DOCUMENT_PARSE_WORKER_CONSUMER_GROUP,
    databaseUrl: process.env.DOCUMENT_PARSE_WORKER_DATABASE_URL,
    minioEndpoint: process.env.DOCUMENT_PARSE_WORKER_MINIO_ENDPOINT,
    minioRootUser: process.env.DOCUMENT_PARSE_WORKER_MINIO_ROOT_USER,
    minioRootPassword: process.env.DOCUMENT_PARSE_WORKER_MINIO_ROOT_PASSWORD,
    minioBucket: process.env.DOCUMENT_PARSE_WORKER_MINIO_BUCKET,
    opensearchUrl: process.env.DOCUMENT_PARSE_WORKER_OPENSEARCH_URL,
    milvusHost: process.env.DOCUMENT_PARSE_WORKER_MILVUS_HOST,
    milvusPort: process.env.DOCUMENT_PARSE_WORKER_MILVUS_PORT,
  });

  const removed = await removeExistingContainer(config.containerName);
  const { stdout } = await execFileAsync('docker', buildDockerRunArgs(config), {
    windowsHide: true,
  });

  console.log(
    JSON.stringify(
      {
        removedExistingContainer: removed,
        containerName: config.containerName,
        containerId: stdout.trim(),
        network: config.network,
        topic: config.env.ROCKETMQ_DOCUMENT_TOPIC,
        consumerGroup: config.env.ROCKETMQ_DOCUMENT_CONSUMER_GROUP,
        minioEndpoint: config.env.MINIO_ENDPOINT,
        opensearchUrl: config.env.OPENSEARCH_URL,
        milvusHost: config.env.MILVUS_HOST,
      },
      null,
      2,
    ),
  );
}

await main();
