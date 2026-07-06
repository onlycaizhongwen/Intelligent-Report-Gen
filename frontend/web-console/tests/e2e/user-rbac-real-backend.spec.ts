import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端用户与 RBAC E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-AUTH-001：批量导入用户并阻止禁用账号访问管理页', async ({ page }) => {
    const adminToken = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['user:manage', 'permission:read'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` }
    });
    const suffix = Date.now();

    const importResponse = await api.post(apiUrl('users/batch-import'), {
      data: {
        users: [
          {
            username: `rbac.analyst.${suffix}`,
            displayName: 'RBAC Analyst',
            roles: ['analyst']
          },
          {
            username: `rbac.viewer.${suffix}`,
            displayName: 'RBAC Viewer',
            roles: ['viewer']
          }
        ]
      }
    });
    expect(importResponse.ok(), `batch import failed: ${importResponse.status()} ${await importResponse.text()}`).toBeTruthy();
    const importResult = (await importResponse.json()).data as {
      imported: number;
      failed: number;
      items: Array<{ userId?: number; username: string; status: string }>;
    };
    expect(importResult.imported).toBe(2);
    expect(importResult.failed).toBe(0);
    const analyst = importResult.items.find((item) => item.username === `rbac.analyst.${suffix}`);
    expect(analyst?.userId).toBeTruthy();

    const disableResponse = await api.put(apiUrl(`users/${analyst!.userId}/status`), {
      data: { status: 'disabled' }
    });
    expect(disableResponse.ok(), `disable user failed: ${disableResponse.status()} ${await disableResponse.text()}`).toBeTruthy();
    const disabledUser = (await disableResponse.json()).data as { userId: number; status: string };
    expect(disabledUser.status).toBe('disabled');

    const disabledToken = await generateJwt({
      sub: String(disabledUser.userId),
      roles: ['ADMIN'],
      permissions: ['user:manage', 'permission:read'],
      status: 'disabled'
    });
    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, disabledToken);

    await page.goto('/admin/users');

    await expect(page.getByText('当前账号无权执行该操作')).toBeVisible();
    await expect(page.getByText(`rbac.analyst.${suffix}`)).toHaveCount(0);
  });
});

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
