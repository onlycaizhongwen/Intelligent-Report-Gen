import { execFile } from 'node:child_process';
import os from 'node:os';
import { promisify } from 'node:util';

import {
  buildDockerInspectArgs,
  buildDockerLogsArgs,
  buildReportGenerationWorkerHealthConfig,
  parseContainerState,
  summarizeReportGenerationWorkerHealth,
} from './report-generation-worker-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

const config = buildReportGenerationWorkerHealthConfig({
  containerName: process.env.REPORT_GENERATION_WORKER_CONTAINER ?? 'ir-report-generation-worker-smoke',
});

async function inspectContainer(containerName) {
  const { stdout } = await execFileAsync('docker', buildDockerInspectArgs(containerName), {
    windowsHide: os.platform() === 'win32',
    timeout: 15_000,
  });
  return parseContainerState(stdout);
}

async function readLogs(containerName) {
  try {
    const { stdout, stderr } = await execFileAsync('docker', buildDockerLogsArgs(containerName), {
      windowsHide: os.platform() === 'win32',
      timeout: 15_000,
      maxBuffer: 1024 * 1024,
    });
    return `${stdout}${stderr}`;
  } catch (error) {
    return `${error.stdout ?? ''}${error.stderr ?? ''}`;
  }
}

let state = null;
try {
  state = await inspectContainer(config.containerName);
} catch {
  state = null;
}

const logs = !state || state.running !== true || state.healthStatus !== config.requiredHealthStatus
  ? await readLogs(config.containerName)
  : '';

const summary = summarizeReportGenerationWorkerHealth({ config, state, logs });
console.log(JSON.stringify(summary, null, 2));

if (!summary.passed) {
  process.exit(1);
}
