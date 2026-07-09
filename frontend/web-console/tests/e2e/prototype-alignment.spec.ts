import { expect, test, type Page } from '@playwright/test';

async function enableLocalPreview(page: Page) {
  await page.addInitScript(() => {
    window.localStorage.setItem('accessToken', 'local-preview-token');
    window.localStorage.setItem('authMode', 'local-preview');
  });
}

async function mockAllBusinessGetsAsUnauthorized(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    if (route.request().method() !== 'GET') return route.fallback();
    await route.fulfill({
      status: 401,
      json: { code: 401, message: '请登录后继续操作', data: null }
    });
  });
}

async function mockDashboardOverview(page: Page) {
  await page.route('**/api/v1/dashboard/overview**', async (route) => {
    await route.fulfill({
      json: {
        code: 200,
        message: 'ok',
        data: {
          range: 'last7days',
          cards: { reportOutputs: 0, knowledgeItems: 0, activeDataSources: 0, citationHitRate: 0, activeUsers: 0 },
          reportTrend: [],
          knowledgeRank: [],
          ruleScheduleHealth: { scheduledRules: 0, failedScheduledRules: 0, blockedScheduledRules: 0, recentAlerts: 0 },
          recentActivities: []
        }
      }
    });
  });
}

test.describe('原型功能对齐回归', () => {
  test('P0：我的报告是独立列表页且菜单跳转正确', async ({ page }) => {
    await mockDashboardOverview(page);
    await page.route('**/api/v1/reports?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              { reportId: 101, title: 'Q3华东区销售分析报告', status: 'completed', generationMode: '智能生成', createdAt: '2026-06-11 10:30' }
            ],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: '我的报告' }).click();

    await expect(page).toHaveURL(/\/reports$/);
    await expect(page.getByRole('heading', { name: '我的报告' })).toBeVisible();
    await expect(page.getByRole('button', { name: '+ 新建报告' })).toBeVisible();
    await expect(page.getByText('报告名称')).toBeVisible();
    await expect(page.getByText('生成方式')).toBeVisible();
    await expect(page.getByText('Q3华东区销售分析报告')).toBeVisible();
    await expect(page.getByRole('heading', { name: '智能生成' })).toHaveCount(0);
  });

  test('P0：历史记录入口展示用户历史，管理员审计作为子入口', async ({ page }) => {
    await mockDashboardOverview(page);
    await page.route('**/api/v1/history**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              { id: 1, operationType: '生成报告', content: '创建 Q3华东区销售分析报告', operator: '张三', createdAt: '2026-06-11 10:30', result: '成功' }
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await page.route('**/api/v1/audit-logs**', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], page: 1, pageSize: 10, total: 0 } } });
    });

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: '历史记录' }).click();

    await expect(page).toHaveURL(/\/history$/);
    await expect(page.getByRole('heading', { name: '历史记录' })).toBeVisible();
    await expect(page.getByText('共 1 条记录')).toBeVisible();
    await expect(page.getByLabel('个人历史').getByText('操作类型')).toBeVisible();
    await expect(page.getByText('创建 Q3华东区销售分析报告')).toBeVisible();
    await expect(page.getByRole('tab', { name: '管理员审计' })).toBeVisible();
  });

  test('P0：local-preview 下管理页面接口 401 不展示登录错误', async ({ page }) => {
    await enableLocalPreview(page);
    await mockAllBusinessGetsAsUnauthorized(page);

    const pages = [
      { path: '/dashboard', heading: '工作台' },
      { path: '/admin/users', heading: '权限协作' },
      { path: '/reports/enterprise-export-templates', heading: '企业导出模板' },
      { path: '/rules/approvals', heading: '审批待办' },
      { path: '/rules/delegate-rules', heading: '审批委托' },
      { path: '/rules/approval-templates', heading: '审批模板' },
      { path: '/admin/organizations', heading: '组织管理' }
    ];

    for (const pageInfo of pages) {
      await page.goto(pageInfo.path);
      await expect(page.getByRole('heading', { name: pageInfo.heading })).toBeVisible();
      await expect(page.getByText('请登录后继续操作')).toHaveCount(0);
    }
  });

  test('P1：规则页 local-preview 展示原型化示例规则、节点库和调试面板', async ({ page }) => {
    await enableLocalPreview(page);
    await mockAllBusinessGetsAsUnauthorized(page);

    await page.goto('/rules');

    await expect(page.getByRole('heading', { name: '规则编排' })).toBeVisible();
    await expect(page.getByText('规则列表')).toBeVisible();
    await expect(page.getByText('销售考核规则')).toBeVisible();
    await expect(page.getByText('财务数据提取规则')).toBeVisible();
    await expect(page.getByText('节点库')).toBeVisible();
    await expect(page.getByText('输入节点')).toBeVisible();
    await expect(page.getByText('解析节点')).toBeVisible();
    await expect(page.getByText('分类节点')).toBeVisible();
    await expect(page.getByText('调试面板')).toBeVisible();
    await expect(page.getByText('2026年Q3华东区销售额达到1.2亿元人民币')).toBeVisible();
    await expect(page.getByText('请登录后继续操作')).toHaveCount(0);
  });

  test('P1：权限协作页聚合用户管理、权限矩阵、报告协作和操作日志', async ({ page }) => {
    await page.route('**/api/v1/users?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              { userId: 1, username: 'zhangsan', displayName: '张三', roles: ['高级分析师'], status: 'enabled' }
            ],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });

    await page.goto('/admin/users');

    await expect(page.getByRole('heading', { name: '权限协作' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '用户管理' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '权限矩阵' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '报告协作' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '操作日志' })).toBeVisible();
    await page.getByRole('tab', { name: '权限矩阵' }).click();
    await expect(page.getByText('角色权限矩阵')).toBeVisible();
    await page.getByRole('tab', { name: '报告协作' }).click();
    await expect(page.getByText('Q3华东区销售分析报告', { exact: true })).toBeVisible();
  });
});
