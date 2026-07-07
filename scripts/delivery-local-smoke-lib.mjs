export function buildDeliverySmokeSteps({
  dashscopeApiKey,
  realBackendApiBaseUrl = 'http://127.0.0.1:18082/api/v1',
  realBackendOrigin = 'http://127.0.0.1:18082',
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  tlsGatewayBaseUrl = 'https://127.0.0.1:18443',
  gatewayApiBaseUrl = 'http://127.0.0.1:18000/api/v1',
  gatewayOrigin = 'http://127.0.0.1:18000',
  postgresContainer = 'ir-postgres',
  proxyEnv = {},
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
        P0_SMOKE_GATEWAY_API_BASE_URL: gatewayApiBaseUrl,
        P0_SMOKE_GATEWAY_ORIGIN: gatewayOrigin,
        P0_SMOKE_POSTGRES_CONTAINER: postgresContainer,
        ...proxyEnv,
      },
    },
    {
      name: 'p1-local-smoke',
      command: 'node',
      args: ['scripts/p1-local-smoke.mjs'],
      env: {
        P1_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P1_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
        P1_SMOKE_HIGRESS_API_BASE_URL: gatewayApiBaseUrl,
        P1_SMOKE_HIGRESS_ORIGIN: gatewayOrigin,
      },
    },
    {
      name: 'p2-local-smoke',
      command: 'node',
      args: ['scripts/p2-local-smoke.mjs'],
      env: {
        P2_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P2_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
        P2_SMOKE_HIGRESS_GATEWAY_BASE_URL: gatewayBaseUrl,
        P2_SMOKE_HIGRESS_TLS_GATEWAY_BASE_URL: tlsGatewayBaseUrl,
        P2_SMOKE_HIGRESS_API_BASE_URL: gatewayApiBaseUrl,
        P2_SMOKE_HIGRESS_ORIGIN: gatewayOrigin,
      },
    },
    {
      name: 'p3-local-smoke',
      command: 'node',
      args: ['scripts/p3-local-smoke.mjs'],
      env: {
        P3_SMOKE_REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        P3_SMOKE_REAL_BACKEND_ORIGIN: realBackendOrigin,
        P3_SMOKE_HIGRESS_API_BASE_URL: gatewayApiBaseUrl,
        P3_SMOKE_HIGRESS_ORIGIN: gatewayOrigin,
      },
    },
  ];
}
