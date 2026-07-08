import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildDockerInspectArgs,
  buildDockerLogsArgs,
  buildDockerRunArgs,
  buildKnowledgeIndexWorkerSmokeConfigs,
  parseContainerState,
  summarizeKnowledgeIndexWorkers,
} from './knowledge-index-worker-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

const stabilizationMs = Number.parseInt(
  process.env.KNOWLEDGE_INDEX_WORKER_STABILIZATION_MS ?? '1500',
  10,
);
const healthTimeoutMs = Number.parseInt(
  process.env.KNOWLEDGE_INDEX_WORKER_HEALTH_TIMEOUT_MS ?? '45000',
  10,
);

function wait(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}

async function removeExistingContainer(name) {
  try {
    await execFileAsync('docker', ['rm', '-f', name], { windowsHide: true });
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
    { windowsHide: true },
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

async function readLogs(name) {
  try {
    const logsResult = await execFileAsync(
      'docker',
      buildDockerLogsArgs(name),
      { windowsHide: true },
    );
    return `${logsResult.stdout ?? ''}${logsResult.stderr ?? ''}`;
  } catch (error) {
    return `${error.stdout ?? ''}${error.stderr ?? ''}`;
  }
}

async function startWorker(config) {
  const removedExistingContainer = await removeExistingContainer(config.containerName);
  const { stdout } = await execFileAsync('docker', buildDockerRunArgs(config), {
    windowsHide: true,
  });
  const containerId = stdout.trim();
  await wait(Number.isFinite(stabilizationMs) && stabilizationMs >= 0 ? stabilizationMs : 1500);

  const state = await waitForWorkerState(config.containerName);
  const logs = !state.running || (state.healthStatus && state.healthStatus !== 'healthy')
    ? await readLogs(config.containerName)
    : '';

  return {
    config,
    removedExistingContainer,
    containerId,
    state,
    logs,
  };
}

async function main() {
  const configs = buildKnowledgeIndexWorkerSmokeConfigs({
    network: process.env.KNOWLEDGE_INDEX_WORKER_NETWORK,
    image: process.env.KNOWLEDGE_INDEX_WORKER_IMAGE,
    rocketmqEndpoint: process.env.KNOWLEDGE_INDEX_WORKER_ROCKETMQ_ENDPOINT,
    databaseUrl: process.env.KNOWLEDGE_INDEX_WORKER_DATABASE_URL,
    opensearchUrl: process.env.KNOWLEDGE_INDEX_WORKER_OPENSEARCH_URL,
    milvusHost: process.env.KNOWLEDGE_INDEX_WORKER_MILVUS_HOST,
    milvusPort: process.env.KNOWLEDGE_INDEX_WORKER_MILVUS_PORT,
  });

  const workers = [];
  for (const config of configs) {
    workers.push(await startWorker(config));
  }

  const summary = summarizeKnowledgeIndexWorkers({ workers });
  console.log(JSON.stringify(summary, null, 2));

  if (!summary.passed) {
    process.exitCode = 1;
  }
}

await main();
