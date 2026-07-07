import crypto from 'node:crypto';

function base64urlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

export function buildSmokeJwt({ secret, payload, expiresInSeconds = 7200 }) {
  const now = Math.floor(Date.now() / 1000);
  const header = base64urlJson({ alg: 'HS256', typ: 'JWT' });
  const body = base64urlJson({
    iat: now,
    exp: now + expiresInSeconds,
    ...payload,
  });
  const unsigned = `${header}.${body}`;
  const signature = crypto
    .createHmac('sha256', secret)
    .update(unsigned)
    .digest('base64url');
  return `${unsigned}.${signature}`;
}

export function buildCreateTaskRequest(topic) {
  return {
    topic,
    payload: {
      acceptance: 'UC-01-real-provider',
    },
  };
}

export function buildConfirmOutlineRequest() {
  return {
    outline: ['执行摘要', '关键发现'],
    confirmed: true,
  };
}

export function buildAuthHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function extractReportSummary(responseBody) {
  const report = responseBody?.data ?? {};
  const sections = Array.isArray(report.sections) ? report.sections : [];
  const citationCount = sections.reduce((total, section) => {
    const citations = Array.isArray(section.citations) ? section.citations.length : 0;
    return total + citations;
  }, 0);

  return {
    reportId: report.reportId ?? null,
    status: report.status ?? null,
    currentVersionId: report.currentVersionId ?? null,
    sectionCount: sections.length,
    citationCount,
  };
}

export function buildModelInvocationSql(taskId) {
  return `select task_id, provider, model_name, status, total_tokens, trace_id, error_code, error_message from model_invocations where task_id = ${taskId} order by id desc limit 1;`;
}

export function parseModelInvocationLine(line) {
  const [taskIdValue, provider, modelName, status, totalTokens, traceId, errorCode, errorMessage] = line.split('|');
  return {
    taskId: Number(taskIdValue),
    provider: provider || null,
    modelName: modelName || null,
    status: status || null,
    totalTokens: totalTokens ? Number(totalTokens) : null,
    traceId: traceId || null,
    errorCode: errorCode || null,
    errorMessage: errorMessage || null,
  };
}

export function evaluateSmokeResult({ strictAudit = false, reportSummary, modelInvocation }) {
  const failures = [];

  if (reportSummary?.status !== 'completed') {
    failures.push('REPORT_NOT_COMPLETED');
  }

  if (strictAudit) {
    if (!modelInvocation) {
      failures.push('STRICT_AUDIT_REQUIRES_MODEL_INVOCATION');
      return failures;
    }
    if (modelInvocation.provider === 'local-fallback') {
      failures.push('STRICT_AUDIT_REQUIRES_REAL_PROVIDER');
    }
    if (modelInvocation.status !== 'succeeded') {
      failures.push('STRICT_AUDIT_REQUIRES_SUCCEEDED_INVOCATION');
    }
  }

  return failures;
}
