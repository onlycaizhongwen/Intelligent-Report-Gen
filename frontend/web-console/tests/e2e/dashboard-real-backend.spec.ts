import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端工作台 Dashboard E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-DASH-001：浏览器通过真实后端展示工作台聚合与最近活动', async ({ page }) => {
    const token = await generateJwt({
      sub: '9201',
      roles: ['ADMIN'],
      permissions: [
        'dashboard:read',
        'audit:read',
        'report:create',
        'report:read',
        'knowledge:manage',
        'datasource:manage',
      ],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    const knowledgeBaseName = `Dashboard KB ${Date.now()}`;
    const knowledgeBaseResponse = await api.post(apiUrl('knowledge-bases'), {
      data: { name: knowledgeBaseName },
    });
    expect(
      knowledgeBaseResponse.ok(),
      `create knowledge base failed: ${knowledgeBaseResponse.status()} ${await knowledgeBaseResponse.text()}`,
    ).toBeTruthy();
    const knowledgeBase = (await knowledgeBaseResponse.json()).data as { knowledgeBaseId: number };

    const knowledgeItemResponse = await api.post(apiUrl('knowledge-items'), {
      data: {
        knowledgeBaseId: knowledgeBase.knowledgeBaseId,
        title: `Dashboard insight ${Date.now()}`,
        content: 'Dashboard real backend smoke item',
      },
    });
    expect(
      knowledgeItemResponse.ok(),
      `create knowledge item failed: ${knowledgeItemResponse.status()} ${await knowledgeItemResponse.text()}`,
    ).toBeTruthy();

    const dataSourceResponse = await api.post(apiUrl('data-sources'), {
      data: {
        name: `Dashboard datasource ${Date.now()}`,
        sourceType: 'postgresql',
        endpoint: 'jdbc:postgresql://localhost:5432/dashboard-smoke',
        knowledgeBaseId: knowledgeBase.knowledgeBaseId,
        syncQuery: 'select id, title, content from dashboard_smoke',
        cursorColumn: 'id',
      },
    });
    expect(
      dataSourceResponse.ok(),
      `save data source failed: ${dataSourceResponse.status()} ${await dataSourceResponse.text()}`,
    ).toBeTruthy();

    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: {
        topic: `Dashboard smoke report ${Date.now()}`,
        payload: { acceptance: 'UC-13' },
      },
    });
    expect(taskResponse.ok(), `create report task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number };

    const completeResponse = await api.post(apiUrl(`reports/generation-tasks/${task.taskId}/completion`), {
      data: {
        sections: [
          {
            sectionId: 'summary',
            heading: '经营概览',
            content: 'Dashboard smoke completed report content.',
            citations: [],
          },
        ],
        references: [],
        modelInvocation: {
          provider: 'local-fallback',
          modelName: 'real-backend-e2e',
          status: 'succeeded',
          traceId: `dashboard-real-${Date.now()}`,
          inputTokens: 8,
          outputTokens: 16,
          totalTokens: 24,
          latencyMs: 5,
          responseSummary: 'Dashboard smoke completed report content.',
        },
      },
    });
    expect(
      completeResponse.ok(),
      `complete report failed: ${completeResponse.status()} ${await completeResponse.text()}`,
    ).toBeTruthy();

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto('/dashboard');
    await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
    await expect(page.getByText('全局指标')).toBeVisible();

    await expect(metricCard(page, '报告产出')).toHaveNumericValueGreaterThan(0);
    await expect(metricCard(page, '知识条目')).toHaveNumericValueGreaterThan(0);
    await expect(metricCard(page, '活跃数据源')).toHaveNumericValueGreaterThan(0);

    const recentActivityTable = page.locator('.panel').filter({
      has: page.getByRole('heading', { name: '最近活动' }),
    });
    await expect(recentActivityTable.getByText('knowledge_data_source_saved').first()).toBeVisible();
    await expect(recentActivityTable.getByText('report_generation_task_created').first()).toBeVisible();

    await page.getByText('近 30 天').click();
    await expect(page.getByText('全局指标')).toBeVisible();
    await expect(metricCard(page, '报告产出')).toHaveNumericValueGreaterThan(0);
  });
});

function metricCard(page: Parameters<typeof expect>[0], label: string) {
  // @ts-expect-error page locator type is sufficient here
  return page.locator('.metric-card').filter({ hasText: label }).locator('strong');
}

expect.extend({
  async toHaveNumericValueGreaterThan(locator, expected) {
    const text = await locator.textContent();
    const actual = Number.parseInt((text ?? '').replace(/[^\d-]/g, ''), 10);
    const pass = Number.isFinite(actual) && actual > expected;
    return {
      pass,
      message: () => `expected ${text ?? '<empty>'} to be a numeric value greater than ${expected}`,
    };
  },
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
