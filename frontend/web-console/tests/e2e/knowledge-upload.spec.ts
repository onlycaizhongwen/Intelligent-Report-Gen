import { expect, test } from '@playwright/test';

test.describe('知识库导入 E2E', () => {
  test('REQ-KB-002：上传请求使用当前用户可用知识库，而不是硬编码 1', async ({ page }) => {
    await page.route('**/api/v1/knowledge-bases?page=1&pageSize=20', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [{ knowledgeBaseId: 9, name: 'Finance KB', ownerUserId: 7, status: 'enabled' }],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });

    await page.route('**/api/v1/documents/upload*', async (route) => {
      expect(route.request().url()).toContain('knowledgeBaseId=9');
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { documentId: '301', filename: 'scan.pdf', parseStatus: 'processed' }
        }
      });
    });

    await page.goto('/knowledge/upload');
    await page.setInputFiles('input[type="file"]', '../../tests/e2e/fixtures/sample.txt');

    await expect(page.getByText('scan.pdf processed')).toBeVisible();
  });

  test('REQ-KB-002：上传文件后轮询展示解析失败原因', async ({ page }) => {
    await page.route('**/api/v1/knowledge-bases?page=1&pageSize=20', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [{ knowledgeBaseId: 1, name: 'Default KB', ownerUserId: 1, status: 'enabled' }],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });

    await page.route('**/api/v1/documents/upload*', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { documentId: '101', filename: 'scan.pdf', parseStatus: 'pending' }
        }
      });
    });

    let statusCalls = 0;
    await page.route('**/api/v1/documents/101', async (route) => {
      statusCalls += 1;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: statusCalls === 1
            ? { documentId: '101', filename: 'scan.pdf', parseStatus: 'pending' }
            : {
                documentId: '101',
                filename: 'scan.pdf',
                parseStatus: 'failed',
                parseFailureReason: 'document knowledge/scan.pdf requires OCR or table extraction before text parsing'
              }
        }
      });
    });

    await page.goto('/knowledge/upload');
    await page.setInputFiles('input[type="file"]', '../../tests/e2e/fixtures/sample.txt');

    await expect(page.getByText('scan.pdf failed')).toBeVisible();
    await expect(page.getByText('document knowledge/scan.pdf requires OCR or table extraction before text parsing')).toBeVisible();
  });

  test('REQ-KB-002：上传文件后轮询等待较慢的异步解析成功状态', async ({ page }) => {
    await page.route('**/api/v1/knowledge-bases?page=1&pageSize=20', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [{ knowledgeBaseId: 1, name: 'Default KB', ownerUserId: 1, status: 'enabled' }],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });

    await page.route('**/api/v1/documents/upload*', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { documentId: '202', filename: 'scan-success.pdf', parseStatus: 'pending' }
        }
      });
    });

    let statusCalls = 0;
    await page.route('**/api/v1/documents/202', async (route) => {
      statusCalls += 1;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: statusCalls < 6
            ? { documentId: '202', filename: 'scan-success.pdf', parseStatus: 'pending' }
            : {
                documentId: '202',
                filename: 'scan-success.pdf',
                parseStatus: 'processed'
              }
        }
      });
    });

    await page.goto('/knowledge/upload');
    await page.setInputFiles('input[type="file"]', '../../tests/e2e/fixtures/sample.txt');

    await expect(page.getByText('scan-success.pdf processed')).toBeVisible();
  });
});
