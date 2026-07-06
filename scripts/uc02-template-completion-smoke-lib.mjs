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
  const signature = crypto.createHmac('sha256', secret).update(unsigned).digest('base64url');
  return `${unsigned}.${signature}`;
}

export function buildAuthHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    'Content-Type': 'application/json',
  };
}

export function selectTemplateFieldValue(field) {
  if (field.type === 'select' && Array.isArray(field.options) && field.options.length > 0) {
    return field.options[field.options.length - 1];
  }
  if (field.type === 'number') {
    return 1;
  }
  const values = {
    period: field.defaultValue || '2026Q2',
    scope: 'East Region Completion Smoke',
    focus: 'Template completion and citation audit',
  };
  return values[field.fieldKey] ?? `${field.fieldKey}-completion-smoke`;
}

export function buildTemplateTaskRequest(template) {
  const payload = {};
  for (const field of template.fields ?? []) {
    payload[field.fieldKey] = selectTemplateFieldValue(field);
  }
  return {
    templateId: template.templateId,
    payload,
  };
}

export function buildConfirmOutlineRequest() {
  return {
    outline: {
      sections: ['Template executive summary', 'Template risk actions'],
    },
    confirmed: true,
  };
}

export function buildTemplateCompletionPayload({
  traceId = `trace-uc02-template-completion-${Date.now()}`,
  templateId = 'enterprise-quarterly',
} = {}) {
  const references = [
    {
      referenceId: 'uc02-template-source-1',
      sourceTitle: 'Template completion smoke source',
      sourceType: 'controlled_smoke',
      snapshot: 'Template-generated report completed with controlled worker callback evidence.',
      score: {
        credibility: 0.99,
        citationQuality: 0.97,
      },
      anchor: {
        sectionNo: 1,
        heading: 'Template executive summary',
        text: 'controlled worker callback evidence',
      },
    },
  ];
  return {
    sections: [
      {
        heading: 'Template executive summary',
        content: 'Template-generated report completed through the controlled worker callback path.',
        citations: references,
      },
      {
        heading: 'Template risk actions',
        content: 'The smoke verifies report status, version persistence, citations, and model audit metadata.',
        citations: [],
      },
    ],
    references,
    modelInvocation: {
      provider: 'local-controlled-worker',
      model: 'uc02-template-completion-smoke',
      status: 'succeeded',
      traceId,
      promptTokens: 12,
      completionTokens: 18,
      inputTokens: 12,
      outputTokens: 18,
      totalTokens: 30,
      latencyMs: 25,
      fallbackUsed: false,
      errorMessage: null,
      routingPolicy: {
        dimension: 'template',
        key: templateId,
        env: 'LOCAL_CONTROLLED_WORKER',
        candidates: ['uc02-template-completion-smoke'],
      },
    },
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
  return `select task_id, provider, model_name, status, total_tokens, trace_id from model_invocations where task_id = ${taskId} order by id desc limit 1;`;
}

export function evaluateTemplateCompletionSmoke({ reportSummary, modelInvocation }) {
  const failures = [];
  if (reportSummary?.status !== 'completed') {
    failures.push('REPORT_NOT_COMPLETED');
  }
  if (!reportSummary?.currentVersionId) {
    failures.push('REPORT_VERSION_MISSING');
  }
  if (!reportSummary?.sectionCount) {
    failures.push('REPORT_SECTIONS_MISSING');
  }
  if (!reportSummary?.citationCount) {
    failures.push('REPORT_CITATIONS_MISSING');
  }
  if (!modelInvocation) {
    failures.push('MODEL_INVOCATION_MISSING');
  } else {
    if (modelInvocation.provider !== 'local-controlled-worker') {
      failures.push('MODEL_INVOCATION_PROVIDER_UNEXPECTED');
    }
    if (modelInvocation.status !== 'succeeded') {
      failures.push('MODEL_INVOCATION_NOT_SUCCEEDED');
    }
    if (!modelInvocation.totalTokens) {
      failures.push('MODEL_INVOCATION_TOKENS_MISSING');
    }
  }
  return failures;
}
