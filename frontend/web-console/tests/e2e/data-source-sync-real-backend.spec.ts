import { createServer } from 'node:http';

import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端数据源同步 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-KB-003：浏览器保存 API 数据源并触发真实同步日志刷新', async ({ page }) => {
    const serverState = {
      methods: [] as string[],
      paths: [] as string[],
      apiKeyHeaders: [] as Array<string | undefined>,
      tenantHeaders: [] as Array<string | undefined>,
      bodies: [] as string[],
    };
    const apiFixtureServer = await startApiFixtureServer(serverState);

    try {
      const token = await generateJwt({
        sub: '9202',
        roles: ['ADMIN'],
        permissions: ['datasource:manage', 'knowledge:manage'],
        status: 'enabled',
      });
      const api = await request.newContext({
        baseURL: apiBaseUrl,
        extraHTTPHeaders: { Authorization: `Bearer ${token}` },
      });

      const knowledgeBaseResponse = await api.post(apiUrl('knowledge-bases'), {
        data: { name: `Datasource KB ${Date.now()}` },
      });
      expect(
        knowledgeBaseResponse.ok(),
        `create knowledge base failed: ${knowledgeBaseResponse.status()} ${await knowledgeBaseResponse.text()}`,
      ).toBeTruthy();
      const knowledgeBase = (await knowledgeBaseResponse.json()).data as { knowledgeBaseId: number };

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/knowledge/data-sources');
      await expect(page.getByRole('heading', { name: '数据源配置' })).toBeVisible();

      await page.getByLabel('数据源名称').fill(`ERP API ${Date.now()}`);
      await page.getByRole('combobox', { name: '数据源类型' }).click({ force: true });
      await page.getByRole('option', { name: 'HTTP API' }).click();
      await page.getByLabel('连接地址').fill(`${apiFixtureServer.backendOrigin}/reports`);
      await page.getByLabel('用户名或 Token 标识').fill('erp_reader');
      await page.getByLabel('密码或访问密钥').fill('api-secret');
      await page.getByLabel('目标知识库 ID').fill(String(knowledgeBase.knowledgeBaseId));
      await page.getByLabel('API 行路径').fill('data.items');
      await page.getByLabel('标题字段').fill('headline');
      await page.getByLabel('内容字段').fill('body');
      await page.getByLabel('增量游标列').fill('id');
      await page.getByRole('combobox', { name: '请求方法' }).click({ force: true });
      await page.getByRole('option', { name: 'POST', exact: true }).click();
      await page.getByRole('combobox', { name: '认证方式' }).click({ force: true });
      await page.getByRole('option', { name: 'API Key Header' }).click();
      await page.getByLabel('API Key Header').fill('X-API-Key');
      await page.getByLabel('自定义 Header').fill('X-Tenant: finance');
      await page.getByLabel('POST Body').fill('{"period":"2026Q1"}');
      await page.getByLabel('页码参数').fill('page');
      await page.getByRole('spinbutton', { name: '起始页码', exact: true }).fill('1');
      await page.getByLabel('每页数量参数').fill('pageSize');
      await page.getByRole('spinbutton', { name: '每页数量', exact: true }).fill('1');
      await page.getByRole('spinbutton', { name: '最大页数', exact: true }).fill('2');
      await page.getByText('启用', { exact: true }).click();
      await page.getByRole('spinbutton', { name: '同步间隔秒', exact: true }).fill('300');
      await page.getByRole('spinbutton', { name: '最大失败重试次数', exact: true }).fill('2');

      await page.getByRole('button', { name: '保存数据源' }).click();
      await expect(page.getByText('数据源已保存，敏感凭据不会在页面回显')).toBeVisible();

      await page.getByRole('button', { name: '测试连接' }).click();
      await expect(page.getByText(/connection (un)?available/)).toBeVisible();

      await page.getByRole('button', { name: '启动同步' }).click();
      await expect(page.getByText('sync completed')).toBeVisible();
      const syncRow = page.getByRole('row').filter({ hasText: 'manual' }).filter({ hasText: 'sync completed' });
      await expect(syncRow).toContainText('succeeded');
      await expect(syncRow).toContainText('2');

      expect(serverState.methods).toContain('POST');
      expect(serverState.paths).toContain('/reports?page=1&pageSize=1');
      expect(serverState.paths).toContain('/reports?page=2&pageSize=1');
      expect(serverState.apiKeyHeaders).toContain('api-secret');
      expect(serverState.tenantHeaders).toContain('finance');
      expect(serverState.bodies).toContain('{"period":"2026Q1"}');
    } finally {
      await apiFixtureServer.close();
    }
  });
});

async function startApiFixtureServer(state: {
  methods: string[];
  paths: string[];
  apiKeyHeaders: Array<string | undefined>;
  tenantHeaders: Array<string | undefined>;
  bodies: string[];
}) {
  const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? '/', 'http://127.0.0.1');
    const bodyChunks: Uint8Array[] = [];
    for await (const chunk of req) {
      bodyChunks.push(typeof chunk === 'string' ? Buffer.from(chunk) : chunk);
    }
    const body = Buffer.concat(bodyChunks).toString('utf8');

    state.methods.push(req.method ?? '');
    state.paths.push(`${url.pathname}${url.search}`);
    state.apiKeyHeaders.push(req.headers['x-api-key']?.toString());
    state.tenantHeaders.push(req.headers['x-tenant']?.toString());
    state.bodies.push(body);

    const page = url.searchParams.get('page') ?? '1';
    const payload = page === '2'
      ? { data: { items: [{ id: 2, headline: 'ERP cash', body: 'Cash flow improved' }] } }
      : { data: { items: [{ id: 1, headline: 'ERP revenue', body: 'Revenue improved' }] } };

    res.writeHead(200, { 'content-type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(payload));
  });

  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', () => resolve()));
  const address = server.address();
  if (!address || typeof address === 'string') {
    throw new Error('api fixture server address not available');
  }

  return {
    origin: `http://127.0.0.1:${address.port}`,
    backendOrigin: `http://host.docker.internal:${address.port}`,
    close: () => new Promise<void>((resolve, reject) => server.close((error) => (error ? reject(error) : resolve()))),
  };
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
    ['sign'],
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
