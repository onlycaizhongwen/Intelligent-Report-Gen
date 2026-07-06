import { execFile } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';

import { buildP2SmokeSteps } from './p2-local-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

async function runStep(step) {
  const isWindowsNpm = step.command === 'npm' && os.platform() === 'win32';
  const command = isWindowsNpm ? 'cmd.exe' : step.command;
  const args = isWindowsNpm ? ['/d', '/s', '/c', `npm ${step.args.join(' ')}`] : step.args;
  const mergedEnv = {
    ...process.env,
    ...step.env,
  };
  const workdir = step.workdir ? path.resolve(process.cwd(), step.workdir) : process.cwd();

  const { stdout, stderr } = await execFileAsync(command, args, {
    cwd: workdir,
    env: mergedEnv,
    windowsHide: true,
  });

  return {
    name: step.name,
    command,
    args,
    workdir,
    stdout: stdout.trim(),
    stderr: stderr.trim(),
  };
}

async function main() {
  const steps = buildP2SmokeSteps({
    realBackendApiBaseUrl:
      process.env.P2_SMOKE_REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1',
    realBackendOrigin:
      process.env.P2_SMOKE_REAL_BACKEND_ORIGIN ?? 'http://127.0.0.1:18082',
  });

  const results = [];
  for (const step of steps) {
    const result = await runStep(step);
    results.push(result);
  }

  console.log(
    JSON.stringify(
      {
        completedSteps: results.map((result) => result.name),
        results,
        plannedSteps: steps.map((step) => ({
          name: step.name,
          command: step.command,
          args: step.args,
          workdir: step.workdir ?? '.',
        })),
      },
      null,
      2,
    ),
  );
}

await main();
