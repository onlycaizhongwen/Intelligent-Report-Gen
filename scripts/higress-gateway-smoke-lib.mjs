import crypto from 'node:crypto';

const NEGATIVE_MULTIPART_BOUNDARY = '----ir-higress-negative-boundary';
const NEGATIVE_MULTIPART_BODY = [
  `--${NEGATIVE_MULTIPART_BOUNDARY}`,
  'Content-Disposition: form-data; name="file"; filename="gateway-negative-smoke.txt"',
  'Content-Type: text/plain',
  '',
  'gateway negative authorization smoke',
  `--${NEGATIVE_MULTIPART_BOUNDARY}--`,
  '',
].join('\r\n');

function base64urlJson(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url');
}

export function buildGatewaySecurityJwt({
  secret,
  userId,
  roles,
  permissions,
  status = 'enabled',
  nowEpochSeconds = Math.floor(Date.now() / 1000),
  expiresInSeconds = 7200,
}) {
  const header = base64urlJson({ alg: 'HS256', typ: 'JWT' });
  const payload = base64urlJson({
    sub: String(userId),
    roles,
    permissions,
    status,
    iat: nowEpochSeconds,
    exp: nowEpochSeconds + expiresInSeconds,
  });
  const unsigned = `${header}.${payload}`;
  const signature = crypto.createHmac('sha256', secret).update(unsigned).digest('base64url');
  return `${unsigned}.${signature}`;
}

export function buildHigressGatewaySmokeChecks({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  return [
    {
      name: 'java-permission-matrix-through-higress',
      url: `${baseUrl}/api/v1/roles/permission-matrix`,
      expected: 'java-auth-required',
      headers: { Authorization: 'Bearer invalid' },
    },
    {
      name: 'python-chat-not-public-through-higress',
      url: `${baseUrl}/api/v1/chat`,
      expected: 'not-python-chat',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: 'gateway boundary smoke' }),
    },
  ];
}

