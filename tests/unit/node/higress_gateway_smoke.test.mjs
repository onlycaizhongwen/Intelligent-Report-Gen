import test from 'node:test';
import assert from 'node:assert/strict';

import {
  buildHigressDataSourceSecurityChecks,
  buildGatewaySecurityJwt,
  buildHigressEndpointSecurityChecks,
  buildHigressGatewaySmokeChecks,
  classifyGatewayResponse,
} from '../../../scripts/higress-gateway-smoke-lib.mjs';

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
