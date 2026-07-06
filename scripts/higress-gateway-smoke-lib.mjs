import crypto from 'node:crypto';

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
  fetchImpl = fetch,
} = {}) {
  const checks = [
    ...buildHigressGatewaySmokeChecks({ gatewayBaseUrl }),
    ...buildHigressEndpointSecurityChecks({ gatewayBaseUrl, jwtSecret }),
    ...buildHigressDataSourceSecurityChecks({ gatewayBaseUrl, jwtSecret }),
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
