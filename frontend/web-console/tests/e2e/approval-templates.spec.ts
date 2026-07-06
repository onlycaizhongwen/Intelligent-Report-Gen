import { expect, test } from '@playwright/test';

test.describe('Approval templates E2E', () => {
  test('creates and lists reusable approval templates', async ({ page }) => {
    let createPayload: Record<string, unknown> | null = null;
    let updatePayload: Record<string, unknown> | null = null;
    let templates = [
      {
        approvalTemplateId: 900,
        name: 'Legal review approval',
        description: 'Legal manager approval template',
        status: 'enabled',
        version: 1,
        usageCount: 1,
        usageRules: [
          {
            ruleId: 12,
            name: 'Legal review rule',
            status: 'draft'
          }
        ],
        steps: [
          {
            stepId: 'legalManager',
            approvalTitle: 'Legal manager approval',
            assigneeRoles: ['legal_manager'],
            approvalMode: 'all',
            slaHours: 6,
            slaEscalations: []
          }
        ],
        createdAt: '2026-06-29T08:00:00Z'
      }
    ];

    await page.route('**/api/v1/rules/approval-templates?**', async (route) => {
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
    await page.route('**/api/v1/rules/approval-templates/900/usage?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                ruleId: 12,
                name: 'Legal review rule',
                status: 'draft'
              }
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900/versions/1', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            approvalTemplateId: 900,
            name: 'Legal review approval',
            description: 'Legal manager approval template',
            status: 'enabled',
            version: 1,
            steps: [
              {
                stepId: 'legalManager',
                approvalTitle: 'Legal manager approval',
                assigneeRoles: ['legal_manager'],
                approvalMode: 'all',
                slaHours: 6,
                slaEscalations: []
              }
            ],
            createdAt: '2026-06-29T08:00:00Z'
          }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900/versions', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: [
            {
              approvalTemplateId: 900,
              name: 'Legal review approval',
              description: 'Legal manager and legal director approval template',
              status: 'enabled',
              version: 2,
              steps: [
                {
                  stepId: 'legalManager',
                  approvalTitle: 'Legal manager approval',
                  assigneeRoles: ['legal_manager'],
                  approvalMode: 'all',
                  slaHours: 6,
                  slaEscalations: []
                },
                {
                  stepId: 'legalDirector',
                  approvalTitle: 'Legal director approval',
                  assigneeRoles: ['legal_director'],
                  approvalMode: 'all',
                  slaHours: 12,
                  slaEscalations: []
                }
              ],
              createdAt: '2026-06-29T09:00:00Z'
            },
            {
              approvalTemplateId: 900,
              name: 'Legal review approval',
              description: 'Legal manager approval template',
              status: 'enabled',
              version: 1,
              steps: [
                {
                  stepId: 'legalManager',
                  approvalTitle: 'Legal manager approval',
                  assigneeRoles: ['legal_manager'],
                  approvalMode: 'all',
                  slaHours: 6,
                  slaEscalations: []
                }
              ],
              createdAt: '2026-06-29T08:00:00Z'
            }
          ]
        }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900/versions/diff**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            approvalTemplateId: 900,
            baseVersion: 1,
            targetVersion: 2,
            summary: {
              added: 1,
              removed: 0,
              modified: 1,
              unchanged: 0
            },
            changes: [
              {
                changeType: 'modified',
                stepId: 'legalManager',
                baseStep: {
                  stepId: 'legalManager',
                  approvalTitle: 'Legal manager approval',
                  assigneeRoles: ['legal_manager']
                },
                targetStep: {
                  stepId: 'legalManager',
                  approvalTitle: 'Legal director approval',
                  assigneeRoles: ['legal_director']
                }
              },
              {
                changeType: 'added',
                stepId: 'legalDirector',
                baseStep: null,
                targetStep: {
                  stepId: 'legalDirector',
                  approvalTitle: 'Legal director approval',
                  assigneeRoles: ['legal_director']
                }
              }
            ]
          }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900/versions/1/rollback', async (route) => {
      const target = templates.find((template) => template.approvalTemplateId === 900);
      if (target) {
        Object.assign(target, {
          name: 'Legal review approval',
          description: 'Legal manager approval template',
          status: 'enabled',
          version: 3,
          steps: [
            {
              stepId: 'legalManager',
              approvalTitle: 'Legal manager approval',
              assigneeRoles: ['legal_manager'],
              approvalMode: 'all',
              slaHours: 6,
              slaEscalations: []
            }
          ]
        });
      }
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            approvalTemplateId: 900,
            sourceVersion: 1,
            newVersion: 3,
            currentVersion: 3,
            changeReason: 'rollback'
          }
        }
      });
    });

    await page.route('**/api/v1/rules/approval-templates', async (route) => {
      createPayload = await route.request().postDataJSON();
      const created = {
        approvalTemplateId: 901,
        name: 'Finance two-level approval',
        description: 'Finance manager then finance director',
        status: 'enabled',
        version: 1,
        steps: [
          {
            stepId: 'financeManager',
            approvalTitle: 'Finance manager approval',
            assigneeRoles: ['finance_manager'],
            approvalMode: 'all',
            slaHours: 4,
            slaEscalations: [{ afterHours: 8, role: 'finance_director' }]
          },
          {
            stepId: 'financeDirector',
            approvalTitle: 'Finance director approval',
            assigneeRoles: ['finance_director'],
            approvalMode: 'all',
            slaHours: 8,
            slaEscalations: []
          }
        ],
        createdAt: '2026-06-29T09:00:00Z'
      };
      templates = [created, ...templates];
      await route.fulfill({
        json: { code: 200, message: 'ok', data: created }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900/disable', async (route) => {
      const target = templates.find((template) => template.approvalTemplateId === 900);
      if (target) {
        target.status = 'disabled';
        target.impact = {
          usageCount: target.usageCount,
          usageRules: target.usageRules
        };
      }
      await route.fulfill({
        json: { code: 200, message: 'ok', data: target }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900/enable', async (route) => {
      const target = templates.find((template) => template.approvalTemplateId === 900);
      if (target) {
        target.status = 'enabled';
      }
      await route.fulfill({
        json: { code: 200, message: 'ok', data: target }
      });
    });
    await page.route('**/api/v1/rules/approval-templates/900', async (route) => {
      updatePayload = await route.request().postDataJSON();
      const target = templates.find((template) => template.approvalTemplateId === 900);
      if (target) {
        Object.assign(target, updatePayload, { version: 2 });
      }
      await route.fulfill({
        json: { code: 200, message: 'ok', data: target }
      });
    });

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: 'Approval templates' }).click();

    await expect(page.getByRole('heading', { name: 'Approval Templates' })).toBeVisible();
    await expect(page.getByText('Legal review approval')).toBeVisible();
    await expect(page.getByText('Legal manager approval', { exact: true })).toBeVisible();
    await expect(page.getByText('Version 1')).toBeVisible();
    await expect(page.getByText('Usage 1 rule')).toBeVisible();
    await expect(page.getByText('Used by Legal review rule (draft)')).toBeVisible();
    await page.getByRole('button', { name: 'View usage Legal review approval' }).click();
    await expect(page.getByText('Usage details for Legal review approval')).toBeVisible();
    await expect(page.getByText('Page 1 of 1')).toBeVisible();
    await expect(page.getByText('Referenced by Legal review rule (draft)')).toBeVisible();
    await page.getByRole('button', { name: 'Disable Legal review approval' }).click();
    await expect(page.getByText('Disable referenced approval template?')).toBeVisible();
    await expect(page.getByText('Legal review rule (draft)', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Confirm disable' }).click();
    await expect(page.getByText('Approval template disabled: Legal review approval')).toBeVisible();
    await expect(page.getByText('Status disabled')).toBeVisible();
    await page.getByRole('button', { name: 'Enable Legal review approval' }).click();
    await expect(page.getByText('Approval template enabled: Legal review approval')).toBeVisible();
    await expect(page.getByText('Status enabled')).toBeVisible();
    await page.getByRole('button', { name: 'Edit Legal review approval' }).click();
    await expect(page.getByRole('button', { name: 'Save approval template changes' })).toBeVisible();
    await page.getByLabel('Template description').fill('Legal manager and legal director approval template');
    await page.getByLabel('Approval template steps').fill([
      'legalManager|Legal manager approval|legal_manager|all|6|',
      'legalDirector|Legal director approval|legal_director|all|12|'
    ].join('\n'));
    await page.getByRole('button', { name: 'Save approval template changes' }).click();
    expect(updatePayload).toMatchObject({
      name: 'Legal review approval',
      description: 'Legal manager and legal director approval template',
      status: 'enabled',
      steps: [
        {
          stepId: 'legalManager',
          approvalTitle: 'Legal manager approval',
          assigneeRoles: ['legal_manager'],
          approvalMode: 'all',
          slaHours: 6,
          slaEscalations: []
        },
        {
          stepId: 'legalDirector',
          approvalTitle: 'Legal director approval',
          assigneeRoles: ['legal_director'],
          approvalMode: 'all',
          slaHours: 12,
          slaEscalations: []
        }
      ]
    });
    await expect(page.getByText('Approval template updated to version 2: Legal review approval')).toBeVisible();
    await expect(page.getByText('Version 2', { exact: true })).toBeVisible();
    await expect(page.getByText('Legal director approval', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'View versions Legal review approval' }).click();
    await expect(page.getByText('Version history for Legal review approval')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Open version 2 Legal review approval' })).toBeVisible();
    await page.getByRole('button', { name: 'Compare version 1 to 2 Legal review approval' }).click();
    await expect(page.getByText('Version diff 1 -> 2 for Legal review approval')).toBeVisible();
    await expect(page.getByText('Added 1, removed 0, modified 1, unchanged 0')).toBeVisible();
    await expect(page.getByText('Modified legalManager')).toBeVisible();
    await expect(page.getByText('Added legalDirector')).toBeVisible();
    await page.getByRole('button', { name: 'Rollback version 1 Legal review approval' }).click();
    await expect(page.getByText('Rollback approval template version?')).toBeVisible();
    await expect(page.getByText('Legal review approval will restore version 1 as a new current version.')).toBeVisible();
    await page.getByRole('button', { name: 'Confirm rollback' }).click();
    await expect(page.getByText('Approval template rolled back to version 1 as version 3')).toBeVisible();
    await expect(page.getByText('Version 3', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'View versions Legal review approval' }).click();
    await page.getByRole('button', { name: 'Open version 1 Legal review approval' }).click();
    const versionSnapshot = page.getByLabel('Version snapshot for Legal review approval');
    await expect(versionSnapshot.getByText('Version snapshot 1 for Legal review approval')).toBeVisible();
    await expect(versionSnapshot.getByText('Legal manager approval', { exact: true })).toBeVisible();

    await page.getByLabel('Template name').fill('Finance two-level approval');
    await page.getByLabel('Template description').fill('Finance manager then finance director');
    await page.getByLabel('Approval template steps').fill([
      'financeManager|Finance manager approval|finance_manager|all|4|8:finance_director',
      'financeDirector|Finance director approval|finance_director|all|8|'
    ].join('\n'));
    await page.getByRole('button', { name: 'Create approval template' }).click();

    expect(createPayload).toMatchObject({
      name: 'Finance two-level approval',
      description: 'Finance manager then finance director',
      status: 'enabled',
      steps: [
        {
          stepId: 'financeManager',
          approvalTitle: 'Finance manager approval',
          assigneeRoles: ['finance_manager'],
          approvalMode: 'all',
          slaHours: 4,
          slaEscalations: [{ afterHours: 8, role: 'finance_director' }]
        },
        {
          stepId: 'financeDirector',
          approvalTitle: 'Finance director approval',
          assigneeRoles: ['finance_director'],
          approvalMode: 'all',
          slaHours: 8,
          slaEscalations: []
        }
      ]
    });
    await expect(page.getByText('Approval template created: Finance two-level approval')).toBeVisible();
    await expect(page.getByText('Finance manager approval')).toBeVisible();
    await expect(page.getByText('finance_manager')).toBeVisible();
    await expect(page.getByText('Escalate after 8h to finance_director')).toBeVisible();
  });
});
