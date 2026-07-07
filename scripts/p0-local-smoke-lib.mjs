export function buildP0SmokeSteps({
  dashscopeApiKey,
  realBackendApiBaseUrl = 'http://127.0.0.1:18082/api/v1',
  realBackendOrigin = 'http://127.0.0.1:18082',
  gatewayApiBaseUrl = 'http://127.0.0.1:18000/api/v1',
  gatewayOrigin = 'http://127.0.0.1:18000',
  postgresContainer = 'ir-postgres',
  proxyEnv = {},
} = {}) {
  if (!dashscopeApiKey) {
    throw new Error('P0 local smoke bundle requires dashscopeApiKey');
  }

  return [
    {
      name: 'uc01-provider-preflight',
      command: 'node',
      args: ['scripts/uc01-provider-connectivity-preflight.mjs'],
      env: {
        UC01_PROVIDER_PREFLIGHT_API_KEY: dashscopeApiKey,
        ...proxyEnv,
      },
    },
    {
      name: 'uc01-worker',
      command: 'node',
      args: ['scripts/uc01-real-provider-worker.mjs'],
      env: {
        UC01_REAL_PROVIDER_API_KEY: dashscopeApiKey,
        ...proxyEnv,
      },
    },
    {
      name: 'uc01-strict-smoke',
      command: 'node',
      args: ['scripts/uc01-real-provider-smoke.mjs'],
      env: {
        UC01_SMOKE_POSTGRES_CONTAINER: postgresContainer,
        UC01_SMOKE_STRICT_AUDIT: 'true',
      },
    },
    {
      name: 'uc06-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/knowledge-upload-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc02-report-template-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-template-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc02-template-completion-smoke',
      command: 'node',
      args: ['scripts/uc02-template-completion-smoke.mjs'],
      env: {
        UC02_TEMPLATE_COMPLETION_BASE_URL: realBackendApiBaseUrl,
        UC02_TEMPLATE_COMPLETION_POSTGRES_CONTAINER: postgresContainer,
      },
    },
    {
      name: 'uc02-template-completion-higress-smoke',
      command: 'node',
      args: ['scripts/uc02-template-completion-smoke.mjs'],
      env: {
        UC02_TEMPLATE_COMPLETION_BASE_URL: gatewayApiBaseUrl,
        UC02_TEMPLATE_COMPLETION_POSTGRES_CONTAINER: postgresContainer,
      },
    },
    {
      name: 'uc02-report-template-higress-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-template-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: gatewayApiBaseUrl,
        REAL_BACKEND_ORIGIN: gatewayOrigin,
      },
    },
    {
      name: 'uc04-higress-export-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-export-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: gatewayApiBaseUrl,
        REAL_BACKEND_ORIGIN: gatewayOrigin,
      },
    },
    {
      name: 'uc04-enterprise-export-template-higress-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/enterprise-export-templates-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: gatewayApiBaseUrl,
        REAL_BACKEND_ORIGIN: gatewayOrigin,
      },
    },
    {
      name: 'uc04-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-export-real-backend.spec.ts',
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
