import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

import {
  buildAuthHeaders,
  buildConfirmOutlineRequest,
  buildModelInvocationSql,
  buildSmokeJwt,
  buildTemplateCompletionPayload,
  buildTemplateTaskRequest,
  evaluateTemplateCompletionSmoke,
  extractReportSummary,
} from './uc02-template-completion-smoke-lib.mjs';

const execFileAsync = promisify(execFile);
const baseUrl = process.env.UC02_TEMPLATE_COMPLETION_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret =
  process.env.UC02_TEMPLATE_COMPLETION_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';
const subject = process.env.UC02_TEMPLATE_COMPLETION_SUB ?? '1';
const templateId = process.env.UC02_TEMPLATE_COMPLETION_TEMPLATE_ID ?? 'enterprise-quarterly';
const postgresContainer = process.env.UC02_TEMPLATE_COMPLETION_POSTGRES_CONTAINER ?? 'ir-postgres';
const postgresUser = process.env.UC02_TEMPLATE_COMPLETION_POSTGRES_USER ?? 'report';
const postgresDatabase = process.env.UC02_TEMPLATE_COMPLETION_POSTGRES_DB ?? 'intelligent_report';

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

async function queryModelInvocation(taskId) {
  if (!postgresContainer) {
    return null;
  }

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
    buildModelInvocationSql(taskId),
  ]);

  const line = stdout
    .split(/\r?\n/)
    .map((item) => item.trim())
    .find(Boolean);
  if (!line) {
    return null;
  }
  const [taskIdValue, provider, modelName, status, totalTokens, traceId] = line.split('|');
  return {
    taskId: Number(taskIdValue),
    provider,
    modelName,
    status,
    totalTokens: Number(totalTokens),
    traceId,
  };
}

function selectTemplate(templates) {
  return templates.find((item) => item.templateId === templateId) ?? templates[0];
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

  const templatesResponse = await call('report-templates', { headers });
  if (!templatesResponse.ok) {
    console.error('TEMPLATES_FAILED', templatesResponse.status, templatesResponse.text);
    process.exitCode = 1;
    return;
  }
  const template = selectTemplate(templatesResponse.body?.data ?? []);
  if (!template?.templateId) {
    console.error('TEMPLATE_MISSING', templatesResponse.text);
    process.exitCode = 1;
    return;
  }

  const createResponse = await call('reports/template-generation-tasks', {
    method: 'POST',
    headers,
    body: JSON.stringify(buildTemplateTaskRequest(template)),
  });
  if (!createResponse.ok) {
    console.error('CREATE_TEMPLATE_TASK_FAILED', createResponse.status, createResponse.text);
    process.exitCode = 1;
    return;
  }

  const taskId = createResponse.body?.data?.taskId;
  const reportId = createResponse.body?.data?.reportId;
  if (!taskId || !reportId) {
    console.error('CREATE_TEMPLATE_TASK_MISSING_IDS', createResponse.text);
    process.exitCode = 1;
    return;
  }

  const outlineResponse = await call(`reports/generation-tasks/${taskId}/outline`, {
    method: 'PUT',
    headers,
    body: JSON.stringify(buildConfirmOutlineRequest()),
  });
  if (!outlineResponse.ok) {
    console.error('CONFIRM_OUTLINE_FAILED', outlineResponse.status, outlineResponse.text);
    process.exitCode = 1;
    return;
  }

  const completionPayload = buildTemplateCompletionPayload({
    traceId: `trace-uc02-template-completion-${taskId}`,
    templateId: template.templateId,
  });
  const completionResponse = await call(`reports/generation-tasks/${taskId}/completion`, {
    method: 'POST',
    headers,
    body: JSON.stringify(completionPayload),
  });
  if (!completionResponse.ok) {
    console.error('COMPLETION_FAILED', completionResponse.status, completionResponse.text);
    process.exitCode = 1;
    return;
  }

  const reportResponse = await call(`reports/${reportId}`, { headers });
  if (!reportResponse.ok) {
    console.error('REPORT_DETAIL_FAILED', reportResponse.status, reportResponse.text);
    process.exitCode = 1;
    return;
  }

  const reportSummary = extractReportSummary(reportResponse.body);
  const modelInvocation = await queryModelInvocation(taskId);
  const failures = evaluateTemplateCompletionSmoke({ reportSummary, modelInvocation });

  console.log(
    JSON.stringify(
      {
        baseUrl,
        templateId: template.templateId,
        taskId,
        reportId,
        reportSummary,
        modelInvocation,
        failures,
      },
      null,
      2,
    ),
  );

  if (failures.length > 0) {
    console.error('UC02_TEMPLATE_COMPLETION_VALIDATION_FAILED', JSON.stringify(failures));
    process.exitCode = 1;
  }
}

await main();
