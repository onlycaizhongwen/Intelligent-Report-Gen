import { createServer, type Server } from 'node:http';
import { AddressInfo } from 'node:net';

import { expect, request, test, type Page } from '@playwright/test';

const runRealBackend = process.env.RUN_REAL_BACKEND_E2E === 'true';
const apiBaseUrl = process.env.REAL_BACKEND_API_BASE_URL ?? 'http://127.0.0.1:18082/api/v1';
const jwtSecret = process.env.REAL_BACKEND_JWT_SECRET ?? 'local-dev-secret-change-me-32-bytes-minimum';
const webhookCallbackHost = process.env.REAL_BACKEND_WEBHOOK_HOST ?? 'host.docker.internal';

test.describe('Real backend rule runtime E2E', () => {
  test.skip(!runRealBackend, 'set RUN_REAL_BACKEND_E2E=true to run against local Java/PostgreSQL backend');

  test('REQ-RULE-001: browser runs a published rule and shows real webhook action ledger', async ({ page }) => {
    const webhook = await startWebhookServer();
    const token = await generateJwt({
      sub: '9211',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    try {
      const seed = Date.now();
      const ruleName = `Rule runtime smoke ${seed}`;
      const ruleId = await createPublishedRule(api, ruleName, createRuntimeDefinition(webhook.url));

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/rules');
      await expect(page.getByRole('heading', { name: '规则编排' })).toBeVisible();
      const ruleCard = page.locator('.rule-item').filter({ hasText: ruleName });
      await expect(ruleCard).toBeVisible();
      await ruleCard.click();

      await fillSampleJson(page, {
        invoices: [
          { invoiceNo: 'A001', overdueAmount: 4000 },
          { invoiceNo: 'A002', overdueAmount: 7000 },
        ],
      });
      await clickProductionRun(page);

      await expect(page.getByText('生产运行：命中')).toBeVisible();
      await expect(page.getByText('runType: production')).toBeVisible();
      await expect(page.getByText(new RegExp(`#\\d+.*production.*succeeded`))).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Webhook action ledger' })).toBeVisible();
      await expect(page.locator('.ledger-row').filter({ hasText: 'notifyFinance' })).toBeVisible();
      await expect(page.locator('.ledger-row').filter({ hasText: webhook.url })).toBeVisible();
      await expect(page.getByText('Succeeded 1')).toBeVisible();
      await expect(page.getByText('Success rate 100%')).toBeVisible();

      expect(webhook.requests).toHaveLength(1);
      expect(webhook.requests[0].headers['idempotency-key']).toMatch(
        new RegExp(`^rule-${ruleId}-run-\\d+-node-notifyFinance$`),
      );
      expect(webhook.requests[0].body).toMatchObject({
        eventType: 'rule_runtime_smoke',
        ruleId,
        ruleName,
        nodeId: 'notifyFinance',
      });
    } finally {
      await api.dispose();
      await webhook.close();
    }
  });

  test('REQ-RULE-001: browser shows failed webhook compensation and supports batch ignore', async ({ page }) => {
    const webhook = await startWebhookServer({ statusCode: 503, responseBody: 'erp unavailable' });
    const token = await generateJwt({
      sub: '9212',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    try {
      const seed = Date.now();
      const ruleName = `Rule runtime compensation ${seed}`;
      const ruleId = await createPublishedRule(api, ruleName, createRuntimeDefinition(webhook.url));

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/rules');
      const ruleCard = page.locator('.rule-item').filter({ hasText: ruleName });
      await expect(ruleCard).toBeVisible();
      await ruleCard.click();

      await fillSampleJson(page, {
        invoices: [
          { invoiceNo: 'A101', overdueAmount: 6000 },
          { invoiceNo: 'A102', overdueAmount: 8000 },
        ],
      });
      await clickProductionRun(page);

      await expect(page.getByText(/webhook action failed/)).toBeVisible();
      await expect(page.getByRole('heading', { name: 'Webhook action ledger' })).toBeVisible();
      const failedRow = page.locator('.ledger-row').filter({ hasText: 'notifyFinance' }).first();
      await expect(failedRow).toBeVisible();
      await expect(failedRow).toContainText('pending_retry');
      await expect(failedRow).toContainText('erp unavailable');
      await expect(page.getByText('Pending 1')).toBeVisible();

      await failedRow.locator('.el-checkbox').click();
      await page.getByRole('button', { name: 'Batch ignore' }).click();

      await expect(page.locator('.ledger-row').filter({ hasText: 'compensation_ignored' })).toBeVisible();
      await expect(page.getByText('Ignored 1')).toBeVisible();

      const executionsResponse = await api.get(apiUrl(`rules/${ruleId}/action-executions?page=1&pageSize=10`));
      expect(executionsResponse.ok(), `list executions failed: ${executionsResponse.status()} ${await executionsResponse.text()}`).toBeTruthy();
      const executions = (await executionsResponse.json()).data.items as Array<{ status: string; errorMessage?: string }>;
      expect(executions.some((item) => item.status === 'compensation_ignored')).toBeTruthy();
      expect(executions.some((item) => item.errorMessage?.includes('erp unavailable'))).toBeTruthy();
      expect(webhook.requests.length).toBeGreaterThanOrEqual(1);
    } finally {
      await api.dispose();
      await webhook.close();
    }
  });

  test('REQ-RULE-001: browser retries failed webhook compensation successfully', async ({ page }) => {
    const webhook = await startWebhookServer({
      responses: [
        { statusCode: 503, responseBody: 'erp unavailable' },
        { statusCode: 204, responseBody: '' },
      ],
    });
    const token = await generateJwt({
      sub: '9213',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    try {
      const seed = Date.now();
      const ruleName = `Rule runtime retry compensation ${seed}`;
      const ruleId = await createPublishedRule(api, ruleName, createRuntimeDefinition(webhook.url, { maxAsyncReplayAttempts: 0 }));

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/rules');
      const ruleCard = page.locator('.rule-item').filter({ hasText: ruleName });
      await expect(ruleCard).toBeVisible();
      await ruleCard.click();

      await fillSampleJson(page, {
        invoices: [
          { invoiceNo: 'A201', overdueAmount: 7000 },
          { invoiceNo: 'A202', overdueAmount: 9000 },
        ],
      });
      await clickProductionRun(page);

      await expect(page.getByText(/webhook action failed/)).toBeVisible();
      const failedRow = page.locator('.ledger-row').filter({ hasText: 'notifyFinance' }).first();
      await expect(failedRow).toBeVisible();
      await expect(failedRow).toContainText('pending_retry');
      await expect(failedRow).toContainText('erp unavailable');
      const retryButtonName = await failedRow.getByRole('button', { name: /^Retry \d+$/ }).innerText();
      const failedExecutionId = Number(retryButtonName.replace('Retry ', ''));

      await failedRow.getByRole('button', { name: retryButtonName }).click();

      await expect(page.locator('.ledger-row').filter({ hasText: 'succeeded' }).filter({ hasText: `Source action #${failedExecutionId}` })).toHaveCount(1);
      await expect(page.getByText('Succeeded 1')).toBeVisible();
      await expect(page.getByText('Success rate 50%')).toBeVisible();

      expect(webhook.requests.length).toBeGreaterThanOrEqual(2);
      expect(webhook.requests[1].headers['idempotency-key']).toBe(webhook.requests[0].headers['idempotency-key']);

      const executionsResponse = await api.get(apiUrl(`rules/${ruleId}/action-executions?page=1&pageSize=10`));
      expect(executionsResponse.ok(), `list executions failed: ${executionsResponse.status()} ${await executionsResponse.text()}`).toBeTruthy();
      const executions = (await executionsResponse.json()).data.items as Array<{
        actionExecutionId: number;
        sourceActionExecutionId?: number;
        status: string;
        idempotencyKey: string;
      }>;
      const succeededRetry = executions.find((item) =>
        item.status === 'succeeded' && item.sourceActionExecutionId === failedExecutionId
      );
      expect(succeededRetry).toBeTruthy();
      expect(succeededRetry?.idempotencyKey).toBe(webhook.requests[0].headers['idempotency-key']);
    } finally {
      await api.dispose();
      await webhook.close();
    }
  });

  test('REQ-RULE-001: browser shows automatic webhook replay exhaustion', async ({ page }) => {
    const webhook = await startWebhookServer({ statusCode: 503, responseBody: 'erp unavailable' });
    const token = await generateJwt({
      sub: '9214',
      roles: ['ADMIN'],
      permissions: ['rule:manage', 'rule:debug'],
      status: 'enabled',
    });
    const api = await request.newContext({
      baseURL: apiBaseUrl,
      extraHTTPHeaders: { Authorization: `Bearer ${token}` },
    });

    try {
      const seed = Date.now();
      const ruleName = `Rule runtime replay exhaustion ${seed}`;
      const ruleId = await createPublishedRule(api, ruleName, createRuntimeDefinition(webhook.url));

      await page.addInitScript((accessToken) => {
        window.localStorage.setItem('accessToken', accessToken);
      }, token);

      await page.goto('/rules');
      const ruleCard = page.locator('.rule-item').filter({ hasText: ruleName });
      await expect(ruleCard).toBeVisible();
      await ruleCard.click();

      await fillSampleJson(page, {
        invoices: [
          { invoiceNo: 'A301', overdueAmount: 7000 },
          { invoiceNo: 'A302', overdueAmount: 9000 },
        ],
      });
      await clickProductionRun(page);

      await expect(page.getByText(/webhook action failed/)).toBeVisible();
      const failedRow = page.locator('.ledger-row').filter({ hasText: 'notifyFinance' }).first();
      await expect(failedRow).toBeVisible();
      await expect(failedRow).toContainText('pending_retry');
      const retryButtonName = await failedRow.getByRole('button', { name: /^Retry \d+$/ }).innerText();
      const failedExecutionId = Number(retryButtonName.replace('Retry ', ''));

      await expect.poll(async () => {
        const response = await api.get(apiUrl(`rules/${ruleId}/action-executions?page=1&pageSize=10`));
        expect(response.ok(), `list executions failed: ${response.status()} ${await response.text()}`).toBeTruthy();
        const executions = (await response.json()).data.items as Array<{
          sourceActionExecutionId?: number;
          status: string;
        }>;
        return executions.some((item) =>
          item.status === 'compensation_exhausted' && item.sourceActionExecutionId === failedExecutionId
        );
      }, { timeout: 15_000 }).toBeTruthy();

      await page.getByRole('button', { name: 'Refresh' }).click();
      const exhaustedRow = page.locator('.ledger-row').filter({ hasText: 'compensation_exhausted' }).first();
      await expect(exhaustedRow).toBeVisible();
      await expect(exhaustedRow).toContainText(`Source action #${failedExecutionId}`);
      await expect(page.getByText('Exhausted 1')).toBeVisible();
      expect(webhook.requests.length).toBeGreaterThanOrEqual(2);
      expect(webhook.requests[1].headers['idempotency-key']).toBe(webhook.requests[0].headers['idempotency-key']);
    } finally {
      await api.dispose();
      await webhook.close();
    }
  });
});

async function fillSampleJson(page: Page, sample: Record<string, unknown>) {
  await page.locator('.sample-editor textarea').fill(JSON.stringify(sample));
}

async function clickProductionRun(page: Page) {
  await page.locator('.toolbar-actions button').nth(2).click();
}

function createRuntimeDefinition(
  webhookUrl: string,
  options: {
    maxAsyncReplayAttempts?: number;
  } = {},
) {
  return {
    nodes: [
      { id: 'start', type: 'start' },
      {
        id: 'sumOverdue',
        type: 'aggregate',
        sourceField: 'invoices',
        operation: 'sum',
        valueField: 'overdueAmount',
        outputField: 'totalOverdueAmount',
      },
      { id: 'riskBranch', type: 'branch', field: 'totalOverdueAmount', operator: '>=', value: 10000 },
      {
        id: 'notifyFinance',
        type: 'action',
        actionType: 'webhook',
        endpoint: webhookUrl,
        method: 'POST',
        maxRetryCount: 0,
        retryBackoffSeconds: 0,
        maxAsyncReplayAttempts: options.maxAsyncReplayAttempts ?? 1,
        headers: { 'X-System': 'rule-runtime-smoke' },
        body: { eventType: 'rule_runtime_smoke' },
      },
      { id: 'end', type: 'end' },
    ],
    edges: [
      { source: 'start', target: 'sumOverdue' },
      { source: 'sumOverdue', target: 'riskBranch' },
      { source: 'riskBranch', target: 'notifyFinance', condition: 'true' },
      { source: 'riskBranch', target: 'end', condition: 'false' },
      { source: 'notifyFinance', target: 'end' },
    ],
  };
}

async function createPublishedRule(
  api: Awaited<ReturnType<typeof request.newContext>>,
  ruleName: string,
  definition: Record<string, unknown>,
) {
  const createResponse = await api.post(apiUrl('rules'), {
    data: {
      name: ruleName,
      definition,
    },
  });
  expect(createResponse.ok(), `create rule failed: ${createResponse.status()} ${await createResponse.text()}`).toBeTruthy();
  const created = (await createResponse.json()).data as { ruleId: number };

  const saveResponse = await api.put(apiUrl(`rules/${created.ruleId}`), {
    data: { name: ruleName, definition, status: 'draft' },
  });
  expect(saveResponse.ok(), `save rule failed: ${saveResponse.status()} ${await saveResponse.text()}`).toBeTruthy();

  const reviewResponse = await api.post(apiUrl(`rules/${created.ruleId}/review-submissions`), {
    data: { comment: `ready for ${ruleName}` },
  });
  expect(
    reviewResponse.ok(),
    `submit rule for review failed: ${reviewResponse.status()} ${await reviewResponse.text()}`,
  ).toBeTruthy();

  const approveResponse = await api.post(apiUrl(`rules/${created.ruleId}/approvals`), {
    data: { comment: `approved for ${ruleName}` },
  });
  expect(
    approveResponse.ok(),
    `approve rule failed: ${approveResponse.status()} ${await approveResponse.text()}`,
  ).toBeTruthy();

  return created.ruleId;
}

async function startWebhookServer(options: {
  statusCode?: number;
  responseBody?: string;
  responses?: Array<{ statusCode: number; responseBody?: string }>;
} = {}) {
  const statusCode = options.statusCode ?? 204;
  const responseBody = options.responseBody ?? '';
  const responses = options.responses ?? [];
  const requests: Array<{
    headers: Record<string, string | string[] | undefined>;
    body: Record<string, unknown>;
  }> = [];
  const server = createServer((req, res) => {
    const chunks: Buffer[] = [];
    req.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
    req.on('end', () => {
      const rawBody = Buffer.concat(chunks).toString('utf8');
      requests.push({
        headers: req.headers,
        body: rawBody ? JSON.parse(rawBody) : {},
      });
      const response = responses[Math.min(requests.length - 1, Math.max(responses.length - 1, 0))];
      res.writeHead(response?.statusCode ?? statusCode);
      res.end(response?.responseBody ?? responseBody);
    });
  });
  await new Promise<void>((resolve) => server.listen(0, '0.0.0.0', resolve));
  const address = server.address() as AddressInfo;
  return {
    url: `http://${webhookCallbackHost}:${address.port}/rule-webhook`,
    requests,
    close: () => closeServer(server),
  };
}

function closeServer(server: Server) {
  return new Promise<void>((resolve, reject) => {
    server.close((error) => {
      if (error) {
        reject(error);
        return;
      }
      resolve();
    });
  });
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
