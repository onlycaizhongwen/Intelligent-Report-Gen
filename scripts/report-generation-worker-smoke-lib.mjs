export function buildReportGenerationWorkerHealthConfig({
  containerName = 'ir-report-generation-worker-smoke',
  requiredHealthStatus = 'healthy',
} = {}) {
  return {
    containerName,
    requiredHealthStatus,
  };
}

export function buildDockerInspectArgs(containerName) {
  return ['inspect', '--format', '{{json .State}}', containerName];
}

export function buildDockerLogsArgs(containerName, tail = 80) {
  return ['logs', '--tail', String(tail), containerName];
}

export function parseContainerState(rawState) {
  const parsed = JSON.parse(rawState);
  return {
    status: parsed.Status ?? 'unknown',
    running: parsed.Running === true,
    exitCode: Number.isInteger(parsed.ExitCode) ? parsed.ExitCode : null,
    error: parsed.Error ?? '',
    ...(parsed.Health
      ? {
          healthStatus: parsed.Health.Status ?? 'unknown',
          healthFailingStreak: Number.isInteger(parsed.Health.FailingStreak)
            ? parsed.Health.FailingStreak
            : null,
        }
      : {}),
  };
}

function redactSensitiveText(value) {
  return String(value ?? '')
    .replace(
      /(PASSWORD|SECRET|TOKEN|KEY|DATABASE_URL|LLM_API_KEY)=([^\s]+)/gi,
      '$1=<redacted>',
    )
    .replace(/postgresql:\/\/[^\s]+/gi, 'postgresql://<redacted>');
}

export function summarizeReportGenerationWorkerHealth({
  config = buildReportGenerationWorkerHealthConfig(),
  state,
  logs = '',
} = {}) {
  const base = {
    containerName: config.containerName,
    requiredHealthStatus: config.requiredHealthStatus,
  };

  if (!state) {
    return {
      passed: false,
      classification: 'report-generation-worker-missing',
      ...base,
      ...(logs ? { logsTail: redactSensitiveText(logs) } : {}),
    };
  }

  if (state.running !== true) {
    return {
      passed: false,
      classification: 'report-generation-worker-not-running',
      ...base,
      state,
      ...(logs ? { logsTail: redactSensitiveText(logs) } : {}),
    };
  }

  const healthy = state.healthStatus === config.requiredHealthStatus;
  return {
    passed: healthy,
    classification: healthy
      ? 'report-generation-worker-healthy'
      : 'report-generation-worker-unhealthy',
    ...base,
    state,
    ...(logs && !healthy ? { logsTail: redactSensitiveText(logs) } : {}),
  };
}
