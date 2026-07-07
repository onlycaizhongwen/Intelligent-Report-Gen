import { execFile } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';

import { buildP0SmokeSteps } from './p0-local-smoke-lib.mjs';
import { collectProxyEnv } from './uc01-real-provider-worker-lib.mjs';

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
  const steps = buildP0SmokeSteps({
    dashscopeApiKey:
      process.env.P0_SMOKE_DASHSCOPE_API_KEY ?? process.env.DASHSCOPE_API_KEY ?? '',
    realBackendApiBaseUrl:
      process.env.P0_SMOKE_REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1',
    realBackendOrigin:
      process.env.P0_SMOKE_REAL_BACKEND_ORIGIN ?? 'http://127.0.0.1:18082',
    gatewayApiBaseUrl:
      process.env.P0_SMOKE_GATEWAY_API_BASE_URL ?? 'http://127.0.0.1:18000/api/v1',
    gatewayOrigin:
      process.env.P0_SMOKE_GATEWAY_ORIGIN ?? 'http://127.0.0.1:18000',
    postgresContainer: process.env.P0_SMOKE_POSTGRES_CONTAINER ?? 'ir-postgres',
    providerRelayMode: process.env.P0_SMOKE_PROVIDER_RELAY ?? 'host',
    providerRelayPort: process.env.P0_SMOKE_PROVIDER_RELAY_PORT ?? '18091',
    providerRelayTargetOrigin:
      process.env.P0_SMOKE_PROVIDER_RELAY_TARGET_ORIGIN ?? 'https://dashscope.aliyuncs.com',
    proxyEnv: collectProxyEnv(process.env),
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
