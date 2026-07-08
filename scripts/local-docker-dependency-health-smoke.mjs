import { execFile } from 'node:child_process';
import os from 'node:os';
import { promisify } from 'node:util';

import {
  buildDockerInspectArgs,
  buildLocalDockerDependencyHealthPlan,
  parseDockerInspectState,
  summarizeLocalDockerDependencyHealth,
} from './local-docker-dependency-health-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

async function inspectContainer(containerName) {
  const { stdout } = await execFileAsync('docker', buildDockerInspectArgs(containerName), {
    windowsHide: os.platform() === 'win32',
    timeout: 15_000,
  });
  return parseDockerInspectState(stdout);
}

const plan = buildLocalDockerDependencyHealthPlan();
const statesByContainer = new Map();

for (const dependency of plan) {
  try {
    statesByContainer.set(dependency.containerName, await inspectContainer(dependency.containerName));
  } catch {
    statesByContainer.set(dependency.containerName, null);
  }
}

const summary = summarizeLocalDockerDependencyHealth({ plan, statesByContainer });
console.log(JSON.stringify(summary, null, 2));

if (!summary.passed) {
  process.exit(1);
}
