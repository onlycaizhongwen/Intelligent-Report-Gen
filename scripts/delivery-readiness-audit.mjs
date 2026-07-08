import { execFile } from 'node:child_process';
import os from 'node:os';
import { promisify } from 'node:util';

import {
  buildProductionReadinessActionPlan,
  buildDeliveryReadinessChecks,
  classifyDeliveryReadinessResult,
  sanitizeCommandEnv,
  summarizeDeliveryReadiness,
} from './delivery-readiness-audit-lib.mjs';

const execFileAsync = promisify(execFile);

async function runHttpCheck(check) {
  try {
    const response = await fetch(check.url, {
      signal: AbortSignal.timeout(10_000),
    });

    return {
      name: check.name,
      scope: check.scope,
      status: response.ok ? 'passed' : 'failed',
      required: check.required === true,
      evidence: {
        url: check.url,
        statusCode: response.status,
        statusText: response.statusText,
      },
    };
  } catch (error) {
    return {
      name: check.name,
      scope: check.scope,
      status: 'failed',
      required: check.required === true,
      evidence: {
        url: check.url,
        error: error instanceof Error ? error.message : String(error),
      },
    };
  }
}

async function runCommandCheck(check) {
  const env = {
    ...process.env,
    ...sanitizeCommandEnv(check.env),
  };

  try {
    const { stdout, stderr } = await execFileAsync(check.command, check.args, {
      cwd: process.cwd(),
      env,
      maxBuffer: 25 * 1024 * 1024,
      windowsHide: os.platform() === 'win32',
    });

    const outcome = classifyDeliveryReadinessResult(check, {
      exitCode: 0,
      stdout,
      stderr,
    });
    return {
      ...outcome,
      command: check.command,
      args: check.args,
    };
  } catch (error) {
    const outcome = classifyDeliveryReadinessResult(check, {
      exitCode: error?.code ?? 1,
      stdout: error?.stdout ?? '',
      stderr: error?.stderr ?? '',
    });
    return {
      ...outcome,
      command: check.command,
      args: check.args,
    };
  }
}

async function runCheck(check) {
  if (check.kind === 'blocked') {
    return {
      name: check.name,
      scope: check.scope,
      status: 'blocked',
      required: check.required === true,
      evidence: {
        description: check.description,
        missingEnv: check.missingEnv,
      },
    };
  }

  if (check.kind === 'http') {
    return runHttpCheck(check);
  }

  return runCommandCheck(check);
}

const checks = buildDeliveryReadinessChecks();
const results = [];

for (const check of checks) {
  results.push(await runCheck(check));
}

const summary = summarizeDeliveryReadiness(results);
const actionPlan = buildProductionReadinessActionPlan({ checks, results });

console.log(
  JSON.stringify(
    {
      summary,
      actionPlan,
      checks: checks.map((check) => ({
        name: check.name,
        scope: check.scope,
        kind: check.kind,
        description: check.description,
        command: check.command,
        args: check.args,
        env: check.env,
        url: check.url,
        missingEnv: check.missingEnv,
      })),
      results,
    },
    null,
    2,
  ),
);

if (!summary.localReady) {
  process.exit(1);
}

if (!summary.productionReady) {
  process.exit(2);
}
