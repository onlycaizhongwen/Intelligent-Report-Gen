export function buildP3SmokeSteps({
  realBackendApiBaseUrl = 'http://127.0.0.1:18082/api/v1',
  realBackendOrigin = 'http://127.0.0.1:18082',
} = {}) {
  return [
    {
      name: 'uc13-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/dashboard-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc07-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/data-source-sync-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc08-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/approval-inbox-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc08-rule-runtime-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/rule-runtime-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc08-rule-runtime-higress-compensation-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/rule-runtime-real-backend.spec.ts',
        '-g',
        'webhook compensation',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: 'http://127.0.0.1:18000/api/v1',
        REAL_BACKEND_ORIGIN: 'http://127.0.0.1:18000',
      },
    },
    {
      name: 'uc08-rule-runtime-higress-replay-exhaustion-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/rule-runtime-real-backend.spec.ts',
        '-g',
        'automatic webhook replay exhaustion',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: 'http://127.0.0.1:18000/api/v1',
        REAL_BACKEND_ORIGIN: 'http://127.0.0.1:18000',
        RULE_WEBHOOK_REPLAY_WORKER_ENABLED: 'true',
      },
    },
    {
      name: 'uc08-delegate-rules-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/approval-delegate-rules-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
  ];
}
