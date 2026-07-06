import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端报告版本 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-REPORT-005：浏览器连接 Java/PostgreSQL 查看差异并回滚', async ({ page }) => {
    const token = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read', 'report:export', 'report:share'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });

    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: { topic: `真实后端版本回滚验收 ${Date.now()}`, payload: { acceptance: 'UC-14' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };

    await completeVersion(api, task.taskId, '收入增长 8%。', 'trace-real-e2e-v1');
    await completeVersion(api, task.taskId, '收入增长 12%。', 'trace-real-e2e-v2');

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto(`/reports/${task.reportId}`);
    await expect(page.getByRole('heading', { name: '报告详情' })).toBeVisible();
    await expect(page.locator('[data-section-content]').getByText('收入增长 12%。')).toBeVisible();

    await page.getByRole('button', { name: '查看差异' }).click();
    await expect(page.getByText('版本差异')).toBeVisible();
    await expect(page.getByText(/修改 1/)).toBeVisible();
    await expect(page.getByText('修改：经营概览')).toBeVisible();

    await page.getByRole('button', { name: '回滚版本 v1' }).click();
    await expect(page.getByText(/已回滚并生成新版本/)).toBeVisible();
    await expect(page.locator('[data-section-content]').getByText('收入增长 8%。')).toBeVisible();
  });
});

async function completeVersion(api: Awaited<ReturnType<typeof request.newContext>>, taskId: number, content: string, traceId: string) {
  const response = await api.post(apiUrl(`reports/generation-tasks/${taskId}/completion`), {
    data: {
      sections: [
        {
          heading: '经营概览',
          content,
          citations: ['doc-real-e2e']
        }
      ],
      references: [{ sourceId: 'doc-real-e2e', score: 0.91, title: '真实后端验收文档' }],
      modelInvocation: {
        provider: 'local-fallback',
        modelName: 'real-backend-e2e',
        status: 'succeeded',
        traceId,
        inputTokens: 10,
        outputTokens: 20,
        totalTokens: 30,
        latencyMs: 5,
        responseSummary: content
      }
    }
  });
  expect(response.ok(), `complete version failed: ${response.status()} ${await response.text()}`).toBeTruthy();
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
