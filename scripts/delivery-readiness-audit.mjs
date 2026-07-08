import { execFile } from 'node:child_process';
import os from 'node:os';
import { promisify } from 'node:util';

import {
  buildProductionReadinessActionPlan,
  buildDeliveryReadinessChecks,
  classifyDeliveryReadinessResult,
  formatDeliveryReadinessAuditOutput,
  renderProductionReadinessEnvTemplate,
  sanitizeCommandEnv,
  summarizeDeliveryReadiness,
  writeDeliveryReadinessReportFile,
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
  const startedAt = Date.now();

  try {
    console.error(`[readiness] start ${check.name}`);
    const { stdout, stderr } = await execFileAsync(check.command, check.args, {
      cwd: process.cwd(),
      env,
      maxBuffer: 25 * 1024 * 1024,
      timeout: check.timeoutMs,
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
      durationMs: Date.now() - startedAt,
    };
  } catch (error) {
    const timedOut = error?.killed === true && error?.signal === 'SIGTERM';
    const outcome = classifyDeliveryReadinessResult(check, {
      exitCode: error?.code ?? 1,
      stdout: error?.stdout ?? '',
      stderr: error?.stderr ?? '',
      timedOut,
      signal: error?.signal,
      timeoutMs: check.timeoutMs,
    });
    return {
      ...outcome,
      command: check.command,
      args: check.args,
      durationMs: Date.now() - startedAt,
    };
  } finally {
    console.error(`[readiness] end ${check.name} ${Date.now() - startedAt}ms`);
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
const outputPayload = {
  summary,
  actionPlan,
  checks: checks.map((check) => ({
    name: check.name,
    scope: check.scope,
    kind: check.kind,
    description: check.description,
    command: check.command,
    args: check.args,
    timeoutMs: check.timeoutMs,
    env: check.env,
    url: check.url,
    missingEnv: check.missingEnv,
  })),
  results,
};
const outputFormat = process.env.DELIVERY_READINESS_OUTPUT === 'markdown' ? 'markdown' : 'json';
const output = formatDeliveryReadinessAuditOutput({
  payload: outputPayload,
  outputFormat,
});

console.log(output);

await writeDeliveryReadinessReportFile({
  reportFile: process.env.DELIVERY_READINESS_REPORT_FILE,
  content: output,
});

await writeDeliveryReadinessReportFile({
  reportFile: process.env.DELIVERY_READINESS_ENV_TEMPLATE_FILE,
  content: renderProductionReadinessEnvTemplate({ actionPlan }),
});

if (!summary.localReady) {
  process.exit(1);
}

if (!summary.productionReady) {
  process.exit(2);
}
