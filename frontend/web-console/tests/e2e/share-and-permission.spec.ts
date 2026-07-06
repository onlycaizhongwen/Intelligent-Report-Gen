import { expect, test } from '@playwright/test';

test.describe('分享与权限 E2E', () => {
  test('REQ-COLLAB-001：分享链接过期时展示拒绝访问', async ({ page }) => {
    await page.route('**/api/v1/share-links/expired-token/report', async (route) => {
      await route.fulfill({ status: 403, json: { code: 403, message: '分享链接已过期', data: null } });
    });

    await page.goto('/share/expired-token');
    await expect(page.getByRole('heading', { name: '报告分享访问' })).toBeVisible();
    await page.getByLabel(/密码|访问码/).fill('wrong');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByText(/过期|无权|拒绝|分享链接已过期/)).toBeVisible();
  });

  test('REQ-COLLAB-001：密码错误时展示拒绝访问', async ({ page }) => {
    await page.route('**/api/v1/share-links/password-token/report', async (route) => {
      await route.fulfill({ status: 403, json: { code: 403, message: '分享密码错误', data: null } });
    });

    await page.goto('/share/password-token');
    await page.getByLabel(/密码|访问码/).fill('wrong');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByText('分享密码错误')).toBeVisible();
  });

  test('REQ-COLLAB-001：分享访问触发限流时展示稍后再试提示', async ({ page }) => {
    await page.route('**/api/v1/share-links/rate-limited-token/report', async (route) => {
      await route.fulfill({ status: 429, json: { code: 429, message: 'share access rate limited', data: null } });
    });

    await page.goto('/share/rate-limited-token');
    await page.getByLabel(/密码|访问码/).fill('secret');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByText('尝试次数过多，请稍后再试')).toBeVisible();
  });

  test('REQ-COLLAB-001：分享访问触发挑战后可提交挑战答案继续访问', async ({ page }) => {
    const payloads: Array<Record<string, unknown>> = [];
    await page.route('**/api/v1/share-links/challenge-token/report', async (route) => {
      const payload = route.request().postDataJSON();
      payloads.push(payload);
      if (payload.challengeAnswer !== 'REPORT') {
        await route.fulfill({ status: 428, json: { code: 428, message: 'share access challenge required', data: null } });
        return;
      }
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: {
            accessGranted: true,
            shareToken: 'challenge-token',
            reportId: '45',
            title: '挑战保护报告',
            status: 'completed',
            allowDownload: false,
            exports: [],
            sections: [{ heading: '摘要', content: '挑战通过后可见。', citations: [] }]
          }
        }
      });
    });

    await page.goto('/share/challenge-token');
    await page.getByLabel(/密码|访问码/).fill('secret');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByText('需要完成访问验证，请输入 REPORT 后继续')).toBeVisible();
    await page.getByLabel('访问验证').fill('REPORT');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByRole('heading', { name: '挑战保护报告' })).toBeVisible();
    expect(payloads[1]).toMatchObject({ password: 'secret', challengeAnswer: 'REPORT' });
  });

  test('REQ-COLLAB-001：外部用户只能查看分享报告只读详情', async ({ page }) => {
    await page.route('**/api/v1/share-links/read-token/report', async (route) => {
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: {
            accessGranted: true,
            shareToken: 'read-token',
            reportId: '42',
            title: '季度经营分析报告',
            status: 'completed',
            currentVersionId: 8,
            allowDownload: false,
            exports: [],
            sections: [
              {
                heading: '经营摘要',
                content: '收入增长 12%，但应收账款风险上升。',
                citations: ['doc_1_chunk_0']
              }
            ]
          }
        }
      });
    });

    await page.goto('/share/read-token');
    await page.getByLabel(/密码|访问码/).fill('secret');
    await page.getByRole('button', { name: /访问|打开/ }).click();

    await expect(page.getByRole('heading', { name: '季度经营分析报告' })).toBeVisible();
    await expect(page.getByText('收入增长 12%，但应收账款风险上升。')).toBeVisible();
    await expect(page.getByText('doc_1_chunk_0')).toBeVisible();
    await expect(page.getByTestId('external-share-watermark')).toContainText('外部只读');
    await expect(page.getByTestId('external-share-watermark')).toContainText('read-token');
    await expect(page.getByTestId('external-share-watermark')).toContainText('报告 42');
    await expect(page.getByRole('button', { name: /下载/ })).toHaveCount(0);
  });

  test('REQ-COLLAB-001：允许下载时通过受控分享下载接口获取短期 URL', async ({ page }) => {
    let downloadEndpointCalled = false;
    await page.route('**/api/v1/share-links/download-token/report', async (route) => {
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: {
            accessGranted: true,
            shareToken: 'download-token',
            reportId: '43',
            title: '可下载报告',
            status: 'completed',
            allowDownload: true,
            sections: [],
            exports: [
              {
                exportFileId: '9001',
                fileName: 'report-43.md',
                format: 'markdown',
                contentType: 'text/markdown',
                sizeBytes: 128
              }
            ]
          }
        }
      });
    });
    await page.route('**/api/v1/share-links/download-token/exports/9001/download-url', async (route) => {
      downloadEndpointCalled = true;
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: {
            exportFileId: '9001',
            reportId: '43',
            fileName: 'report-43.md',
            contentType: 'text/markdown',
            sizeBytes: 128,
            downloadPolicy: 'share_presigned_url',
            downloadUrl: '/mock-download/report-43.md?token=short',
            expiresAt: '2026-06-23T12:00:00Z'
          }
        }
      });
    });
    await page.route('**/mock-download/report-43.md**', async (route) => {
      await route.fulfill({ status: 200, body: 'downloaded' });
    });

    await page.goto('/share/download-token');
    await page.getByLabel(/密码|访问码/).fill('secret');
    await page.getByRole('button', { name: /访问|打开/ }).click();
    await page.getByRole('button', { name: /下载 report-43\.md/ }).click();

    await expect.poll(() => downloadEndpointCalled).toBe(true);
  });

  test('REQ-COLLAB-001：未授权下载时保留只读详情并展示错误', async ({ page }) => {
    await page.route('**/api/v1/share-links/denied-download-token/report', async (route) => {
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: {
            accessGranted: true,
            shareToken: 'denied-download-token',
            reportId: '44',
            title: '下载受限报告',
            status: 'completed',
            allowDownload: true,
            sections: [{ heading: '摘要', content: '只读内容。', citations: [] }],
            exports: [{ exportFileId: '9002', fileName: 'report-44.md' }]
          }
        }
      });
    });
    await page.route('**/api/v1/share-links/denied-download-token/exports/9002/download-url', async (route) => {
      await route.fulfill({ status: 403, json: { code: 403, message: '分享链接未授权下载', data: null } });
    });

    await page.goto('/share/denied-download-token');
    await page.getByLabel(/密码|访问码/).fill('secret');
    await page.getByRole('button', { name: /访问|打开/ }).click();
    await page.getByRole('button', { name: /下载 report-44\.md/ }).click();

    await expect(page.getByText('分享链接未授权下载')).toBeVisible();
    await expect(page.getByRole('heading', { name: '下载受限报告' })).toBeVisible();
  });

  test('REQ-AUTH-001：无权限访问管理页时被拒绝', async ({ page }) => {
    await page.route('**/api/v1/users**', async (route) => {
      await route.fulfill({ status: 403, json: { code: 403, message: '无权限', data: null } });
    });

    await page.goto('/admin/users');

    await expect(page.getByText(/无权限|403|拒绝/)).toBeVisible();
  });

  test('REQ-AUTH-001：管理员批量导入并禁用用户', async ({ page }) => {
    let users = [
      {
        userId: 1,
        username: 'admin.demo',
        displayName: '系统管理员',
        status: 'enabled',
        roles: ['system_admin']
      }
    ];
    let batchImportCalled = false;
    let disableCalled = false;

    await page.route('**/api/v1/users?page=1&pageSize=20', async (route) => {
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: { items: users, page: 1, pageSize: 20, total: users.length }
        }
      });
    });
    await page.route('**/api/v1/users/batch-import', async (route) => {
      batchImportCalled = true;
      const payload = route.request().postDataJSON();
      users = [
        {
          userId: 3,
          username: payload.users[1].username,
          displayName: payload.users[1].displayName,
          status: 'enabled',
          roles: payload.users[1].roles
        },
        {
          userId: 2,
          username: payload.users[0].username,
          displayName: payload.users[0].displayName,
          status: 'enabled',
          roles: payload.users[0].roles
        },
        ...users
      ];
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: {
            imported: 2,
            failed: 0,
            items: [
              { username: 'analyst.one', status: 'imported' },
              { username: 'viewer.one', status: 'imported' }
            ]
          }
        }
      });
    });
    await page.route('**/api/v1/users/2/status', async (route) => {
      disableCalled = true;
      users = users.map((user) => (user.userId === 2 ? { ...user, status: 'disabled' } : user));
      await route.fulfill({
        status: 200,
        json: {
          code: 200,
          message: 'success',
          data: { userId: 2, username: 'analyst.one', displayName: 'Analyst One', status: 'disabled', roles: ['analyst'] }
        }
      });
    });

    await page.goto('/admin/users');
    await expect(page.getByText('admin.demo')).toBeVisible();

    await page.getByLabel('批量导入用户').fill([
      'analyst.one,Analyst One,analyst',
      'viewer.one,Viewer One,viewer'
    ].join('\n'));
    await page.getByRole('button', { name: '执行导入' }).click();

    await expect.poll(() => batchImportCalled).toBe(true);
    await expect(page.getByText('导入成功 2 个，失败 0 个')).toBeVisible();
    await expect(page.getByText('analyst.one')).toBeVisible();

    await page.getByRole('row', { name: /analyst\.one/ }).getByRole('button', { name: '禁用' }).click();

    await expect.poll(() => disableCalled).toBe(true);
    await expect(page.getByRole('row', { name: /analyst\.one/ }).getByText('disabled')).toBeVisible();
  });
});
