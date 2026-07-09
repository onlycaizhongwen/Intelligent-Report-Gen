import { expect, test } from '@playwright/test';

test.describe('Approval templates E2E', () => {
  test('P0：审批模板页面使用中文业务文案', async ({ page }) => {
    await page.route('**/api/v1/rules/approval-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { items: [], page: 1, pageSize: 20, total: 0 }
        }
      });
    });

    await page.goto('/rules/approval-templates');

    await expect(page.getByRole('heading', { name: '审批模板' })).toBeVisible();
    await expect(page.getByText('维护规则流程可复用的审批步骤、角色和 SLA。')).toBeVisible();
    await expect(page.getByLabel('模板名称')).toBeVisible();
    await expect(page.getByLabel('模板描述')).toBeVisible();
    await expect(page.getByLabel('审批步骤')).toBeVisible();
    await expect(page.getByLabel('审批步骤')).toHaveAttribute('placeholder', /步骤编码/);
    await expect(page.getByRole('button', { name: '创建审批模板' })).toBeVisible();
    await expect(page.getByText('暂无审批模板')).toBeVisible();

    const visibleText = await page.locator('body').innerText();
    expect(visibleText).not.toMatch(/Approval Templates|Manage reusable|Refresh|Existing templates|No approval templates|Name|Description|Status|Steps|Create approval template|stepId|approvalTitle/);
  });

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

    await page.goto('/rules/approval-templates');

    await expect(page.getByRole('heading', { name: '审批模板' })).toBeVisible();
    await expect(page.getByText('Legal review approval')).toBeVisible();
    await expect(page.getByText('Legal manager approval', { exact: true })).toBeVisible();
    await expect(page.getByText('版本 1')).toBeVisible();
    await expect(page.getByText('使用 1 条规则')).toBeVisible();
    await expect(page.getByText('被 Legal review rule 引用（草稿）')).toBeVisible();
    await page.getByRole('button', { name: '查看使用情况 Legal review approval' }).click();
    await expect(page.getByText('Legal review approval 的使用明细')).toBeVisible();
    await expect(page.getByText('第 1 / 1 页')).toBeVisible();
    await expect(page.getByLabel('Legal review approval 的使用明细').getByText('被 Legal review rule 引用（草稿）')).toBeVisible();
    await page.getByRole('button', { name: '停用 Legal review approval' }).click();
    await expect(page.getByText('确认停用被引用的审批模板？')).toBeVisible();
    await expect(page.getByText('Legal review rule（草稿）', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '确认停用' }).click();
    await expect(page.getByText('审批模板已停用：Legal review approval')).toBeVisible();
    await expect(page.getByText('状态 停用')).toBeVisible();
    await page.getByRole('button', { name: '启用 Legal review approval' }).click();
    await expect(page.getByText('审批模板已启用：Legal review approval')).toBeVisible();
    await expect(page.getByText('状态 启用')).toBeVisible();
    await page.getByRole('button', { name: '编辑 Legal review approval' }).click();
    await expect(page.getByRole('button', { name: '保存审批模板' })).toBeVisible();
    await page.getByLabel('模板描述').fill('Legal manager and legal director approval template');
    await page.getByLabel('审批步骤').fill([
      'legalManager|Legal manager approval|legal_manager|all|6|',
      'legalDirector|Legal director approval|legal_director|all|12|'
    ].join('\n'));
    await page.getByRole('button', { name: '保存审批模板' }).click();
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
    await expect(page.getByText('审批模板已更新到版本 2：Legal review approval')).toBeVisible();
    await expect(page.getByText('版本 2', { exact: true })).toBeVisible();
    await expect(page.getByText('Legal director approval', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '查看版本 Legal review approval' }).click();
    await expect(page.getByText('Legal review approval 的版本历史')).toBeVisible();
    await expect(page.getByRole('button', { name: '打开版本 2 Legal review approval' })).toBeVisible();
    await page.getByRole('button', { name: '比较版本 1 到 2 Legal review approval' }).click();
    await expect(page.getByText('Legal review approval 的版本差异 1 -> 2')).toBeVisible();
    await expect(page.getByText(/新增 1，删除 0，\s*修改 1，未变 0/)).toBeVisible();
    await expect(page.getByText('修改 legalManager')).toBeVisible();
    await expect(page.getByText('新增 legalDirector')).toBeVisible();
    await page.getByRole('button', { name: '回滚版本 1 Legal review approval' }).click();
    await expect(page.getByText('确认回滚审批模板版本？')).toBeVisible();
    await expect(page.getByText('Legal review approval 将把版本 1 恢复为新的当前版本。')).toBeVisible();
    await page.getByRole('button', { name: '确认回滚' }).click();
    await expect(page.getByText('审批模板已从版本 1 回滚，新版本 3')).toBeVisible();
    await expect(page.getByText('版本 3', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: '查看版本 Legal review approval' }).click();
    await page.getByRole('button', { name: '打开版本 1 Legal review approval' }).click();
    const versionSnapshot = page.getByLabel('Legal review approval 的版本快照');
    await expect(versionSnapshot.getByText('Legal review approval 的版本快照 1')).toBeVisible();
    await expect(versionSnapshot.getByText('Legal manager approval', { exact: true })).toBeVisible();

    await page.getByLabel('模板名称').fill('Finance two-level approval');
    await page.getByLabel('模板描述').fill('Finance manager then finance director');
    await page.getByLabel('审批步骤').fill([
      'financeManager|Finance manager approval|finance_manager|all|4|8:finance_director',
      'financeDirector|Finance director approval|finance_director|all|8|'
    ].join('\n'));
    await page.getByRole('button', { name: '创建审批模板' }).click();

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
    await expect(page.getByText('审批模板已创建：Finance two-level approval')).toBeVisible();
    await expect(page.getByText('Finance manager approval')).toBeVisible();
    await expect(page.getByText('finance_manager')).toBeVisible();
    await expect(page.getByText('8h 后升级给 finance_director')).toBeVisible();
  });
});
