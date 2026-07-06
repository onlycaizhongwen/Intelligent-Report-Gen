import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildDockerRunArgs,
  buildWorkerConfig,
} from './uc01-real-provider-worker-lib.mjs';

const execFileAsync = promisify(execFile);

const apiKey =
  process.env.UC01_REAL_PROVIDER_API_KEY ?? process.env.DASHSCOPE_API_KEY ?? '';
const containerName =
  process.env.UC01_REAL_PROVIDER_CONTAINER ?? 'ir-report-generation-worker-smoke';

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
  const config = buildWorkerConfig({
    apiKey,
    containerName,
    network: process.env.UC01_REAL_PROVIDER_NETWORK,
    consumerGroup: process.env.UC01_REAL_PROVIDER_CONSUMER_GROUP,
    javaServiceUrl: process.env.UC01_REAL_PROVIDER_JAVA_SERVICE_URL,
    databaseUrl: process.env.UC01_REAL_PROVIDER_DATABASE_URL,
    opensearchUrl: process.env.UC01_REAL_PROVIDER_OPENSEARCH_URL,
    milvusHost: process.env.UC01_REAL_PROVIDER_MILVUS_HOST,
    milvusPort: process.env.UC01_REAL_PROVIDER_MILVUS_PORT,
    rocketmqEndpoint: process.env.UC01_REAL_PROVIDER_ROCKETMQ_ENDPOINT,
    topic: process.env.UC01_REAL_PROVIDER_TOPIC,
    jwtSecret: process.env.UC01_REAL_PROVIDER_JWT_SECRET,
    llmProvider: process.env.UC01_REAL_PROVIDER_LLM_PROVIDER,
    llmModel: process.env.UC01_REAL_PROVIDER_LLM_MODEL,
    llmBaseUrl: process.env.UC01_REAL_PROVIDER_LLM_BASE_URL,
  });

  const removed = await removeExistingContainer(config.containerName);
  const args = buildDockerRunArgs(config);
  const { stdout } = await execFileAsync('docker', args);

  console.log(
    JSON.stringify(
      {
        removedExistingContainer: removed,
        containerName: config.containerName,
        containerId: stdout.trim(),
        network: config.network,
        llmProvider: config.env.LLM_PROVIDER,
        llmModel: config.env.LLM_MODEL,
        llmBaseUrl: config.env.LLM_BASE_URL,
        consumerGroup: config.env.ROCKETMQ_REPORT_GENERATION_CONSUMER_GROUP,
      },
      null,
      2,
    ),
  );
}

await main();
