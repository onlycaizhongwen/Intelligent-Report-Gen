import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildDockerInspectArgs,
  buildDockerLogsArgs,
  buildDockerRunArgs,
  buildDocumentParseWorkerConfig,
  parseContainerState,
  summarizeWorkerStartup,
} from './document-parse-worker-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

const containerName =
  process.env.DOCUMENT_PARSE_WORKER_CONTAINER ?? 'ir-document-parse-worker-smoke';
const stabilizationMs = Number.parseInt(
  process.env.DOCUMENT_PARSE_WORKER_STABILIZATION_MS ?? '1500',
  10,
);
const healthTimeoutMs = Number.parseInt(
  process.env.DOCUMENT_PARSE_WORKER_HEALTH_TIMEOUT_MS ?? '45000',
  10,
);

function wait(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

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

async function inspectContainerState(name) {
  const inspectResult = await execFileAsync(
    'docker',
    buildDockerInspectArgs(name),
    {
      windowsHide: true,
    },
  );
  return parseContainerState(inspectResult.stdout.trim());
}

async function waitForWorkerState(name) {
  const timeoutMs = Number.isFinite(healthTimeoutMs) && healthTimeoutMs >= 0
    ? healthTimeoutMs
    : 45000;
  const deadline = Date.now() + timeoutMs;
  let state;

  do {
    state = await inspectContainerState(name);
    if (!state.running) {
      return state;
    }
    if (!state.healthStatus || state.healthStatus === 'healthy') {
      return state;
    }
    if (state.healthStatus !== 'starting') {
      return state;
    }
    await wait(1000);
  } while (Date.now() < deadline);

  return state;
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
  const containerId = stdout.trim();
  await wait(Number.isFinite(stabilizationMs) && stabilizationMs >= 0 ? stabilizationMs : 1500);

  const state = await waitForWorkerState(config.containerName);
  let logs = '';

  if (!state.running || (state.healthStatus && state.healthStatus !== 'healthy')) {
    try {
      const logsResult = await execFileAsync(
        'docker',
        buildDockerLogsArgs(config.containerName),
        {
          windowsHide: true,
        },
      );
      logs = `${logsResult.stdout ?? ''}${logsResult.stderr ?? ''}`;
    } catch (error) {
      logs = `${error.stdout ?? ''}${error.stderr ?? ''}`;
    }
  }

  const summary = summarizeWorkerStartup({
    config,
    removedExistingContainer: removed,
    containerId,
    state,
    logs,
  });

  console.log(
    JSON.stringify(
      summary,
      null,
      2,
    ),
  );

  if (!summary.passed) {
    process.exitCode = 1;
  }
}

await main();
