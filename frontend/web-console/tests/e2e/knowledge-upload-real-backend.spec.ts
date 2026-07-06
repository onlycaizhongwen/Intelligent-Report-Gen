import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('真实后端知识文档上传解析 E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-KB-002：浏览器上传扫描 PDF 后可查询到真实 processed 状态', async ({ page }) => {
    const ownerToken = await generateJwt({
      sub: '2114',
      roles: ['ADMIN'],
      permissions: ['knowledge:upload', 'knowledge:manage', 'report:read'],
      status: 'enabled'
    });

    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${ownerToken}` }
    });

    const knowledgeBase = await api.get(apiUrl('knowledge-bases'), { params: { page: '1', pageSize: '20' } });
    expect(knowledgeBase.ok(), `list knowledge bases failed: ${knowledgeBase.status()} ${await knowledgeBase.text()}`).toBeTruthy();
    const knowledgeBaseItems = ((await knowledgeBase.json()).data.items ?? []) as Array<Record<string, unknown>>;
    expect(knowledgeBaseItems.length).toBeGreaterThan(0);

    await page.addInitScript((accessToken) => {
      window.localStorage.setItem('accessToken', accessToken);
    }, ownerToken);

    await page.goto('/knowledge/upload');
    await expect(page.getByRole('heading', { name: '文档上传' })).toBeVisible();

    const uploadResponsePromise = page.waitForResponse((response) =>
      response.request().method() === 'POST' && response.url().includes('/api/v1/documents/upload')
    );
    await page.setInputFiles('input[type="file"]', '../../tests/e2e/fixtures/minimal-scan-smoke.pdf');
    const uploadResponse = await uploadResponsePromise;
    expect(uploadResponse.ok(), `upload failed: ${uploadResponse.status()} ${await uploadResponse.text()}`).toBeTruthy();
    const uploadPayload = await uploadResponse.json();
    const documentId = uploadPayload?.data?.documentId;
    expect(documentId).toBeTruthy();

    await expect(page.getByText(/minimal-scan-smoke\.pdf pending|minimal-scan-smoke\.pdf processed/)).toBeVisible();
    await expect(page.getByText('minimal-scan-smoke.pdf processed')).toBeVisible({ timeout: 15_000 });

    await expect
      .poll(
        async () => {
          const response = await api.get(apiUrl(`/documents/${documentId}`));
          expect(response.ok(), `document status failed: ${response.status()} ${await response.text()}`).toBeTruthy();
          return (await response.json()).data as Record<string, unknown>;
        },
        {
          timeout: 15_000,
          intervals: [500, 1_000, 1_500]
        }
      )
      .toMatchObject({
        documentId,
        filename: 'minimal-scan-smoke.pdf',
        parseStatus: 'processed'
      });
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
