import { expect, test } from '@playwright/test';

const adminPages = [
  { path: '/dashboard', title: '工作台' },
  { path: '/audit', title: '审计与历史' },
  { path: '/knowledge/upload', title: '文档上传' },
  { path: '/knowledge/data-sources', title: '数据源配置' },
  { path: '/admin/users', title: '权限协作' },
  { path: '/admin/organizations', title: '组织管理' },
  { path: '/rules/approval-templates', title: '审批模板' },
  { path: '/reports/enterprise-export-templates', title: '企业导出模板' }
];

test.describe('后台页面视觉一致性', () => {
  for (const pageInfo of adminPages) {
    test(`${pageInfo.title} 使用统一后台页面壳`, async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.route('**/api/v1/**', async (route) => {
        if (route.request().method() !== 'GET') return route.fallback();
        await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], total: 0, page: 1, pageSize: 20 } } });
      });

      await page.goto(pageInfo.path);
      await expect(page.getByRole('heading', { name: pageInfo.title })).toBeVisible();

      const root = page.locator('main .page, main .dashboard-page').first();
      await expect(root).toBeVisible();

      const rootBox = await root.boundingBox();
      expect(rootBox).not.toBeNull();
      expect(rootBox!.x).toBeGreaterThanOrEqual(220);
      expect(rootBox!.width).toBeGreaterThan(900);

      const background = await root.evaluate((element) => getComputedStyle(element).backgroundColor);
      const padding = await root.evaluate((element) => getComputedStyle(element).paddingTop);
      expect(background).toBe('rgb(244, 246, 248)');
      expect(Number.parseFloat(padding)).toBeGreaterThanOrEqual(20);
    });
  }

  test('空表格使用中文空状态文案', async ({ page }) => {
    await page.route('**/api/v1/**', async (route) => {
      if (route.request().method() !== 'GET') return route.fallback();
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], total: 0, page: 1, pageSize: 20 } } });
    });

    await page.goto('/admin/users');
    await expect(page.getByText('暂无数据')).toBeVisible();
    await expect(page.getByText('No Data')).toHaveCount(0);
  });
});
