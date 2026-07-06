import { expect, test } from '@playwright/test';

test.describe('Enterprise export templates E2E', () => {
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

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: 'Enterprise export templates' }).click();

    await expect(page.getByRole('heading', { name: 'Enterprise Export Templates' })).toBeVisible();
    await expect(page.getByText('Board standard export')).toBeVisible();
    await expect(page.getByText('Template board-standard')).toBeVisible();
    const boardCard = page.locator('.template-card').filter({ hasText: 'Template board-standard' });
    await expect(boardCard.getByText('Version v1')).toBeVisible();
    await expect(boardCard.getByText('Status active')).toBeVisible();
    await expect(boardCard.getByText('Acme Finance Board Pack')).toBeVisible();

    await page.getByRole('button', { name: 'Disable Board standard export' }).click();
    await expect(page.getByText('Enterprise export template disabled: Board standard export')).toBeVisible();
    await expect(boardCard.getByText('Status disabled')).toBeVisible();

    await page.getByRole('button', { name: 'Enable Board standard export' }).click();
    await expect(page.getByText('Enterprise export template enabled: Board standard export')).toBeVisible();
    await expect(boardCard.getByText('Status active')).toBeVisible();

    await page.getByRole('button', { name: 'Edit Board standard export' }).click();
    await page.getByLabel('Template name').fill('Board standard export v2');
    await page.getByLabel('Footer').fill('Board confidential');
    await page.getByLabel('Cover title').fill('Board Strategy Pack 2026');
    await page.getByRole('button', { name: 'Save enterprise export template changes' }).click();

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
    await expect(page.getByText('Enterprise export template updated to version v2: Board standard export v2')).toBeVisible();
    const updatedBoardCard = page.locator('.template-card').filter({ hasText: 'Template board-standard' });
    await expect(updatedBoardCard.getByText('Version v2', { exact: true })).toBeVisible();
    await expect(updatedBoardCard.getByText('Board confidential')).toBeVisible();

    await page.getByRole('button', { name: 'View versions Board standard export v2' }).click();
    await expect(page.getByText('Version history for Board standard export v2')).toBeVisible();
    const versionPanel = page.getByLabel('Version history for Board standard export v2');
    await expect(versionPanel.getByText('Version snapshot v2')).toBeVisible();
    await expect(versionPanel.getByText('Board confidential')).toBeVisible();

    await page.getByLabel('Template ID').fill('finance-quarterly');
    await page.getByLabel('Template name').fill('Finance quarterly export');
    await page.getByLabel('Company name').fill('Finance Group');
    await page.getByLabel('Logo object key').fill('logos/finance.svg');
    await page.getByLabel('Header').fill('Finance Quarterly Pack');
    await page.getByLabel('Footer').fill('Internal use only');
    await page.getByLabel('Font family').fill('Arial');
    await page.getByLabel('Primary color').fill('#0F766E');
    await page.getByLabel('Cover title').fill('Quarterly Management Report');
    await page.getByLabel('TOC title').fill('Contents');
    await page.getByLabel('Section title prefix').fill('Part');
    await page.getByRole('button', { name: 'Create enterprise export template' }).click();

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
    await expect(page.getByText('Enterprise export template created: Finance quarterly export')).toBeVisible();
    await expect(page.getByText('Template finance-quarterly')).toBeVisible();
    await expect(page.getByText('Finance Quarterly Pack')).toBeVisible();
  });
});
