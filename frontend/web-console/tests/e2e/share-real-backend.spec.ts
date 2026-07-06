import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端分享访问 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-COLLAB-001：外部用户通过分享密码只读访问报告并获取受控下载 URL', async ({ page }) => {
    const adminToken = await generateJwt({
      sub: '1',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read', 'report:export', 'report:share'],
      status: 'enabled'
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${adminToken}` }
    });
    const suffix = Date.now();

    const taskResponse = await api.post(apiUrl('reports/generation-tasks'), {
      data: { topic: `真实后端分享验收 ${suffix}`, payload: { acceptance: 'UC-09' } }
    });
    expect(taskResponse.ok(), `create task failed: ${taskResponse.status()} ${await taskResponse.text()}`).toBeTruthy();
    const task = (await taskResponse.json()).data as { taskId: number; reportId: number };

    await completeReport(api, task.taskId);

    const exportResponse = await api.post(apiUrl(`reports/${task.reportId}/exports`), {
      data: { format: 'markdown', templateId: 'enterprise-default' }
    });
    expect(exportResponse.ok(), `create export failed: ${exportResponse.status()} ${await exportResponse.text()}`).toBeTruthy();
    const exported = (await exportResponse.json()).data as { exportFileId: number; fileName: string };

    const shareResponse = await api.post(apiUrl(`reports/${task.reportId}/share-links`), {
      data: {
        password: 'ExternalPass#1',
        allowDownload: true,
        expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
      }
    });
    expect(shareResponse.ok(), `create share failed: ${shareResponse.status()} ${await shareResponse.text()}`).toBeTruthy();
    const share = (await shareResponse.json()).data as { shareToken: string; shareUrl: string; passwordRequired: boolean };
    expect(share.passwordRequired).toBe(true);

    const wrongPassword = await api.post(apiUrl(`share-links/${share.shareToken}/report`), {
      data: { password: 'wrong', visitor: 'external@example.com' }
    });
    expect(wrongPassword.status()).toBe(403);
    const wrongPasswordBody = await wrongPassword.json() as { code: number; message: string };
    expect(wrongPasswordBody.code).toBe(403);
    expect(wrongPasswordBody.message).toBe('当前账号无权执行该操作');

    await page.goto(`/share/${share.shareToken}`);
    await expect(page.getByRole('heading', { name: '报告分享访问' })).toBeVisible();
    await page.getByLabel(/密码|访问码/).fill('ExternalPass#1');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByRole('heading', { name: `真实后端分享验收 ${suffix}` })).toBeVisible();
    await expect(page.getByText('收入增长 12%，但应收账款风险上升。')).toBeVisible();
    await expect(page.getByText('doc-real-share')).toBeVisible();
    await expect(page.getByRole('button', { name: new RegExp(`下载 ${escapeRegExp(exported.fileName)}`) })).toBeVisible();

    const downloadResponse = await api.post(apiUrl(`share-links/${share.shareToken}/exports/${exported.exportFileId}/download-url`), {
      data: { password: 'ExternalPass#1', visitor: 'external@example.com' }
    });
    expect(downloadResponse.ok(), `share download failed: ${downloadResponse.status()} ${await downloadResponse.text()}`).toBeTruthy();
    const download = (await downloadResponse.json()).data as {
      exportFileId: number;
      downloadPolicy: string;
      downloadUrl: string;
      fileName: string;
    };
    expect(download.exportFileId).toBe(exported.exportFileId);
    expect(download.fileName).toBe(exported.fileName);
    expect(download.downloadPolicy).toBe('share_presigned_url');
    expect(download.downloadUrl).toMatch(/^https?:\/\//);
  });
});

async function completeReport(api: Awaited<ReturnType<typeof request.newContext>>, taskId: number) {
  const response = await api.post(apiUrl(`reports/generation-tasks/${taskId}/completion`), {
    data: {
      sections: [
        {
          sectionId: 'summary',
          heading: '经营摘要',
          content: '收入增长 12%，但应收账款风险上升。',
          citations: ['doc-real-share']
        }
      ],
      references: [{ sourceId: 'doc-real-share', score: 0.93, title: '真实后端分享验收文档' }],
      modelInvocation: {
        provider: 'local-fallback',
        modelName: 'real-backend-e2e',
        status: 'succeeded',
        traceId: `trace-real-share-${Date.now()}`,
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

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
