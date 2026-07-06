import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端报告引用溯源 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-REPORT-003：浏览器可从 Java/PostgreSQL 报告详情点击引用并查看来源快照评分', async ({ page }) => {
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
      data: { topic: `真实后端引用溯源验收 ${Date.now()}`, payload: { acceptance: 'REQ-REPORT-003' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };
    await completeReportWithCitation(api, task.taskId);

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto(`/reports/${task.reportId}`);
    await expect(page.getByRole('heading', { name: '报告详情' })).toBeVisible();
    await expect(page.getByText('收入增长 12%，但应收账款风险上升。')).toBeVisible();

    await page.getByRole('button', { name: '引用 77' }).click();
    await expect(page.getByText('华东销售数据集')).toBeVisible();
    await expect(page.getByText('收入同比增长 12%，应收账款周转天数升高。')).toBeVisible();
    await expect(page.getByText('可信度 92%')).toBeVisible();
    await expect(page.getByText('引用质量 88%')).toBeVisible();
    await expect(page.locator('.anchor-text').getByText('收入增长 12%', { exact: true })).toBeVisible();
  });
});

async function completeReportWithCitation(api: Awaited<ReturnType<typeof request.newContext>>, taskId: number) {
  const citation = {
    referenceId: 77,
    sourceTitle: '华东销售数据集',
    sourceType: 'knowledge_document',
    snapshot: '收入同比增长 12%，应收账款周转天数升高。',
    score: { credibility: 0.92, citationQuality: 0.88 },
    anchor: { sectionNo: 1, heading: '经营概览', text: '收入增长 12%' }
  };
  const response = await api.post(apiUrl(`reports/generation-tasks/${taskId}/completion`), {
    data: {
      sections: [
        {
          sectionId: 'summary',
          heading: '经营概览',
          content: '收入增长 12%，但应收账款风险上升。',
          citations: [citation]
        }
      ],
      references: [citation],
      modelInvocation: {
        provider: 'local-fallback',
        modelName: 'real-backend-e2e',
        status: 'succeeded',
        traceId: `trace-real-citation-${Date.now()}`,
        inputTokens: 10,
        outputTokens: 20,
        totalTokens: 30,
        latencyMs: 5,
        responseSummary: '收入增长 12%，但应收账款风险上升。'
      }
    }
  });
  expect(response.ok(), `complete report failed: ${response.status()} ${await response.text()}`).toBeTruthy();
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
