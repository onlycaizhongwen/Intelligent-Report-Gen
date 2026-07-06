import { expect, test } from '@playwright/test';

test.describe('数据源同步 E2E', () => {
  test('REQ-KB-003：配置 API 高级请求、字段映射、调度、重试并支持人工重跑', async ({ page }) => {
    let savedPayload: Record<string, unknown> | null = null;
    const syncPayloads: Record<string, unknown>[] = [];

    await page.route('**/api/v1/data-sources/presets', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            {
              presetId: 'finance-api',
              displayName: '财务凭证 HTTP API',
              category: 'finance',
              sourceType: 'api',
              endpoint: 'https://finance.example.com/api/v1/vouchers',
              username: 'finance_reader',
              fieldMapping: {
                rowsPath: 'data.vouchers',
                titleField: 'voucherNo',
                contentField: 'summary',
                authType: 'api_key',
                apiKeyHeader: 'X-API-Key',
                pageParam: 'page',
                pageStart: 1,
                pageSizeParam: 'pageSize',
                pageSize: 100,
                maxPages: 3
              },
              cursorColumn: 'voucherId',
              scheduleEnabled: false,
              scheduleIntervalSeconds: 900,
              maxRetryCount: 5
            }
          ]
        }
      });
    });

    await page.route('**/api/v1/data-sources', async (route) => {
      savedPayload = await route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            dataSourceId: 77,
            name: 'ERP API',
            sourceType: 'api',
            endpoint: 'https://erp.example.com/reports',
            status: 'enabled',
            knowledgeBaseId: 1,
            cursorColumn: 'id',
            scheduleEnabled: true,
            scheduleIntervalSeconds: 300,
            maxRetryCount: 2,
            credentialConfigured: true
          }
        }
      });
    });

    await page.route('**/api/v1/data-sources/test-connection', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { dataSourceId: 77, success: true, message: 'connection available', sourceType: 'api' }
        }
      });
    });

    await page.route('**/api/v1/data-sources/77/sync-runs**', async (route) => {
      if (route.request().method() === 'POST') {
        syncPayloads.push(await route.request().postDataJSON());
        await route.fulfill({
          json: {
            code: 200,
            message: 'ok',
            data: {
              syncRunId: 9001 + syncPayloads.length,
              dataSourceId: 77,
              mode: syncPayloads.at(-1)?.mode ?? 'manual',
              status: 'succeeded',
              processedRows: 2,
              lastCursor: '2',
              message: 'sync completed',
              startedAt: '2026-06-23T10:00:00Z'
            }
          }
        });
        return;
      }
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                syncRunId: 9002,
                dataSourceId: 77,
                mode: 'manual_retry',
                status: 'succeeded',
                processedRows: 2,
                lastCursor: '2',
                message: 'sync completed',
                startedAt: '2026-06-23T10:00:00Z'
              }
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });

    await page.goto('/knowledge/data-sources');
    await page.getByRole('combobox', { name: '配置模板' }).click({ force: true });
    await page.getByRole('option', { name: 'finance / 财务凭证 HTTP API' }).click();
    await expect(page.getByLabel('连接地址')).toHaveValue('https://finance.example.com/api/v1/vouchers');
    await expect(page.getByLabel('API 行路径')).toHaveValue('data.vouchers');
    await expect(page.getByLabel('标题字段')).toHaveValue('voucherNo');
    await expect(page.getByLabel('内容字段')).toHaveValue('summary');
    await page.getByLabel('数据源名称').fill('ERP API');
    await page.getByRole('combobox', { name: '数据源类型' }).click({ force: true });
    await page.getByRole('option', { name: 'HTTP API', exact: true }).click();
    await page.getByLabel('连接地址').fill('https://erp.example.com/reports');
    await page.getByLabel('用户名或 Token 标识').fill('erp_reader');
    await page.getByLabel('密码或访问密钥').fill('secret');
    await page.getByLabel('目标知识库 ID').fill('1');
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
    await page.getByRole('spinbutton', { name: '起始页码' }).fill('1');
    await page.getByLabel('每页数量参数').fill('pageSize');
    await page.getByRole('spinbutton', { name: '每页数量' }).fill('100');
    await page.getByRole('spinbutton', { name: '最大页数' }).fill('3');
    await page.getByText('启用', { exact: true }).click();
    await page.getByRole('spinbutton', { name: '同步间隔秒' }).fill('300');
    await page.getByRole('spinbutton', { name: '最大失败重试次数' }).fill('2');

    await page.getByRole('button', { name: '保存数据源' }).click();
    await expect(page.getByText('凭据已配置')).toBeVisible();
    await expect(page.getByText('最大重试 2 次')).toBeVisible();
    expect(savedPayload).toMatchObject({
      name: 'ERP API',
      sourceType: 'api',
      endpoint: 'https://erp.example.com/reports',
      username: 'erp_reader',
      password: 'secret',
      knowledgeBaseId: 1,
      fieldMapping: {
        rowsPath: 'data.items',
        titleField: 'headline',
        contentField: 'body',
        method: 'POST',
        authType: 'api_key',
        apiKeyHeader: 'X-API-Key',
        headers: { 'X-Tenant': 'finance' },
        bodyTemplate: '{"period":"2026Q1"}',
        pageParam: 'page',
        pageStart: 1,
        pageSizeParam: 'pageSize',
        pageSize: 100,
        maxPages: 3
      },
      cursorColumn: 'id',
      scheduleEnabled: true,
      scheduleIntervalSeconds: 300,
      maxRetryCount: 2
    });

    await page.getByRole('button', { name: '测试连接' }).click();
    await expect(page.getByText('connection available')).toBeVisible();

    await page.getByRole('button', { name: '启动同步' }).click();
    await page.getByRole('button', { name: '人工重跑' }).click();
    expect(syncPayloads).toEqual([{ mode: 'manual' }, { mode: 'manual_retry' }]);
    await expect(page.getByText('manual_retry')).toBeVisible();
    await expect(page.getByText('sync completed')).toBeVisible();
  });
});
