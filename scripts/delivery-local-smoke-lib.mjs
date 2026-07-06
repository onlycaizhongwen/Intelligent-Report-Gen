export function buildDeliverySmokeSteps({
  dashscopeApiKey,
  realBackendApiBaseUrl = 'http://127.0.0.1:18082/api/v1',
  realBackendOrigin = 'http://127.0.0.1:18082',
  postgresContainer = 'ir-postgres',
} = {}) {
  if (!dashscopeApiKey) {
    throw new Error('Delivery local smoke bundle requires dashscopeApiKey');
  }

  return [
    {
      name: 'p0-local-smoke',
      command: 'node',
      args: ['scripts/p0-local-smoke.mjs'],
      env: {
        P0_SMOKE_DASHSCOPE_API_KEY: dashscopeApiKey,
        P0_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P0_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
        P0_SMOKE_POSTGRES_CONTAINER: postgresContainer,
      },
    },
    {
      name: 'p1-local-smoke',
      command: 'node',
      args: ['scripts/p1-local-smoke.mjs'],
      env: {
        P1_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P1_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'p2-local-smoke',
      command: 'node',
      args: ['scripts/p2-local-smoke.mjs'],
      env: {
        P2_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P2_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'p3-local-smoke',
      command: 'node',
      args: ['scripts/p3-local-smoke.mjs'],
      env: {
        P3_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P3_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
  ];
}
