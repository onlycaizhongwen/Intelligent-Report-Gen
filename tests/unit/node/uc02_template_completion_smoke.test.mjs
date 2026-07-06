import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildTemplateCompletionPayload,
  buildTemplateTaskRequest,
  evaluateTemplateCompletionSmoke,
  selectTemplateFieldValue,
} from '../../../scripts/uc02-template-completion-smoke-lib.mjs';

test('buildTemplateTaskRequest fills every template field with deterministic acceptance values', () => {
  const request = buildTemplateTaskRequest({
    templateId: 'enterprise-quarterly',
    fields: [
      { fieldKey: 'period', type: 'text', defaultValue: '2026Q1' },
      { fieldKey: 'scope', type: 'text' },
      { fieldKey: 'focus', type: 'textarea' },
      { fieldKey: 'style', type: 'select', options: ['管理摘要', '经营分析'] },
    ],
  });

  assert.deepEqual(request, {
    templateId: 'enterprise-quarterly',
    payload: {
      period: '2026Q1',
      scope: 'East Region Completion Smoke',
      focus: 'Template completion and citation audit',
      style: '经营分析',
    },
  });
});

test('selectTemplateFieldValue uses safe fallback values for unknown required fields', () => {
  assert.equal(selectTemplateFieldValue({ fieldKey: 'unknownField', type: 'number' }), 1);
  assert.equal(selectTemplateFieldValue({ fieldKey: 'unknownField', type: 'text' }), 'unknownField-completion-smoke');
});

test('buildTemplateCompletionPayload produces worker-style sections, references and model audit metadata', () => {
  const payload = buildTemplateCompletionPayload({
    traceId: 'trace-uc02-template-completion',
    templateId: 'enterprise-quarterly',
  });

  assert.equal(payload.sections.length, 2);
  assert.equal(payload.sections[0].heading, 'Template executive summary');
  assert.equal(payload.sections[0].citations[0].referenceId, 'uc02-template-source-1');
  assert.equal(payload.references[0].referenceId, 'uc02-template-source-1');
  assert.deepEqual(payload.modelInvocation, {
    provider: 'local-controlled-worker',
    model: 'uc02-template-completion-smoke',
    status: 'succeeded',
    traceId: 'trace-uc02-template-completion',
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
      key: 'enterprise-quarterly',
      env: 'LOCAL_CONTROLLED_WORKER',
      candidates: ['uc02-template-completion-smoke'],
    },
  });
});

test('evaluateTemplateCompletionSmoke requires completed report, content, citations and model audit', () => {
  const passing = evaluateTemplateCompletionSmoke({
    reportSummary: {
      status: 'completed',
      currentVersionId: 77,
      sectionCount: 2,
      citationCount: 1,
    },
    modelInvocation: {
      provider: 'local-controlled-worker',
      modelName: 'uc02-template-completion-smoke',
      status: 'succeeded',
      totalTokens: 30,
      traceId: 'trace-uc02-template-completion',
    },
  });

  assert.deepEqual(passing, []);
  assert.deepEqual(
    evaluateTemplateCompletionSmoke({
      reportSummary: { status: 'running', sectionCount: 0, citationCount: 0 },
      modelInvocation: null,
    }),
    [
      'REPORT_NOT_COMPLETED',
      'REPORT_VERSION_MISSING',
      'REPORT_SECTIONS_MISSING',
      'REPORT_CITATIONS_MISSING',
      'MODEL_INVOCATION_MISSING',
    ],
  );
});
