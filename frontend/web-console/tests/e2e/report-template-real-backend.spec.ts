import { expect, request, test } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';

test.describe('Real backend report template filling E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('UC-02: browser submits template fields and exposes persisted template snapshot evidence', async ({ page }) => {
    const token = await generateJwt({
      sub: '9302',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    try {
      const templatesResponse = await api.get(apiUrl('report-templates'));
      expect(templatesResponse.ok(), `list templates failed: ${templatesResponse.status()} ${await templatesResponse.text()}`).toBeTruthy();
      const templates = (await templatesResponse.json()).data as Array<{
        templateId: string;
        fields: Array<{ fieldKey: string; type: string; options?: string[]; defaultValue?: string | null }>;
      }>;
      const template = templates.find((item) => item.templateId === 'enterprise-quarterly') ?? templates[0];
      expect(template, 'at least one active report template is required').toBeTruthy();

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/reports/create');
      await page.getByRole('tab', { name: '模板填报' }).click();

      const expectedPayload: Record<string, string> = {};
      for (const field of template.fields) {
        const value = fieldValue(field.fieldKey, field.type, field.options);
        expectedPayload[field.fieldKey] = value;
        const locator = page.getByTestId(`template-field-${field.fieldKey}`);
        if (field.type === 'select') {
          await locator.click();
          await page.getByRole('option', { name: value }).click();
        } else {
          await locator.fill(value);
        }
      }

      const createResponsePromise = page.waitForResponse((response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/v1/reports/template-generation-tasks') &&
        response.status() === 200
      );

      await page.getByRole('button', { name: '按模板生成大纲' }).click();

      const createEnvelope = await createResponsePromise.then((response) => response.json()) as {
        data: {
          taskId: number | string;
          reportId: number | string;
          status: string;
          templateSnapshot: {
            templateId: string;
            version: string;
            parameters: Record<string, string>;
          };
        };
      };

      await expect(page.getByText(`模板任务 ${createEnvelope.data.taskId}`)).toBeVisible();
      await expect(page.getByText(`报告 ${createEnvelope.data.reportId}`)).toBeVisible();
      await expect(page.getByText(`模板 ${createEnvelope.data.templateSnapshot.templateId}`)).toBeVisible();
      await expect(page.getByText(`版本 ${createEnvelope.data.templateSnapshot.version}`)).toBeVisible();

      for (const [key, value] of Object.entries(expectedPayload)) {
        expect(createEnvelope.data.templateSnapshot.parameters[key]).toBe(value);
        await expect(page.getByText(`${key}: ${value}`)).toBeVisible();
      }

      const reportResponse = await api.get(apiUrl(`reports/${createEnvelope.data.reportId}`));
      expect(reportResponse.ok(), `report detail failed: ${reportResponse.status()} ${await reportResponse.text()}`).toBeTruthy();
      const report = (await reportResponse.json()).data as { title: string; status: string };
      expect(report.title).toContain(template.templateId);
      expect(report.status).toBeTruthy();
    } finally {
      await api.dispose();
    }
  });

  test('UC-02: browser continues from template task to real outline confirmation', async ({ page }) => {
    const token = await generateJwt({
      sub: '9302',
      roles: ['ADMIN'],
      permissions: ['report:create', 'report:read'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    try {
      const templatesResponse = await api.get(apiUrl('report-templates'));
      expect(templatesResponse.ok(), `list templates failed: ${templatesResponse.status()} ${await templatesResponse.text()}`).toBeTruthy();
      const templates = (await templatesResponse.json()).data as Array<{
        templateId: string;
        fields: Array<{ fieldKey: string; type: string; options?: string[]; defaultValue?: string | null }>;
      }>;
      const template = templates.find((item) => item.templateId === 'enterprise-quarterly') ?? templates[0];
      expect(template, 'at least one active report template is required').toBeTruthy();

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/reports/create');
      await page.getByRole('tab', { name: '模板填报' }).click();

      for (const field of template.fields) {
        const value = fieldValue(field.fieldKey, field.type, field.options);
        const locator = page.getByTestId(`template-field-${field.fieldKey}`);
        if (field.type === 'select') {
          await locator.click();
          await page.getByRole('option', { name: value }).click();
        } else {
          await locator.fill(value);
        }
      }

      const createResponsePromise = page.waitForResponse((response) =>
        response.request().method() === 'POST' &&
        response.url().includes('/api/v1/reports/template-generation-tasks') &&
        response.status() === 200
      );
      await page.getByRole('button', { name: '按模板生成大纲' }).click();
      const createEnvelope = await createResponsePromise.then((response) => response.json()) as {
        data: { taskId: number | string; status: string };
      };

      await page.getByRole('link', { name: '查看大纲' }).click();
      await expect(page).toHaveURL(new RegExp(`/reports/${createEnvelope.data.taskId}/outline$`));

      const confirmResponsePromise = page.waitForResponse((response) =>
        response.request().method() === 'PUT' &&
        response.url().includes(`/api/v1/reports/generation-tasks/${createEnvelope.data.taskId}/outline`) &&
        response.status() === 200
      );
      await page.getByRole('button', { name: '确认并生成正文' }).click();
      const confirmEnvelope = await confirmResponsePromise.then((response) => response.json()) as {
        data: { taskId: number | string; confirmed: boolean; nextStage: string };
      };

      expect(String(confirmEnvelope.data.taskId)).toBe(String(createEnvelope.data.taskId));
      expect(confirmEnvelope.data.confirmed).toBe(true);
      expect(confirmEnvelope.data.nextStage).toBe('retrieval');
      await expect(page.getByText(`大纲已确认：${createEnvelope.data.taskId}`)).toBeVisible();
      await expect(page.getByText('下一阶段：retrieval')).toBeVisible();
    } finally {
      await api.dispose();
    }
  });
});

function fieldValue(fieldKey: string, type: string, options: string[] = []) {
  if (type === 'select' && options.length > 0) {
    return options[options.length - 1];
  }
  const values: Record<string, string> = {
    period: `2026Q2-${Date.now()}`,
    scope: 'East Region Acceptance',
    focus: 'Revenue growth and receivables risk',
  };
  return values[fieldKey] ?? `${fieldKey}-acceptance`;
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
