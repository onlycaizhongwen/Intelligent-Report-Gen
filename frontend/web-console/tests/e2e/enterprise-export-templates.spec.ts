import { expect, test } from '@playwright/test';

test.describe('Enterprise export templates E2E', () => {
  test('P0：企业导出模板页面使用中文业务文案', async ({ page }) => {
    await page.route('**/api/v1/enterprise-export-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { items: [], page: 1, pageSize: 20, total: 0 }
        }
      });
    });

    await page.goto('/reports/enterprise-export-templates');

    await expect(page.getByRole('heading', { name: '企业导出模板' })).toBeVisible();
    await expect(page.getByText('统一维护报告导出的企业品牌、版式和模板版本。')).toBeVisible();
    await expect(page.getByLabel('模板编码')).toBeVisible();
    await expect(page.getByLabel('模板名称')).toBeVisible();
    await expect(page.getByLabel('企业名称')).toBeVisible();
    await expect(page.getByLabel('标识对象键')).toBeVisible();
    await expect(page.getByRole('button', { name: '创建企业导出模板' })).toBeVisible();
    await expect(page.getByText('暂无企业导出模板')).toBeVisible();

    const visibleText = await page.locator('body').innerText();
    expect(visibleText).not.toMatch(/Enterprise Export Templates|Manage governed|Template ID|Company name|Logo object key|Logo 对象键|Header|Footer|Cover title|TOC title|Section title prefix|Existing templates|No enterprise export templates|Refresh/);
  });

  test('manages centralized enterprise export templates for REQ-REPORT-004', async ({ page }) => {
    let createPayload: Record<string, unknown> | null = null;
    let updatePayload: Record<string, unknown> | null = null;
    let templates = [
      {
        id: 1,
        templateId: 'board-standard',
        name: 'Board standard export',
        version: 'v1',
        status: 'active',
        brandSnapshot: {
          templateId: 'board-standard',
          templateVersion: 'v1',
          format: 'pptx',
          companyName: 'Acme Finance',
          logoObjectKey: 'logos/acme.svg',
          header: 'Acme Finance Board Pack',
          footer: 'Confidential',
          fontFamily: 'Microsoft YaHei',
          primaryColor: '#1F4E79',
          layout: {
            coverTitle: 'Board Strategy Pack',
            tocTitle: 'Report Outline',
            bodyTitlePrefix: 'Section'
          }
        },
        createdAt: '2026-07-02T08:00:00Z',
        updatedAt: '2026-07-02T08:00:00Z'
      }
    ];

    await page.route('**/api/v1/enterprise-export-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: templates,
            page: 1,
            pageSize: 20,
            total: templates.length
          }
        }
      });
    });

    await page.route('**/api/v1/enterprise-export-templates/board-standard/versions', async (route) => {
      const latest = templates.find((template) => template.templateId === 'board-standard') ?? templates[0];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            {
              ...latest,
              brandSnapshot: {
                ...latest.brandSnapshot,
                templateVersion: latest.version
              },
              updatedAt: latest.updatedAt
            },
            {
              id: 1,
              templateId: 'board-standard',
              name: 'Board standard export',
              version: 'v1',
              status: 'active',
              brandSnapshot: {
                templateId: 'board-standard',
                templateVersion: 'v1',
                format: 'pptx',
                companyName: 'Acme Finance',
                logoObjectKey: 'logos/acme.svg',
                header: 'Acme Finance Board Pack',
                footer: 'Confidential',
                fontFamily: 'Microsoft YaHei',
                primaryColor: '#1F4E79',
                layout: {
                  coverTitle: 'Board Strategy Pack',
                  tocTitle: 'Report Outline',
                  bodyTitlePrefix: 'Section'
                }
              },
              createdAt: '2026-07-02T08:00:00Z',
              updatedAt: '2026-07-02T08:00:00Z'
            }
          ]
        }
      });
    });

    await page.route('**/api/v1/enterprise-export-templates', async (route) => {
      createPayload = await route.request().postDataJSON();
      const created = {
        id: 2,
        templateId: 'finance-quarterly',
        name: 'Finance quarterly export',
        version: 'v1',
        status: 'active',
        brandSnapshot: {
          ...(createPayload.brand as Record<string, unknown>),
          templateId: 'finance-quarterly',
          templateVersion: 'v1',
          format: 'pdf',
          layout: {
            ...((createPayload.brand as { layout?: Record<string, unknown> }).layout ?? {})
          }
        },
        createdAt: '2026-07-02T10:00:00Z',
        updatedAt: '2026-07-02T10:00:00Z'
      };
      templates = [created, ...templates];
      await route.fulfill({ json: { code: 200, message: 'ok', data: created } });
    });

    await page.route('**/api/v1/enterprise-export-templates/board-standard', async (route) => {
      updatePayload = await route.request().postDataJSON();
      const target = templates.find((template) => template.templateId === 'board-standard');
      if (target) {
        Object.assign(target, {
          name: updatePayload.name,
          version: 'v2',
          brandSnapshot: {
            ...(updatePayload.brand as Record<string, unknown>),
            templateId: 'board-standard',
            templateVersion: 'v2',
            format: 'pptx'
          },
          updatedAt: '2026-07-02T11:00:00Z'
        });
      }
      await route.fulfill({ json: { code: 200, message: 'ok', data: target } });
    });

    await page.route('**/api/v1/enterprise-export-templates/board-standard/disable', async (route) => {
      const target = templates.find((template) => template.templateId === 'board-standard');
      if (target) target.status = 'disabled';
      await route.fulfill({ json: { code: 200, message: 'ok', data: target } });
    });

    await page.route('**/api/v1/enterprise-export-templates/board-standard/enable', async (route) => {
      const target = templates.find((template) => template.templateId === 'board-standard');
      if (target) target.status = 'active';
      await route.fulfill({ json: { code: 200, message: 'ok', data: target } });
    });

    await page.goto('/reports/enterprise-export-templates');

    await expect(page.getByRole('heading', { name: '企业导出模板' })).toBeVisible();
    await expect(page.getByText('Board standard export')).toBeVisible();
    await expect(page.getByText('模板编码 board-standard')).toBeVisible();
    const boardCard = page.locator('.template-card').filter({ hasText: '模板编码 board-standard' });
    await expect(boardCard.getByText('版本 v1')).toBeVisible();
    await expect(boardCard.getByText('状态 启用')).toBeVisible();
    await expect(boardCard.getByText('Acme Finance Board Pack')).toBeVisible();

    await page.getByRole('button', { name: '停用 Board standard export' }).click();
    await expect(page.getByText('企业导出模板已停用：Board standard export')).toBeVisible();
    await expect(boardCard.getByText('状态 停用')).toBeVisible();

    await page.getByRole('button', { name: '启用 Board standard export' }).click();
    await expect(page.getByText('企业导出模板已启用：Board standard export')).toBeVisible();
    await expect(boardCard.getByText('状态 启用')).toBeVisible();

    await page.getByRole('button', { name: '编辑 Board standard export' }).click();
    await page.getByLabel('模板名称').fill('Board standard export v2');
    await page.getByRole('textbox', { name: '页脚', exact: true }).fill('Board confidential');
    await page.getByLabel('封面标题').fill('Board Strategy Pack 2026');
    await page.getByRole('button', { name: '保存企业导出模板' }).click();

    expect(updatePayload).toMatchObject({
      templateId: 'board-standard',
      name: 'Board standard export v2',
      status: 'active',
      brand: {
        companyName: 'Acme Finance',
        footer: 'Board confidential',
        layout: {
          coverTitle: 'Board Strategy Pack 2026'
        }
      }
    });
    await expect(page.getByText('企业导出模板已更新到版本 v2：Board standard export v2')).toBeVisible();
    const updatedBoardCard = page.locator('.template-card').filter({ hasText: '模板编码 board-standard' });
    await expect(updatedBoardCard.getByText('版本 v2', { exact: true })).toBeVisible();
    await expect(updatedBoardCard.getByText('Board confidential')).toBeVisible();

    await page.getByRole('button', { name: '查看版本 Board standard export v2' }).click();
    await expect(page.getByText('Board standard export v2 的版本历史')).toBeVisible();
    const versionPanel = page.getByLabel('Board standard export v2 的版本历史');
    await expect(versionPanel.getByText('版本快照 v2')).toBeVisible();
    await expect(versionPanel.getByText('Board confidential')).toBeVisible();

    await page.getByLabel('模板编码').fill('finance-quarterly');
    await page.getByLabel('模板名称').fill('Finance quarterly export');
    await page.getByLabel('企业名称').fill('Finance Group');
    await page.getByLabel('标识对象键').fill('logos/finance.svg');
    await page.getByRole('textbox', { name: '页眉', exact: true }).fill('Finance Quarterly Pack');
    await page.getByRole('textbox', { name: '页脚', exact: true }).fill('Internal use only');
    await page.getByLabel('字体').fill('Arial');
    await page.getByLabel('主色').fill('#0F766E');
    await page.getByLabel('封面标题').fill('Quarterly Management Report');
    await page.getByLabel('目录标题').fill('Contents');
    await page.getByLabel('章节标题前缀').fill('Part');
    await page.getByRole('button', { name: '创建企业导出模板' }).click();

    expect(createPayload).toMatchObject({
      templateId: 'finance-quarterly',
      name: 'Finance quarterly export',
      status: 'active',
      brand: {
        companyName: 'Finance Group',
        logoObjectKey: 'logos/finance.svg',
        header: 'Finance Quarterly Pack',
        footer: 'Internal use only',
        fontFamily: 'Arial',
        primaryColor: '#0F766E',
        layout: {
          coverTitle: 'Quarterly Management Report',
          tocTitle: 'Contents',
          bodyTitlePrefix: 'Part'
        }
      }
    });
    await expect(page.getByText('企业导出模板已创建：Finance quarterly export')).toBeVisible();
    await expect(page.getByText('模板编码 finance-quarterly')).toBeVisible();
    await expect(page.getByText('Finance Quarterly Pack')).toBeVisible();
  });
});
