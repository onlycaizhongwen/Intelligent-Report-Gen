import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildAuthHeaders,
  buildConfirmOutlineRequest,
  buildCreateTaskRequest,
  buildModelInvocationSql,
  buildSmokeJwt,
  evaluateSmokeResult,
  extractReportSummary,
  parseModelInvocationLine,
} from './uc01-real-provider-smoke-lib.mjs';

const execFileAsync = promisify(execFile);
const baseUrl = process.env.UC01_SMOKE_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret =
  process.env.UC01_SMOKE_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';
const subject = process.env.UC01_SMOKE_SUB ?? '1';
const pollAttempts = Number(process.env.UC01_SMOKE_POLL_ATTEMPTS ?? '20');
const pollIntervalMs = Number(process.env.UC01_SMOKE_POLL_INTERVAL_MS ?? '1000');
const postgresContainer = process.env.UC01_SMOKE_POSTGRES_CONTAINER ?? '';
const postgresUser = process.env.UC01_SMOKE_POSTGRES_USER ?? 'report';
const postgresDatabase = process.env.UC01_SMOKE_POSTGRES_DB ?? 'intelligent_report';
const strictAudit = (process.env.UC01_SMOKE_STRICT_AUDIT ?? 'false').toLowerCase() === 'true';

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function call(path, options = {}) {
  const response = await fetch(`${baseUrl}/${path.replace(/^\//, '')}`, options);
  const text = await response.text();
  let body = null;
  if (text) {
    try {
      body = JSON.parse(text);
    } catch {
      body = text;
    }
  }
  return { ok: response.ok, status: response.status, text, body };
}

async function pollReport(headers, reportId) {
  let lastResponse = null;
  for (let attempt = 0; attempt < pollAttempts; attempt += 1) {
    lastResponse = await call(`reports/${reportId}`, { headers });
    if (
      lastResponse.ok &&
      ['completed', 'failed'].includes(lastResponse.body?.data?.status ?? '')
    ) {
      return lastResponse;
    }
    await sleep(pollIntervalMs);
  }
  return lastResponse;
}

async function queryModelInvocation(taskId) {
  if (!postgresContainer) {
    return null;
  }

  const sql = buildModelInvocationSql(taskId);
  const { stdout } = await execFileAsync('docker', [
    'exec',
    postgresContainer,
    'psql',
    '-U',
    postgresUser,
    '-d',
    postgresDatabase,
    '-t',
    '-A',
    '-F',
    '|',
    '-c',
    sql,
  ]);

  const line = stdout
    .split(/\r?\n/)
    .map((item) => item.trim())
    .find(Boolean);
  if (!line) {
    return null;
  }
  return parseModelInvocationLine(line);
}

async function main() {
  const token = buildSmokeJwt({
    secret: jwtSecret,
    payload: {
      sub: subject,
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read'],
      status: 'enabled',
    },
  });
  const headers = buildAuthHeaders(token);
  const topic = `UC01 real provider smoke ${Date.now()}`;

  const create = await call('reports/generation-tasks', {
    method: 'POST',
    headers,
    body: JSON.stringify(buildCreateTaskRequest(topic)),
  });
  if (!create.ok) {
    console.error('CREATE_FAILED', create.status, create.text);
    process.exitCode = 1;
    return;
  }

  const created = create.body?.data;
  const taskId = created?.taskId;
  const reportId = created?.reportId;
  if (!taskId || !reportId) {
    console.error('CREATE_RESPONSE_MISSING_IDS', create.text);
    process.exitCode = 1;
    return;
  }

  const outline = await call(`reports/generation-tasks/${taskId}/outline`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(buildConfirmOutlineRequest()),
  });
  if (!outline.ok) {
    console.error('OUTLINE_FAILED', outline.status, outline.text);
    process.exitCode = 1;
    return;
  }

  const report = await pollReport(headers, reportId);
  const reportSummary = report?.body ? extractReportSummary(report.body) : null;
  const modelInvocation = await queryModelInvocation(taskId);

  console.log(
    JSON.stringify(
      {
        baseUrl,
        taskId,
        reportId,
        strictAudit,
        reportSummary,
        modelInvocation,
      },
      null,
      2,
    ),
  );

  if (!report?.ok) {
    console.error('REPORT_POLL_FAILED', report?.status, report?.text);
    process.exitCode = 1;
    return;
  }

  const failures = evaluateSmokeResult({
    strictAudit,
    reportSummary,
    modelInvocation,
  });
  if (failures.length > 0) {
    console.error('SMOKE_VALIDATION_FAILED', JSON.stringify(failures), report?.text ?? '');
    process.exitCode = 1;
  }
}

await main();
