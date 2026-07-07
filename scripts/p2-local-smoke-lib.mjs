export function buildP2SmokeSteps({
  realBackendApiBaseUrl = 'http://127.0.0.1:18082/api/v1',
  realBackendOrigin = 'http://127.0.0.1:18082',
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  tlsGatewayBaseUrl = 'https://127.0.0.1:18443',
  gatewayApiBaseUrl = 'http://127.0.0.1:18000/api/v1',
  gatewayOrigin = 'http://127.0.0.1:18000',
} = {}) {
  return [
    {
      name: 'higress-endpoint-security-smoke',
      command: 'node',
      args: ['scripts/higress-gateway-smoke.mjs'],
      env: {
        HIGRESS_GATEWAY_BASE_URL: gatewayBaseUrl,
      },
    },
    {
      name: 'higress-tls-endpoint-security-smoke',
      command: 'node',
      args: ['scripts/higress-gateway-smoke.mjs'],
      env: {
        HIGRESS_GATEWAY_BASE_URL: tlsGatewayBaseUrl,
        NODE_TLS_REJECT_UNAUTHORIZED: '0',
      },
    },
    {
      name: 'uc10-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-collaboration-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc11-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/user-rbac-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: realBackendApiBaseUrl,
        REAL_BACKEND_ORIGIN: realBackendOrigin,
      },
    },
    {
      name: 'uc11-higress-rbac-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/user-rbac-real-backend.spec.ts',
      ],
      workdir: 'frontend/web-console',
      env: {
        RUN_REAL_BACKEND_E2E: 'true',
        REAL_BACKEND_API_BASE_URL: gatewayApiBaseUrl,
        REAL_BACKEND_ORIGIN: gatewayOrigin,
      },
    },
    {
      name: 'uc14-real-backend-e2e',
      command: 'npm',
      args: [
        'run',
        'e2e:real-backend',
        '--',
        'tests/e2e/report-version-real-backend.spec.ts',
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