export function buildHigressEndpointSecurityChecks({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  const insufficientToken = buildGatewaySecurityJwt({
    secret: jwtSecret,
    userId: 901,
    roles: ['viewer'],
    permissions: ['report:read'],
  });
  const allowedToken = buildGatewaySecurityJwt({
    secret: jwtSecret,
    userId: 902,
    roles: ['system_admin'],
    permissions: ['permission:read'],
  });
  const url = `${baseUrl}/api/v1/roles/permission-matrix`;

  return [
    {
      name: 'permission-matrix-missing-token-through-higress',
      url,
      expectedStatus: 401,
      expectedCode: 401,
      headers: {},
    },
    {
      name: 'permission-matrix-insufficient-permission-through-higress',
      url,
      expectedStatus: 403,
      expectedCode: 403,
      headers: { Authorization: `Bearer ${insufficientToken}` },
    },
    {
      name: 'permission-matrix-authorized-through-higress',
      url,
      expectedStatus: 200,
      expectedCode: 200,
      headers: { Authorization: `Bearer ${allowedToken}` },
      bodyIncludes: '"permission:read"',
    },
  ];
}

export function buildHigressRepresentativeAuthorizationMatrixChecks({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  const surfaces = [
    {
      key: 'report-read',
      path: '/api/v1/reports?page=1&pageSize=1',
      permission: 'report:read',
    },
    {
      key: 'knowledge-manage',
      path: '/api/v1/knowledge-bases?page=1&pageSize=1',
      permission: 'knowledge:manage',
    },
    {
      key: 'rule-manage',
      path: '/api/v1/rules?page=1&pageSize=1',
      permission: 'rule:manage',
    },
    {
      key: 'audit-read',
      path: '/api/v1/audit-logs?page=1&pageSize=1',
      permission: 'audit:read',
    },
    {
      key: 'dashboard-read',
      path: '/api/v1/dashboard/overview?range=last7days',
      permission: 'dashboard:read',
    },
    {
      key: 'notification-read',
      path: '/api/v1/system-alerts?page=1&pageSize=1',
      permission: 'notification:read',
    },
  ];
  return surfaces.flatMap((surface, index) => {
    const insufficientToken = buildGatewaySecurityJwt({
      secret: jwtSecret,
      userId: 920 + index,
      roles: ['viewer'],
      permissions: [],
    });
    const allowedToken = buildGatewaySecurityJwt({
      secret: jwtSecret,
      userId: 940 + index,
      roles: ['module_operator'],
      permissions: [surface.permission],
    });
    const url = `${baseUrl}${surface.path}`;
    return [
      {
        name: `${surface.key}-missing-token-through-higress`,
        url,
        expectedStatus: 401,
        expectedCode: 401,
        headers: {},
      },
      {
        name: `${surface.key}-insufficient-permission-through-higress`,
        url,
        expectedStatus: 403,
        expectedCode: 403,
        headers: { Authorization: `Bearer ${insufficientToken}` },
      },
      {
        name: `${surface.key}-authorized-through-higress`,
        url,
        expectedStatus: 200,
        expectedCode: 200,
        headers: { Authorization: `Bearer ${allowedToken}` },
      },
    ];
  });
}

export function buildHigressApplicationLayerAttackFallbackChecks({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  const allowedToken = buildGatewaySecurityJwt({
    secret: jwtSecret,
    userId: 960,
    roles: ['attack_probe'],
    permissions: ['report:read'],
  });
  const attackPage = encodeURIComponent("1' or '1'='1");

  return [
    {
      name: 'report-list-malformed-page-attack-fallback-through-higress',
      url: `${baseUrl}/api/v1/reports?page=${attackPage}&pageSize=1`,
      expectedStatus: 400,
      expectedCode: 400,
      headers: { Authorization: `Bearer ${allowedToken}` },
    },
  ];
}

export function buildHigressPermissionCatalogAuthorizationChecks({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  const surfaces = [
    {
      key: 'report-create',
      path: '/api/v1/report-templates',
      permission: 'report:create',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'report-read',
      path: '/api/v1/reports?page=1&pageSize=1',
      permission: 'report:read',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'report-export',
      path: '/api/v1/files/report-exports/999999999/download-url',
      permission: 'report:export',
      authorizedStatus: 404,
      authorizedCode: 404,
      bodyIncludes: 'report export file not found',
    },
    {
      key: 'report-template-manage',
      path: '/api/v1/enterprise-export-templates?page=1&pageSize=1',
      permission: 'report:template:manage',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'report-share',
      path: '/api/v1/reports/999999999/share-links',
      method: 'POST',
      permission: 'report:share',
      authorizedStatus: 404,
      authorizedCode: 404,
      body: JSON.stringify({ expiresInDays: 1 }),
      bodyIncludes: 'report not found',
    },
    {
      key: 'collaboration-write',
      path: '/api/v1/tasks/999999999/status',
      method: 'PUT',
      permission: 'collaboration:write',
      authorizedStatus: 404,
      authorizedCode: 404,
      body: JSON.stringify({ status: 'completed' }),
      bodyIncludes: 'task not found',
    },
    {
      key: 'knowledge-manage',
      path: '/api/v1/knowledge-bases?page=1&pageSize=1',
      permission: 'knowledge:manage',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'knowledge-upload',
      path: '/api/v1/documents/999999999',
      permission: 'knowledge:upload',
      authorizedStatus: 404,
      authorizedCode: 404,
      bodyIncludes: 'document not found',
    },
    {
      key: 'datasource-manage',
      path: '/api/v1/data-sources/presets',
      permission: 'datasource:manage',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'rule-manage',
      path: '/api/v1/rules?page=1&pageSize=1',
      permission: 'rule:manage',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'rule-debug',
      path: '/api/v1/rules/999999999/runs?page=1&pageSize=1',
      permission: 'rule:debug',
      authorizedStatus: 404,
      authorizedCode: 404,
      bodyIncludes: 'rule not found',
    },
    {
      key: 'audit-read',
      path: '/api/v1/audit-logs?page=1&pageSize=1',
      permission: 'audit:read',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'dashboard-read',
      path: '/api/v1/dashboard/overview?range=last7days',
      permission: 'dashboard:read',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'notification-read',
      path: '/api/v1/system-alerts?page=1&pageSize=1',
      permission: 'notification:read',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
    {
      key: 'permission-read',
      path: '/api/v1/roles/permission-matrix',
      permission: 'permission:read',
      authorizedStatus: 200,
      authorizedCode: 200,
      bodyIncludes: '"permission:read"',
    },
    {
      key: 'user-manage',
      path: '/api/v1/users?page=1&pageSize=1',
      permission: 'user:manage',
      authorizedStatus: 200,
      authorizedCode: 200,
    },
  ];

  return surfaces.flatMap((surface, index) => {
    const insufficientToken = buildGatewaySecurityJwt({
      secret: jwtSecret,
      userId: 960 + index,
      roles: ['viewer'],
      permissions: [],
    });
    const allowedToken = buildGatewaySecurityJwt({
      secret: jwtSecret,
      userId: 980 + index,
      roles: ['permission_catalog_probe'],
      permissions: [surface.permission],
    });
    const contentHeaders = surface.body == null ? {} : { 'Content-Type': 'application/json' };
    const url = `${baseUrl}${surface.path}`;
    return [
      {
        name: `catalog-${surface.key}-missing-token-through-higress`,
        permission: surface.permission,
        url,
        method: surface.method,
        expectedStatus: 401,
        expectedCode: 401,
        headers: contentHeaders,
        body: surface.body,
      },
      {
        name: `catalog-${surface.key}-insufficient-permission-through-higress`,
        permission: surface.permission,
        url,
        method: surface.method,
        expectedStatus: 403,
        expectedCode: 403,
        headers: { Authorization: `Bearer ${insufficientToken}`, ...contentHeaders },
        body: surface.body,
      },
      {
        name: `catalog-${surface.key}-authorized-through-higress`,
        permission: surface.permission,
        url,
        method: surface.method,
        expectedStatus: surface.authorizedStatus,
        expectedCode: surface.authorizedCode,
        headers: { Authorization: `Bearer ${allowedToken}`, ...contentHeaders },
        body: surface.body,
        bodyIncludes: surface.bodyIncludes,
      },
    ];
  });
}

export function buildHigressControllerEndpointAuthorizationChecks({
  controllerMatrix = [],
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  return controllerMatrix
    .filter((endpoint) => endpoint.boundary === 'permission')
    .flatMap((endpoint, index) => {
      const endpointKey = `${endpoint.method} ${endpoint.path}`;
      const probePath = toProbePath(endpoint.path);
      const contentProbe = buildControllerEndpointContentProbe(endpoint);
      const insufficientToken = buildGatewaySecurityJwt({
        secret: jwtSecret,
        userId: 1200 + index,
        roles: ['endpoint_probe_forbidden'],
        permissions: [],
      });
      const allowedToken = buildGatewaySecurityJwt({
        secret: jwtSecret,
        userId: 1400 + index,
        roles: ['endpoint_probe_authorized'],
        permissions: [endpoint.permission],
      });
      return [
        {
          name: `controller-endpoint-${index}-missing-token-through-higress`,
          controllerEndpoint: endpointKey,
          controller: endpoint.controller,
          handler: endpoint.handler,
          permission: endpoint.permission,
          expectedBoundary: 'missing-token',
          url: `${baseUrl}${probePath}`,
          method: endpoint.method,
          expectedStatus: 401,
          expectedCode: 401,
          headers: contentProbe.headers,
          body: contentProbe.body,
        },
        {
          name: `controller-endpoint-${index}-insufficient-permission-through-higress`,
          controllerEndpoint: endpointKey,
          controller: endpoint.controller,
          handler: endpoint.handler,
          permission: endpoint.permission,
          expectedBoundary: 'forbidden',
          url: `${baseUrl}${probePath}`,
          method: endpoint.method,
          expectedStatus: 403,
          expectedCode: 403,
          headers: { Authorization: `Bearer ${insufficientToken}`, ...contentProbe.headers },
          body: contentProbe.body,
        },
        {
          name: `controller-endpoint-${index}-authorized-through-higress`,
          controllerEndpoint: endpointKey,
          controller: endpoint.controller,
          handler: endpoint.handler,
          permission: endpoint.permission,
          expectedBoundary: 'authorized',
          url: `${baseUrl}${probePath}`,
          method: endpoint.method,
          expectedStatus: null,
          expectedCode: null,
          headers: { Authorization: `Bearer ${allowedToken}`, ...contentProbe.headers },
          body: contentProbe.body,
        },
      ];
    });
}

function buildControllerEndpointContentProbe(endpoint) {
  if (!isMultipartEndpoint(endpoint)) {
    return { headers: {}, body: undefined };
  }
  return {
    headers: { 'Content-Type': `multipart/form-data; boundary=${NEGATIVE_MULTIPART_BOUNDARY}` },
    body: NEGATIVE_MULTIPART_BODY,
  };
}

function isMultipartEndpoint(endpoint) {
  return (endpoint.consumes ?? []).some((value) =>
    value === 'MediaType.MULTIPART_FORM_DATA_VALUE'
    || String(value).toLowerCase() === 'multipart/form-data');
}

export function buildHigressControllerEndpointAuthorizationNegativeChecks({
  controllerMatrix = [],
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  return buildHigressControllerEndpointAuthorizationChecks({
    controllerMatrix,
    gatewayBaseUrl,
    jwtSecret,
  }).filter((check) => check.expectedBoundary !== 'authorized');
}

export function renderHigressControllerEndpointAuthorizationMatrixMarkdown(checks) {
  const grouped = new Map();
  for (const check of checks) {
    if (!grouped.has(check.controllerEndpoint)) {
      grouped.set(check.controllerEndpoint, {
        controllerEndpoint: check.controllerEndpoint,
        permission: check.permission,
        controller: check.controller,
        handler: check.handler,
        checkCount: 0,
      });
    }
    grouped.get(check.controllerEndpoint).checkCount += 1;
  }
  const rows = [
    '# Higress Controller Endpoint Authorization Matrix',
    '',
    '| Controller Endpoint | Permission | Check Count | Controller | Handler |',
    '| --- | --- | --- | --- | --- |',
    ...[...grouped.values()]
      .sort((left, right) => left.controllerEndpoint.localeCompare(right.controllerEndpoint))
      .map((entry) => `| ${entry.controllerEndpoint} | ${entry.permission} | ${entry.checkCount} | ${entry.controller} | ${entry.handler} |`),
    '',
  ];
  return rows.join('\n');
}

export function buildHigressControllerEndpointAuthorizationSampleChecks({
  controllerMatrix = [],
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
  sampleLimit = 0,
} = {}) {
  const limit = Number.isFinite(Number(sampleLimit))
    ? Math.max(0, Math.floor(Number(sampleLimit)))
    : 0;
  if (limit === 0) {
    return [];
  }

  const sampledMatrix = controllerMatrix
    .filter((endpoint) => endpoint.boundary === 'permission')
    .slice(0, limit);

  return buildHigressControllerEndpointAuthorizationChecks({
    controllerMatrix: sampledMatrix,
    gatewayBaseUrl,
    jwtSecret,
  }).filter((check) => check.expectedBoundary !== 'authorized');
}

export function buildHigressDataSourceSecurityChecks({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
} = {}) {
  const baseUrl = gatewayBaseUrl.replace(/\/$/, '');
  const insufficientToken = buildGatewaySecurityJwt({
    secret: jwtSecret,
    userId: 911,
    roles: ['viewer'],
    permissions: ['report:read'],
  });
  const allowedToken = buildGatewaySecurityJwt({
    secret: jwtSecret,
    userId: 912,
    roles: ['data_source_admin'],
    permissions: ['datasource:manage', 'knowledge:manage'],
  });
  const authHeaders = {
    Authorization: `Bearer ${allowedToken}`,
    'Content-Type': 'application/json',
  };
  const presetsUrl = `${baseUrl}/api/v1/data-sources/presets`;

  return [
    {
      name: 'data-source-presets-missing-token-through-higress',
      url: presetsUrl,
      expectedStatus: 401,
      expectedCode: 401,
      headers: {},
    },
    {
      name: 'data-source-presets-insufficient-permission-through-higress',
      url: presetsUrl,
      expectedStatus: 403,
      expectedCode: 403,
      headers: { Authorization: `Bearer ${insufficientToken}` },
    },
    {
      name: 'data-source-presets-authorized-through-higress',
      url: presetsUrl,
      expectedStatus: 200,
      expectedCode: 200,
      headers: { Authorization: `Bearer ${allowedToken}` },
      bodyIncludes: 'finance-api',
    },
    {
      name: 'data-source-profile-drift-audit-authorized-through-higress',
      url: `${baseUrl}/api/v1/data-sources/profile-drift?limit=10`,
      expectedStatus: 200,
      expectedCode: 200,
      headers: { Authorization: `Bearer ${allowedToken}` },
      bodyIncludes: 'driftCount',
    },
    {
      name: 'data-source-profile-drift-bulk-repair-preview-through-higress',
      url: `${baseUrl}/api/v1/data-sources/profile-drift/repair`,
      method: 'POST',
      expectedStatus: 200,
      expectedCode: 200,
      headers: authHeaders,
      body: JSON.stringify({ limit: 10, confirmed: false }),
      bodyIncludes: 'repairedCount',
    },
    {
      name: 'data-source-profile-drift-repair-not-found-through-higress',
      url: `${baseUrl}/api/v1/data-sources/999999999/profile-drift/repair`,
      method: 'POST',
      expectedStatus: 404,
      expectedCode: 404,
      headers: authHeaders,
      body: JSON.stringify({ confirmed: false }),
      bodyIncludes: 'knowledge data source not found',
    },
    {
      name: 'data-source-save-validation-through-higress',
      url: `${baseUrl}/api/v1/data-sources`,
      method: 'POST',
      expectedStatus: 400,
      expectedCode: 400,
      headers: authHeaders,
      body: JSON.stringify({}),
      bodyIncludes: 'knowledge data source name is required',
    },
    {
      name: 'data-source-sync-not-found-through-higress',
      url: `${baseUrl}/api/v1/data-sources/999999999/sync-runs`,
      method: 'POST',
      expectedStatus: 404,
      expectedCode: 404,
      headers: authHeaders,
      body: JSON.stringify({ mode: 'manual' }),
      bodyIncludes: 'knowledge data source not found',
    },
  ];
}

function toProbePath(path) {
  return path.replace(/\{[^}]+}/g, '999999999');
}

export function classifyGatewayResponse(status, body) {
  const text = String(body ?? '');
  if (status === 401 && text.includes('"code":401')) {
    return 'java-auth-required';
  }
  if (status === 200 && /"answer"|"choices"|"model"/.test(text)) {
    return 'python-chat-public';
  }
  return 'not-python-chat';
}

function evaluateEndpointSecurityCheck(check, status, body) {
  let code = null;
  try {
    code = JSON.parse(body)?.code ?? null;
  } catch {
    code = null;
  }
  const bodyMatched = !check.bodyIncludes || String(body ?? '').includes(check.bodyIncludes);
  const passed = status === check.expectedStatus && code === check.expectedCode && bodyMatched;
  return {
    code,
    classification: passed ? 'endpoint-security-expected' : 'endpoint-security-unexpected',
    passed,
  };
}

export async function runHigressGatewaySmoke({
  gatewayBaseUrl = 'http://127.0.0.1:18000',
  jwtSecret = 'local-dev-secret-change-me-32-bytes-minimum',
  controllerMatrix = [],
  controllerEndpointAuthorizationSampleLimit = 0,
  controllerEndpointAuthorizationNegativeCoverage = false,
  fetchImpl = fetch,
} = {}) {
  const checks = [
    ...buildHigressGatewaySmokeChecks({ gatewayBaseUrl }),
    ...buildHigressEndpointSecurityChecks({ gatewayBaseUrl, jwtSecret }),
    ...buildHigressRepresentativeAuthorizationMatrixChecks({ gatewayBaseUrl, jwtSecret }),
    ...buildHigressApplicationLayerAttackFallbackChecks({ gatewayBaseUrl, jwtSecret }),
    ...buildHigressPermissionCatalogAuthorizationChecks({ gatewayBaseUrl, jwtSecret }),
    ...buildHigressDataSourceSecurityChecks({ gatewayBaseUrl, jwtSecret }),
    ...(controllerEndpointAuthorizationNegativeCoverage
      ? buildHigressControllerEndpointAuthorizationNegativeChecks({
        controllerMatrix,
        gatewayBaseUrl,
        jwtSecret,
      })
      : buildHigressControllerEndpointAuthorizationSampleChecks({
        controllerMatrix,
        gatewayBaseUrl,
        jwtSecret,
        sampleLimit: controllerEndpointAuthorizationSampleLimit,
      })),
  ];
  const results = [];
  for (const check of checks) {
    const response = await fetchImpl(check.url, {
      method: check.method ?? 'GET',
      headers: check.headers,
      body: check.body,
    });
    const body = await response.text();
    const endpointSecurityResult = Number.isInteger(check.expectedStatus)
      ? evaluateEndpointSecurityCheck(check, response.status, body)
      : null;
    const classification = endpointSecurityResult?.classification
      ?? classifyGatewayResponse(response.status, body);
    const passed = endpointSecurityResult?.passed
      ?? (check.expected === 'not-python-chat'
        ? classification !== 'python-chat-public'
        : classification === check.expected);
    results.push({
      name: check.name,
      url: check.url,
      status: response.status,
      code: endpointSecurityResult?.code,
      classification,
      passed,
      bodyPreview: body.slice(0, 240),
    });
  }

  return {
    gatewayBaseUrl,
    passed: results.every((result) => result.passed),
    results,
  };
}
