import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端审计与历史 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-AUDIT-001：个人历史只返回当前用户，全局审计需要管理员权限', async () => {
    const suffix = Date.now();
    const userAId = 810001 + (suffix % 10000);
    const userBId = 820001 + (suffix % 10000);
    const userAToken = await generateJwt({
      sub: String(userAId),
      roles: ['analyst'],
      permissions: ['report:create', 'report:read'],
      status: 'enabled'
    });
    const userBToken = await generateJwt({
      sub: String(userBId),
      roles: ['analyst'],
      permissions: ['report:create', 'report:read'],
      status: 'enabled'
    });
    const adminToken = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['audit:read'],
      status: 'enabled'
    });

    const userAApi = await authedContext(userAToken);
    const userBApi = await authedContext(userBToken);
    const adminApi = await authedContext(adminToken);

    const topicA = `UC-12 history boundary A ${suffix}`;
    const topicB = `UC-12 history boundary B ${suffix}`;
    await createReportTask(userAApi, topicA);
    await createReportTask(userBApi, topicB);

    const userHistory = await userAApi.get(apiUrl('history'), { params: { page: '1', pageSize: '20' } });
    expect(userHistory.ok(), `history failed: ${userHistory.status()} ${await userHistory.text()}`).toBeTruthy();
    const historyItems = ((await userHistory.json()).data.items ?? []) as Array<Record<string, unknown>>;
    expect(historyItems.some((item) => item.actorUserId === userAId && includesDetail(item, topicA))).toBeTruthy();
    expect(historyItems.some((item) => item.actorUserId === userBId && includesDetail(item, topicB))).toBeFalsy();

    const forbiddenGlobal = await userAApi.get(apiUrl('audit-logs'), { params: { page: '1', pageSize: '20' } });
    expect(forbiddenGlobal.status()).toBe(403);

    const globalAudit = await adminApi.get(apiUrl('audit-logs'), { params: { page: '1', pageSize: '50' } });
    expect(globalAudit.ok(), `audit logs failed: ${globalAudit.status()} ${await globalAudit.text()}`).toBeTruthy();
    const auditItems = ((await globalAudit.json()).data.items ?? []) as Array<Record<string, unknown>>;
    expect(auditItems.some((item) => item.actorUserId === userAId && includesDetail(item, topicA))).toBeTruthy();
    expect(auditItems.some((item) => item.actorUserId === userBId && includesDetail(item, topicB))).toBeTruthy();
  });
});

async function authedContext(token: string) {
  return request.newContext({
    baseURL: apiBaseUrl,
    extraHTTPHeaders: { Authorization: `Bearer ${token}` }
  });
}

async function createReportTask(api: Awaited<ReturnType<typeof request.newContext>>, topic: string) {
  const response = await api.post(apiUrl('reports/generation-tasks'), {
    data: { topic, payload: { acceptance: 'UC-12' } }
  });
  expect(response.ok(), `create task failed: ${response.status()} ${await response.text()}`).toBeTruthy();
}

function includesDetail(item: Record<string, unknown>, value: string) {
  return JSON.stringify(item).includes(value);
}

function apiUrl(path: string) {
  return `${apiBaseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`;
}

async function generateJwt(payload: Record<string, unknown>) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const now = Math.floor(Date.now() / 1000);
  const body = { iat: now, exp: now + 7200, ...payload };
  const unsigned = `${base64UrlJson(header)}.${base64UrlJson(body)}`;
  const signature = await hmacSha256(unsigned, jwtSecret);
  return `${unsigned}.${signature}`;
}

function base64UrlJson(value: unknown) {
  return base64Url(new TextEncoder().encode(JSON.stringify(value)));
}

async function hmacSha256(value: string, secret: string) {
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(value));
  return base64Url(new Uint8Array(signature));
}

function base64Url(bytes: Uint8Array) {
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
