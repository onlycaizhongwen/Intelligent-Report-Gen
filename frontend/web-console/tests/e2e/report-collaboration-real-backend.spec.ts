import { expect, request, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';
const postgresContainer = process.env.REAL_BACKEND_POSTGRES_CONTAINER ?? 'ir-postgres';
const postgresUser = process.env.REAL_BACKEND_POSTGRES_USER ?? 'report';
const postgresDb = process.env.REAL_BACKEND_POSTGRES_DB ?? 'intelligent_report';

test.describe('真实后端批注协作 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-COLLAB-002：浏览器提交正文选区批注后写入协作表', async ({ page }) => {
    const token = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read', 'report:export', 'report:share', 'user:manage'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` }
    });

    const assignee = await createEnabledAssignee(api);
    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: { topic: `真实后端批注协作验收 ${Date.now()}`, payload: { acceptance: 'UC-10' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };
    await completeReport(api, task.taskId);

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, token);

    await page.goto(`/reports/${task.reportId}`);
    const section = page.locator('[data-section-id]').first();
    await section.waitFor();
    await page.evaluate(() => {
      const paragraph = document.querySelector('[data-section-id] [data-section-content]');
      const textNode = paragraph?.firstChild;
      if (!paragraph || !textNode) throw new Error('section paragraph not found');
      const content = textNode.textContent ?? '';
      const start = content.indexOf('回款风险');
      if (start < 0) throw new Error(`selected text not found in section content: ${content}`);
      const range = document.createRange();
      range.setStart(textNode, start);
      range.setEnd(textNode, start + '回款风险'.length);
      const selection = window.getSelection();
      selection?.removeAllRanges();
      selection?.addRange(range);
      paragraph.dispatchEvent(new MouseEvent('mouseup', { bubbles: true }));
    });

    await expect(page.getByText('已选中：回款风险')).toBeVisible();
    await page.getByLabel('批注意见').fill('请财务同事核对真实回款风险');
    await page.getByLabel('指派用户 ID').fill(String(assignee.userId));
    await page.getByRole('button', { name: '提交批注任务' }).click();

    await expect(page.getByText(/批注已提交，任务：/)).toBeVisible();
    expect(queryCount('report_annotations', task.reportId)).toBeGreaterThan(0);
    expect(queryCount('collaboration_tasks', task.reportId)).toBeGreaterThan(0);
    expect(queryCount('collaboration_notifications', task.reportId)).toBeGreaterThan(0);
  });
});

async function createEnabledAssignee(api: Awaited<ReturnType<typeof request.newContext>>) {
  const response = await api.post(apiUrl('users'), {
    data: {
      username: `finance-${Date.now()}`,
      displayName: '财务协作用户',
      roles: ['analyst']
    }
  });
  expect(response.ok(), `create assignee failed: ${response.status()} ${await response.text()}`).toBeTruthy();
  return (await response.json()).data as { userId: number };
}

async function completeReport(api: Awaited<ReturnType<typeof request.newContext>>, taskId: number) {
  const response = await api.post(apiUrl(`reports/generation-tasks/${taskId}/completion`), {
    data: {
      sections: [
        {
          sectionId: 'summary',
          heading: '经营概览',
          content: '收入增长 12%，需要核对回款风险。',
          citations: ['doc-real-collab']
        }
      ],
      references: [{ sourceId: 'doc-real-collab', score: 0.91, title: '真实后端批注文档' }],
      modelInvocation: {
        provider: 'local-fallback',
        modelName: 'real-backend-e2e',
        status: 'succeeded',
        traceId: `trace-real-collab-${Date.now()}`,
        inputTokens: 10,
        outputTokens: 20,
        totalTokens: 30,
        latencyMs: 5,
        responseSummary: '收入增长 12%，需要核对回款风险。'
      }
    }
  });
  expect(response.ok(), `complete report failed: ${response.status()} ${await response.text()}`).toBeTruthy();
}

function queryCount(tableName: string, reportId: number) {
  const sql = `SELECT COUNT(*) FROM ${tableName} WHERE report_id = ${reportId};`;
  const output = execFileSync('docker', [
    'exec',
    postgresContainer,
    'psql',
    '-U',
    postgresUser,
    '-d',
    postgresDb,
    '-tAc',
    sql
  ], { encoding: 'utf8' });
  return Number(output.trim());
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
