import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';

import {
  buildHigressControllerEndpointAuthorizationChecks,
  buildHigressControllerEndpointAuthorizationNegativeChecks,
  buildHigressControllerEndpointAuthorizationReadOnlyAuthorizedChecks,
  buildHigressApplicationLayerAttackFallbackChecks,
  buildHigressDataSourceSecurityChecks,
  buildGatewaySecurityJwt,
  buildHigressEndpointSecurityChecks,
  buildHigressGatewaySmokeChecks,
  buildHigressPermissionCatalogAuthorizationChecks,
  buildHigressRepresentativeAuthorizationMatrixChecks,
  classifyGatewayResponse,
  renderHigressControllerEndpointAuthorizationMatrixMarkdown,
  runHigressGatewaySmoke,
} from '../../../scripts/higress-gateway-smoke-lib.mjs';
import {
  buildJavaControllerAuthorizationMatrix,
} from '../../../scripts/controller-authorization-matrix-lib.mjs';

const controllersRoot = 'backend/java-report-core/src/main/java/com/company/report';
const endpointHigressMatrixDocPath = 'docs/skill-chain/higress_controller_endpoint_authorization_matrix.md';

test('buildHigressGatewaySmokeChecks targets Java-routed API and blocked Python chat path', () => {
  const checks = buildHigressGatewaySmokeChecks({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
  });

  assert.deepEqual(
    checks.map((check) => check.name),
    ['java-permission-matrix-through-higress', 'python-chat-not-public-through-higress'],
  );
  assert.equal(checks[0].url, 'http://127.0.0.1:28000/api/v1/roles/permission-matrix');
  assert.equal(checks[0].expected, 'java-auth-required');
  assert.equal(checks[1].url, 'http://127.0.0.1:28000/api/v1/chat');
  assert.equal(checks[1].expected, 'not-python-chat');
});

test('classifyGatewayResponse recognizes Java API authentication boundary', () => {
  assert.equal(
    classifyGatewayResponse(401, '{"code":401,"message":"请登录后继续操作","data":null}'),
    'java-auth-required',
  );
});

test('classifyGatewayResponse recognizes accidental Python chat exposure', () => {
  assert.equal(
    classifyGatewayResponse(200, '{"answer":"hello","model":"gpt"}'),
    'python-chat-public',
  );
});

function decodeJwtPayload(token) {
  const [, payload] = token.split('.');
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
}

test('buildGatewaySecurityJwt creates Java-compatible permission claims', () => {
  const token = buildGatewaySecurityJwt({
    secret: 'local-dev-secret-change-me-32-bytes-minimum',
    userId: 502,
    roles: ['system_admin'],
    permissions: ['permission:read'],
    nowEpochSeconds: 1800000000,
  });

  const payload = decodeJwtPayload(token);

  assert.equal(payload.sub, '502');
  assert.deepEqual(payload.roles, ['system_admin']);
  assert.deepEqual(payload.permissions, ['permission:read']);
  assert.equal(payload.status, 'enabled');
  assert.equal(payload.iat, 1800000000);
  assert.equal(payload.exp, 1800007200);
});

