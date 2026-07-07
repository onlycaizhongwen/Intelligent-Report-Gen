import { execFile } from 'node:child_process';
import os from 'node:os';
import path from 'node:path';
import { promisify } from 'node:util';

import { buildDeliverySmokeSteps } from './delivery-local-smoke-lib.mjs';

const execFileAsync = promisify(execFile);

async function runStep(step) {
  const command = step.command;
  const args = step.args;
  const mergedEnv = {
    ...process.env,
    ...step.env,
  };
  const workdir = step.workdir ? path.resolve(process.cwd(), step.workdir) : process.cwd();

  const { stdout, stderr } = await execFileAsync(command, args, {
    cwd: workdir,
    env: mergedEnv,
    windowsHide: os.platform() === 'win32',
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
  const steps = buildDeliverySmokeSteps({
    dashscopeApiKey:
      process.env.DELIVERY_SMOKE_DASHSCOPE_API_KEY ?? process.env.DASHSCOPE_API_KEY ?? '',
    realBackendApiBaseUrl:
      process.env.DELIVERY_SMOKE_REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1',
    realBackendOrigin:
      process.env.DELIVERY_SMOKE_REAL_BACKEND_ORIGIN ?? 'http://127.0.0.1:18082',
    gatewayBaseUrl:
      process.env.DELIVERY_SMOKE_GATEWAY_BASE_URL ?? 'http://127.0.0.1:18000',
    tlsGatewayBaseUrl:
      process.env.DELIVERY_SMOKE_TLS_GATEWAY_BASE_URL ?? 'https://127.0.0.1:18443',
    gatewayApiBaseUrl:
      process.env.DELIVERY_SMOKE_GATEWAY_API_BASE_URL ?? 'http://127.0.0.1:18000/api/v1',
    gatewayOrigin:
      process.env.DELIVERY_SMOKE_GATEWAY_ORIGIN ?? 'http://127.0.0.1:18000',
    postgresContainer:
      process.env.DELIVERY_SMOKE_POSTGRES_CONTAINER ?? 'ir-postgres',
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
