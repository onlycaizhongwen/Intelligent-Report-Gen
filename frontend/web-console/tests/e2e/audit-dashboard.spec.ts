import { expect, test } from '@playwright/test';

test.describe('审计与工作台 E2E', () => {
  test('REQ-DASH-001：工作台按时间范围刷新真实指标、趋势、排行和最近活动', async ({ page }) => {
    const requestedRanges: string[] = [];
    await page.route('**/api/v1/dashboard/overview**', async (route) => {
      const url = new URL(route.request().url());
      const range = url.searchParams.get('range') ?? '';
      requestedRanges.push(range);
      const isLast30Days = range === 'last30days';
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            range,
            cards: {
              reportOutputs: isLast30Days ? 21 : 7,
              knowledgeItems: isLast30Days ? 56 : 18,
              activeDataSources: isLast30Days ? 4 : 2,
              citationHitRate: isLast30Days ? 0.91 : 0.75,
              activeUsers: isLast30Days ? 8 : 3
            },
            reportTrend: [
              {
                date: isLast30Days ? '2026-06-01' : '2026-06-23',
                completedReports: isLast30Days ? 9 : 2
              }
            ],
            knowledgeRank: [
              {
                knowledgeBaseId: 1,
                name: isLast30Days ? '财务制度库' : '经营分析库',
                references: isLast30Days ? 32 : 11
              }
            ],
            recentActivities: [
              {
                operationLogId: 9001,
                operationType: isLast30Days ? 'DATA_SOURCE_SYNC' : 'REPORT_COMPLETED',
                actorUserId: isLast30Days ? 8 : 3,
                result: 'succeeded',
                createdAt: '2026-06-23T10:00:00Z'
              }
            ]
          }
        }
      });
    });

    await page.goto('/dashboard');

    await expect(page.getByRole('heading', { name: '工作台' })).toBeVisible();
    await expect(page.getByText('报告产出').locator('..').getByText('7')).toBeVisible();
    await expect(page.getByText('引用命中率').locator('..').getByText('75%')).toBeVisible();
    await expect(page.getByRole('cell', { name: '2026-06-23', exact: true })).toBeVisible();
    await expect(page.getByText('经营分析库')).toBeVisible();
    await expect(page.getByText('REPORT_COMPLETED')).toBeVisible();

    await page.getByText('近 30 天').click();

    await expect(page.getByText('报告产出').locator('..').getByText('21')).toBeVisible();
    await expect(page.getByText('引用命中率').locator('..').getByText('91%')).toBeVisible();
    await expect(page.getByRole('cell', { name: '2026-06-01', exact: true })).toBeVisible();
    await expect(page.getByText('财务制度库')).toBeVisible();
    await expect(page.getByText('DATA_SOURCE_SYNC')).toBeVisible();
    await expect.poll(() => requestedRanges).toEqual(['last7days', 'last30days']);
  });

  test('REQ-AUDIT-001：个人历史与全局审计使用不同权限边界接口', async ({ page }) => {
    const requested: string[] = [];
    await page.route('**/api/v1/history**', async (route) => {
      requested.push('/history');
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                operationLogId: 1001,
                operationType: 'report_export',
                actorUserId: 501,
                resourceType: 'report',
                resourceId: 42,
                result: 'succeeded',
                createdAt: '2026-06-22T10:00:00Z'
              }
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await page.route('**/api/v1/audit-logs**', async (route) => {
      requested.push('/audit-logs');
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                operationLogId: 2001,
                operationType: 'share_create',
                actorUserId: 777,
                resourceType: 'share_link',
                resourceId: 88,
                result: 'succeeded',
                createdAt: '2026-06-22T11:00:00Z'
              }
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });

    await page.goto('/audit');

    await expect(page.getByRole('heading', { name: '审计与历史' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '个人历史' })).toBeVisible();
    await expect(page.getByRole('tab', { name: '全局审计' })).toBeVisible();
    await expect(page.getByText('report_export')).toBeVisible();
    await expect(page.getByText('501')).toBeVisible();

    await page.getByRole('tab', { name: '全局审计' }).click();

    await expect(page.getByText('share_create')).toBeVisible();
    await expect(page.getByText('777')).toBeVisible();
    await expect.poll(() => requested).toEqual(['/history', '/audit-logs']);
  });
});