test('buildHigressEndpointSecurityChecks covers unauthenticated, forbidden and allowed gateway outcomes', () => {
  const checks = buildHigressEndpointSecurityChecks({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.deepEqual(
    checks.map((check) => [check.name, check.expectedStatus, check.expectedCode]),
    [
      ['permission-matrix-missing-token-through-higress', 401, 401],
      ['permission-matrix-insufficient-permission-through-higress', 403, 403],
      ['permission-matrix-authorized-through-higress', 200, 200],
    ],
  );
  assert.equal(checks[0].headers.Authorization, undefined);
  assert.match(checks[1].headers.Authorization, /^Bearer /);
  assert.match(checks[2].headers.Authorization, /^Bearer /);
  assert.equal(checks[0].url, 'http://127.0.0.1:28000/api/v1/roles/permission-matrix');
});

test('buildHigressDataSourceSecurityChecks covers presets, configuration and sync routes', () => {
  const checks = buildHigressDataSourceSecurityChecks({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.deepEqual(
    checks.map((check) => [check.name, check.expectedStatus, check.expectedCode]),
    [
      ['data-source-presets-missing-token-through-higress', 401, 401],
      ['data-source-presets-insufficient-permission-through-higress', 403, 403],
      ['data-source-presets-authorized-through-higress', 200, 200],
      ['data-source-profile-drift-audit-authorized-through-higress', 200, 200],
      ['data-source-profile-drift-bulk-repair-preview-through-higress', 200, 200],
      ['data-source-profile-drift-repair-not-found-through-higress', 404, 404],
      ['data-source-save-validation-through-higress', 400, 400],
      ['data-source-sync-not-found-through-higress', 404, 404],
    ],
  );
  assert.equal(checks[0].url, 'http://127.0.0.1:28000/api/v1/data-sources/presets');
  assert.equal(checks[3].url, 'http://127.0.0.1:28000/api/v1/data-sources/profile-drift?limit=10');
  assert.equal(checks[4].method, 'POST');
  assert.equal(checks[4].url, 'http://127.0.0.1:28000/api/v1/data-sources/profile-drift/repair');
  assert.equal(checks[5].method, 'POST');
  assert.equal(checks[5].url, 'http://127.0.0.1:28000/api/v1/data-sources/999999999/profile-drift/repair');
  assert.equal(checks[6].method, 'POST');
  assert.equal(checks[6].url, 'http://127.0.0.1:28000/api/v1/data-sources');
  assert.equal(checks[7].url, 'http://127.0.0.1:28000/api/v1/data-sources/999999999/sync-runs');
  assert.match(checks[2].headers.Authorization, /^Bearer /);
  assert.match(checks[3].headers.Authorization, /^Bearer /);
  assert.match(checks[4].headers.Authorization, /^Bearer /);
  assert.match(checks[5].headers.Authorization, /^Bearer /);
  assert.match(checks[6].headers.Authorization, /^Bearer /);
  assert.match(checks[7].headers.Authorization, /^Bearer /);
});

test('buildHigressApplicationLayerAttackFallbackChecks rejects malformed pagination without 500', () => {
  const checks = buildHigressApplicationLayerAttackFallbackChecks({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.deepEqual(
    checks.map((check) => [check.name, check.expectedStatus, check.expectedCode]),
    [
      ['report-list-malformed-page-attack-fallback-through-higress', 400, 400],
    ],
  );
  assert.equal(
    checks[0].url,
    "http://127.0.0.1:28000/api/v1/reports?page=1'%20or%20'1'%3D'1&pageSize=1",
  );
  assert.match(checks[0].headers.Authorization, /^Bearer /);
});

test('buildHigressRepresentativeAuthorizationMatrixChecks covers core modules', () => {
  const checks = buildHigressRepresentativeAuthorizationMatrixChecks({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.deepEqual(
    checks.map((check) => [check.name, check.expectedStatus, check.expectedCode]),
    [
      ['report-read-missing-token-through-higress', 401, 401],
      ['report-read-insufficient-permission-through-higress', 403, 403],
      ['report-read-authorized-through-higress', 200, 200],
      ['knowledge-manage-missing-token-through-higress', 401, 401],
      ['knowledge-manage-insufficient-permission-through-higress', 403, 403],
      ['knowledge-manage-authorized-through-higress', 200, 200],
      ['rule-manage-missing-token-through-higress', 401, 401],
      ['rule-manage-insufficient-permission-through-higress', 403, 403],
      ['rule-manage-authorized-through-higress', 200, 200],
      ['audit-read-missing-token-through-higress', 401, 401],
      ['audit-read-insufficient-permission-through-higress', 403, 403],
      ['audit-read-authorized-through-higress', 200, 200],
      ['dashboard-read-missing-token-through-higress', 401, 401],
      ['dashboard-read-insufficient-permission-through-higress', 403, 403],
      ['dashboard-read-authorized-through-higress', 200, 200],
      ['notification-read-missing-token-through-higress', 401, 401],
      ['notification-read-insufficient-permission-through-higress', 403, 403],
      ['notification-read-authorized-through-higress', 200, 200],
    ],
  );
  assert.equal(checks[0].url, 'http://127.0.0.1:28000/api/v1/reports?page=1&pageSize=1');
  assert.equal(checks[8].url, 'http://127.0.0.1:28000/api/v1/rules?page=1&pageSize=1');
  assert.equal(checks[14].url, 'http://127.0.0.1:28000/api/v1/dashboard/overview?range=last7days');
  assert.match(checks[2].headers.Authorization, /^Bearer /);
  assert.match(checks[17].headers.Authorization, /^Bearer /);
});

test('buildHigressPermissionCatalogAuthorizationChecks covers every RBAC catalog permission', () => {
  const checks = buildHigressPermissionCatalogAuthorizationChecks({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  const permissions = new Set(checks.map((check) => check.permission));
  assert.deepEqual(
    [...permissions].sort(),
    [
      'audit:read',
      'collaboration:write',
      'dashboard:read',
      'datasource:manage',
      'knowledge:manage',
      'knowledge:upload',
      'notification:read',
      'permission:read',
      'report:create',
      'report:export',
      'report:read',
      'report:share',
      'report:template:manage',
      'rule:debug',
      'rule:manage',
      'user:manage',
    ],
  );

  for (const permission of permissions) {
    const permissionChecks = checks.filter((check) => check.permission === permission);
    assert.equal(permissionChecks.length, 3, `${permission} should have 401/403/authorized checks`);
    assert.equal(permissionChecks[0].expectedStatus, 401);
    assert.equal(permissionChecks[1].expectedStatus, 403);
    assert.match(permissionChecks[1].headers.Authorization, /^Bearer /);
    assert.match(permissionChecks[2].headers.Authorization, /^Bearer /);
  }

  assert.equal(checks.length, 48);
  assert.equal(checks.find((check) => check.name === 'catalog-report-create-authorized-through-higress')?.url, 'http://127.0.0.1:28000/api/v1/report-templates');
  assert.equal(checks.find((check) => check.name === 'catalog-knowledge-upload-authorized-through-higress')?.url, 'http://127.0.0.1:28000/api/v1/documents/999999999');
  assert.equal(checks.find((check) => check.name === 'catalog-rule-debug-authorized-through-higress')?.url, 'http://127.0.0.1:28000/api/v1/rules/999999999/runs?page=1&pageSize=1');
});

test('buildHigressControllerEndpointAuthorizationChecks covers every permission controller endpoint', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });
  const permissionEndpoints = matrix.filter((entry) => entry.boundary === 'permission');
  const checks = buildHigressControllerEndpointAuthorizationChecks({
    controllerMatrix: matrix,
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.ok(permissionEndpoints.length > 80, 'expected broad endpoint-level permission coverage');
  assert.equal(checks.length, permissionEndpoints.length * 3);

  for (const endpoint of permissionEndpoints) {
    const endpointChecks = checks.filter((check) => check.controllerEndpoint === `${endpoint.method} ${endpoint.path}`);
    assert.equal(endpointChecks.length, 3, `${endpoint.method} ${endpoint.path} should have 401/403/authorized checks`);
    assert.deepEqual(endpointChecks.map((check) => check.expectedBoundary), ['missing-token', 'forbidden', 'authorized']);
    assert.equal(endpointChecks[0].expectedStatus, 401);
    assert.equal(endpointChecks[1].expectedStatus, 403);
    assert.equal(endpointChecks[2].permission, endpoint.permission);
  }

  const uploadChecks = checks.filter((check) =>
    check.controllerEndpoint === 'POST /api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments');
  assert.equal(uploadChecks.length, 3);
  assert.equal(uploadChecks[2].url, 'http://127.0.0.1:28000/api/v1/rules/999999999/approval-records/999999999/supplement-attachments');
  assert.equal(uploadChecks[2].method, 'POST');
  assert.equal(uploadChecks[2].permission, 'rule:debug');
  assert.match(uploadChecks[2].headers.Authorization, /^Bearer /);
});

test('buildHigressControllerEndpointAuthorizationNegativeChecks covers every permission endpoint without authorized write probes', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });
  const permissionEndpoints = matrix.filter((entry) => entry.boundary === 'permission');
  const checks = buildHigressControllerEndpointAuthorizationNegativeChecks({
    controllerMatrix: matrix,
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.ok(permissionEndpoints.length > 80, 'expected broad endpoint-level permission coverage');
  assert.equal(checks.length, permissionEndpoints.length * 2);
  assert.deepEqual(
    [...new Set(checks.map((check) => check.expectedBoundary))].sort(),
    ['forbidden', 'missing-token'],
  );
  assert.equal(checks.filter((check) => check.expectedBoundary === 'authorized').length, 0);

  for (const endpoint of permissionEndpoints) {
    const endpointChecks = checks.filter((check) => check.controllerEndpoint === `${endpoint.method} ${endpoint.path}`);
    assert.equal(endpointChecks.length, 2, `${endpoint.method} ${endpoint.path} should have safe 401/403 checks`);
    assert.deepEqual(endpointChecks.map((check) => check.expectedBoundary), ['missing-token', 'forbidden']);
    assert.equal(endpointChecks[0].expectedStatus, 401);
    assert.equal(endpointChecks[1].expectedStatus, 403);
  }

  for (const endpointPath of [
    '/api/v1/documents/upload',
    '/api/v1/rules/{ruleId}/approval-records/{approvalRecordId}/supplement-attachments',
  ]) {
    const endpointChecks = checks.filter((check) => check.controllerEndpoint === `POST ${endpointPath}`);
    assert.equal(endpointChecks.length, 2, `${endpointPath} should have safe multipart 401/403 checks`);
    for (const check of endpointChecks) {
      assert.match(check.headers['Content-Type'], /^multipart\/form-data; boundary=/);
      assert.match(check.body, /name="file"; filename="gateway-negative-smoke.txt"/);
      assert.match(check.body, /gateway negative authorization smoke/);
    }
  }
});

test('buildHigressControllerEndpointAuthorizationReadOnlyAuthorizedChecks keeps only safe GET authorized probes', () => {
  const controllerMatrix = [
    {
      boundary: 'permission',
      method: 'GET',
      path: '/api/v1/audit-logs',
      permission: 'audit:read',
      controller: 'AuditController',
      handler: 'listAuditLogs',
    },
    {
      boundary: 'permission',
      method: 'POST',
      path: '/api/v1/rules/{ruleId}/runs',
      permission: 'rule:debug',
      controller: 'RuleController',
      handler: 'runRule',
    },
    {
      boundary: 'permission',
      method: 'GET',
      path: '/api/v1/reports/{reportId}/versions/diff',
      permission: 'report:read',
      controller: 'ReportController',
      handler: 'compareVersions',
    },
  ];
  const checks = buildHigressControllerEndpointAuthorizationReadOnlyAuthorizedChecks({
    controllerMatrix,
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    jwtSecret: 'local-dev-secret-change-me-32-bytes-minimum',
  });

  assert.deepEqual(checks.map((check) => check.name), [
    'controller-endpoint-0-authorized-readonly-through-higress',
    'controller-endpoint-1-authorized-readonly-through-higress',
  ]);
  assert.equal(checks[0].method, 'GET');
  assert.equal(checks[0].expectedBoundary, 'authorized-readonly');
  assert.equal(checks[0].url, 'http://127.0.0.1:28000/api/v1/audit-logs');
  assert.match(checks[0].headers.Authorization, /^Bearer /);
  assert.equal(
    checks[1].url,
    'http://127.0.0.1:28000/api/v1/reports/999999999/versions/diff?baseVersionId=999999998&targetVersionId=999999999',
  );
});

test('Higress controller endpoint authorization matrix document stays synchronized', () => {
  const matrix = buildJavaControllerAuthorizationMatrix({ controllersRoot });
  const checks = buildHigressControllerEndpointAuthorizationChecks({
    controllerMatrix: matrix,
    gatewayBaseUrl: 'http://127.0.0.1:18000',
  });
  const expected = renderHigressControllerEndpointAuthorizationMatrixMarkdown(checks);
  const actual = fs.readFileSync(endpointHigressMatrixDocPath, 'utf8');

  assert.equal(actual, expected);
  assert.match(actual, /\| POST \/api\/v1\/rules\/\{ruleId\}\/approval-records\/\{approvalRecordId\}\/supplement-attachments \| rule:debug \| 3 \|/);
});

test('runHigressGatewaySmoke can append authorized read-only controller endpoint probes', async () => {
  const controllerMatrix = [
    {
      boundary: 'permission',
      method: 'GET',
      path: '/api/v1/audit-logs',
      permission: 'audit:read',
      controller: 'AuditController',
      handler: 'listAuditLogs',
    },
    {
      boundary: 'permission',
      method: 'POST',
      path: '/api/v1/rules/{ruleId}/runs',
      permission: 'rule:debug',
      controller: 'RuleController',
      handler: 'runRule',
    },
  ];
  const calls = [];
  const result = await runHigressGatewaySmoke({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    controllerMatrix,
    controllerEndpointAuthorizationReadOnlyAuthorizedCoverage: true,
    fetchImpl: async (url, options) => {
      const authorization = options.headers?.Authorization;
      calls.push({ url, method: options.method, authorization });
      return {
        status: url.endsWith('/audit-logs') && authorization ? 200 : 404,
        text: async () => JSON.stringify({ code: url.endsWith('/audit-logs') && authorization ? 200 : 404 }),
      };
    },
  });

  const names = result.results.map((entry) => entry.name);
  assert.ok(names.includes('controller-endpoint-0-authorized-readonly-through-higress'));
  assert.ok(!names.includes('controller-endpoint-1-authorized-readonly-through-higress'));
  assert.equal(result.results.find((entry) => entry.name === 'controller-endpoint-0-authorized-readonly-through-higress')?.passed, true);
  assert.equal(
    calls.filter((entry) => entry.url === 'http://127.0.0.1:28000/api/v1/audit-logs' && entry.authorization).length,
    1,
  );
  assert.equal(
    calls.filter((entry) => entry.url === 'http://127.0.0.1:28000/api/v1/rules/999999999/runs' && entry.authorization).length,
    0,
  );
});

test('runHigressGatewaySmoke fails authorized read-only probes on 5xx responses', async () => {
  const result = await runHigressGatewaySmoke({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    controllerMatrix: [
      {
        boundary: 'permission',
        method: 'GET',
        path: '/api/v1/reports/{reportId}/versions/diff',
        permission: 'report:read',
        controller: 'ReportController',
        handler: 'compareVersions',
      },
    ],
    controllerEndpointAuthorizationReadOnlyAuthorizedCoverage: true,
    fetchImpl: async () => ({
      status: 500,
      text: async () => JSON.stringify({ code: 500 }),
    }),
  });

  const probe = result.results.find((entry) =>
    entry.name === 'controller-endpoint-0-authorized-readonly-through-higress'
  );
  assert.equal(probe?.passed, false);
});

test('runHigressGatewaySmoke can append full generated endpoint 401 and 403 coverage', async () => {
  const controllerMatrix = [
    {
      boundary: 'permission',
      method: 'GET',
      path: '/api/v1/audit-logs',
      permission: 'audit:read',
      controller: 'AuditController',
      handler: 'listAuditLogs',
    },
    {
      boundary: 'permission',
      method: 'POST',
      path: '/api/v1/rules/{ruleId}/runs',
      permission: 'rule:debug',
      controller: 'RuleController',
      handler: 'runRule',
    },
  ];
  const calls = [];
  const result = await runHigressGatewaySmoke({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    controllerMatrix,
    controllerEndpointAuthorizationNegativeCoverage: true,
    fetchImpl: async (url, options) => {
      const authorization = options.headers?.Authorization;
      calls.push({ url, method: options.method, authorization });
      const status = authorization ? 403 : 401;
      return {
        status,
        text: async () => JSON.stringify({ code: status }),
      };
    },
  });

  const names = result.results.map((entry) => entry.name);
  assert.ok(names.includes('controller-endpoint-0-missing-token-through-higress'));
  assert.ok(names.includes('controller-endpoint-0-insufficient-permission-through-higress'));
  assert.ok(names.includes('controller-endpoint-1-missing-token-through-higress'));
  assert.ok(names.includes('controller-endpoint-1-insufficient-permission-through-higress'));
  assert.ok(!names.includes('controller-endpoint-0-authorized-through-higress'));
  assert.ok(!names.includes('controller-endpoint-1-authorized-through-higress'));
  assert.equal(
    calls.filter((entry) => entry.url === 'http://127.0.0.1:28000/api/v1/audit-logs').length,
    2,
  );
  assert.equal(
    calls.filter((entry) => entry.url === 'http://127.0.0.1:28000/api/v1/rules/999999999/runs').length,
    2,
  );
});

test('runHigressGatewaySmoke can append live 401 and 403 samples from controller endpoints', async () => {
  const controllerMatrix = [
    {
      boundary: 'permission',
      method: 'GET',
      path: '/api/v1/audit-logs',
      permission: 'audit:read',
      controller: 'AuditController',
      handler: 'listAuditLogs',
    },
    {
      boundary: 'permission',
      method: 'POST',
      path: '/api/v1/rules/{ruleId}/runs',
      permission: 'rule:debug',
      controller: 'RuleController',
      handler: 'runRule',
    },
  ];
  const calls = [];
  const result = await runHigressGatewaySmoke({
    gatewayBaseUrl: 'http://127.0.0.1:28000',
    controllerMatrix,
    controllerEndpointAuthorizationSampleLimit: 1,
    fetchImpl: async (url, options) => {
      const authorization = options.headers?.Authorization;
      calls.push({ url, method: options.method, authorization });
      const status = authorization ? 403 : 401;
      return {
        status,
        text: async () => JSON.stringify({ code: status }),
      };
    },
  });

  const names = result.results.map((entry) => entry.name);
  assert.ok(names.includes('controller-endpoint-0-missing-token-through-higress'));
  assert.ok(names.includes('controller-endpoint-0-insufficient-permission-through-higress'));
  assert.ok(!names.includes('controller-endpoint-0-authorized-through-higress'));
  assert.equal(
    calls.filter((entry) => entry.url === 'http://127.0.0.1:28000/api/v1/audit-logs').length,
    2,
  );
});
