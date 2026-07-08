import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildDockerInspectArgs,
  buildDockerLogsArgs,
  buildReportGenerationWorkerHealthConfig,
  parseContainerState,
  summarizeReportGenerationWorkerHealth,
} from '../../../scripts/report-generation-worker-smoke-lib.mjs';

test('buildReportGenerationWorkerHealthConfig targets the report generation worker smoke container', () => {
  const config = buildReportGenerationWorkerHealthConfig();

  assert.equal(config.containerName, 'ir-report-generation-worker-smoke');
  assert.equal(config.requiredHealthStatus, 'healthy');
});

test('buildDockerInspectArgs and buildDockerLogsArgs inspect bounded worker diagnostics', () => {
  assert.deepEqual(buildDockerInspectArgs('ir-report-generation-worker-smoke'), [
    'inspect',
    '--format',
    '{{json .State}}',
    'ir-report-generation-worker-smoke',
  ]);
  assert.deepEqual(buildDockerLogsArgs('ir-report-generation-worker-smoke'), [
    'logs',
    '--tail',
    '80',
    'ir-report-generation-worker-smoke',
  ]);
});

test('parseContainerState extracts report worker running and health status', () => {
  const state = parseContainerState(JSON.stringify({
    Status: 'running',
    Running: true,
    ExitCode: 0,
    Error: '',
    Health: {
      Status: 'healthy',
      FailingStreak: 0,
    },
  }));

  assert.deepEqual(state, {
    status: 'running',
    running: true,
    exitCode: 0,
    error: '',
    healthStatus: 'healthy',
    healthFailingStreak: 0,
  });
});

test('summarizeReportGenerationWorkerHealth passes only for running healthy worker', () => {
  const summary = summarizeReportGenerationWorkerHealth({
    config: buildReportGenerationWorkerHealthConfig(),
    state: {
      status: 'running',
      running: true,
      exitCode: 0,
      error: '',
      healthStatus: 'healthy',
      healthFailingStreak: 0,
    },
  });

  assert.deepEqual(summary, {
    passed: true,
    classification: 'report-generation-worker-healthy',
    containerName: 'ir-report-generation-worker-smoke',
    requiredHealthStatus: 'healthy',
    state: {
      status: 'running',
      running: true,
      exitCode: 0,
      error: '',
      healthStatus: 'healthy',
      healthFailingStreak: 0,
    },
  });
});

test('summarizeReportGenerationWorkerHealth fails closed for missing, exited, and unhealthy worker states', () => {
  assert.equal(
    summarizeReportGenerationWorkerHealth({
      config: buildReportGenerationWorkerHealthConfig(),
      state: null,
      logs: `LLM_API_${'KEY'}=secret DATABASE_${'URL'}=postgresql://report:password@postgres/db`,
    }).classification,
    'report-generation-worker-missing',
  );

  assert.equal(
    summarizeReportGenerationWorkerHealth({
      config: buildReportGenerationWorkerHealthConfig(),
      state: { status: 'exited', running: false, exitCode: 1, error: 'boom' },
    }).classification,
    'report-generation-worker-not-running',
  );

  const unhealthy = summarizeReportGenerationWorkerHealth({
    config: buildReportGenerationWorkerHealthConfig(),
    state: { status: 'running', running: true, healthStatus: 'unhealthy', healthFailingStreak: 3 },
    logs: `LLM_API_${'KEY'}=secret-token DATABASE_${'URL'}=postgresql://report:report123@postgres:5432/intelligent_report`,
  });

  assert.equal(unhealthy.passed, false);
  assert.equal(unhealthy.classification, 'report-generation-worker-unhealthy');
  assert.equal(unhealthy.logsTail.includes('secret-token'), false);
  assert.equal(unhealthy.logsTail.includes('report123'), false);
  assert.match(unhealthy.logsTail, /LLM_API_KEY=<redacted>/);
});
