import { expect, test } from '@playwright/test';

test.describe('知识条目管理 E2E', () => {
  test('REQ-KB-001：知识库管理页使用紧凑管理台布局', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.route('**/api/v1/knowledge-items**', async (route) => {
      if (route.request().method() !== 'GET') return route.fallback();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              { itemId: 1, title: '华东收入证据', sourceType: 'manual', indexStatus: 'indexed' },
              { itemId: 2, title: '市场费用分析', sourceType: 'batch_import', indexStatus: 'pending' }
            ],
            page: 1,
            pageSize: 20,
            total: 2
          }
        }
      });
    });

    await page.goto('/knowledge');

    await expect(page.getByTestId('knowledge-page')).toBeVisible();
    await expect(page.getByRole('heading', { name: '知识库管理' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '知识条目' })).toBeVisible();
    await expect(page.getByLabel('知识库指标').getByText('已入索引')).toBeVisible();

    const importPanel = page.getByTestId('knowledge-import-panel');
    const listPanel = page.getByTestId('knowledge-list-panel');
    const importBox = await importPanel.boundingBox();
    const listBox = await listPanel.boundingBox();
    expect(importBox).not.toBeNull();
    expect(listBox).not.toBeNull();
    expect(importBox!.width).toBeLessThanOrEqual(420);
    expect(listBox!.x).toBeGreaterThan(importBox!.x + importBox!.width);
    expect(listBox!.width).toBeGreaterThan(650);

    const textareaBox = await page.getByLabel('批量导入内容').boundingBox();
    expect(textareaBox).not.toBeNull();
    expect(textareaBox!.width).toBeLessThanOrEqual(360);
  });

  test('REQ-KB-001：删除被报告引用的知识条目需要二次确认', async ({ page }) => {
    let confirmedDeleteCalled = false;
    await page.route('**/api/v1/knowledge-items**', async (route) => {
      if (route.request().method() !== 'GET') return route.fallback();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                itemId: 42,
                title: '华东收入证据',
                content: '收入同比增长 12%',
                sourceType: 'manual',
                indexStatus: 'indexed'
              }
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await page.route('**/api/v1/knowledge-items/42?confirmed=false', async (route) => {
      await route.fulfill({
        status: 409,
        json: { code: 1003, message: '知识条目已被报告引用，请确认影响后再删除' }
      });
    });
    await page.route('**/api/v1/knowledge-items/42?confirmed=true', async (route) => {
      confirmedDeleteCalled = true;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { itemId: 42, deleted: true, requiresConfirmation: false, referenceCount: 2 }
        }
      });
    });

    await page.goto('/knowledge');
    await expect(page.getByText('华东收入证据')).toBeVisible();
    await expect(page.getByTestId('knowledge-list-panel').getByText('手工录入')).toBeVisible();
    await expect(page.getByTestId('knowledge-list-panel').getByText('已入索引')).toBeVisible();

    await page.getByRole('button', { name: '删除' }).click();
    await expect(page.getByText('知识条目已被报告引用，请确认影响后再删除')).toBeVisible();
    await page.getByRole('button', { name: '确认删除' }).click();

    await expect.poll(() => confirmedDeleteCalled).toBe(true);
    await expect(page.getByText('已删除知识条目，历史引用数：2')).toBeVisible();
  });

  test('REQ-KB-001：批量导入知识条目并刷新列表', async ({ page }) => {
    let batchPayload: unknown;
    let listCalls = 0;
    await page.route('**/api/v1/knowledge-items**', async (route) => {
      if (route.request().method() !== 'GET') return route.fallback();
      listCalls += 1;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: listCalls > 1
              ? [{ itemId: 99, title: '市场洞察', sourceType: 'manual', indexStatus: 'pending' }]
              : [],
            page: 1,
            pageSize: 20,
            total: listCalls > 1 ? 1 : 0
          }
        }
      });
    });
    await page.route('**/api/v1/knowledge-items/batch-import', async (route) => {
      batchPayload = route.request().postDataJSON();
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            knowledgeBaseId: 1,
            total: 2,
            imported: 1,
            failed: 1,
            items: [
              { title: '市场洞察', status: 'imported', itemId: 99 },
              { title: '', status: 'failed', reason: 'title_required' }
            ]
          }
        }
      });
    });

    await page.goto('/knowledge');
    await page.getByLabel('批量导入内容').fill('市场洞察,收入增长 12%,手工录入\n,缺少标题,手工录入');
    await page.getByRole('button', { name: '导入' }).click();

    await expect.poll(() => batchPayload).toEqual({
      knowledgeBaseId: '1',
      items: [
        { title: '市场洞察', content: '收入增长 12%', sourceType: 'manual' },
        { title: '', content: '缺少标题', sourceType: 'manual' }
      ]
    });
    await expect(page.getByText('导入 1 条，失败 1 条')).toBeVisible();
    await expect(page.getByText('市场洞察')).toBeVisible();
    await expect(page.getByText('待索引')).toBeVisible();
  });
});
