import { expect, test } from '@playwright/test';

test.describe('Approval delegate rules E2E', () => {
  test('P0：审批委托页面使用中文业务文案', async ({ page }) => {
    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { departments: [], roles: [] }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-delegate-rules?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { items: [], page: 1, pageSize: 20, total: 0 }
        }
      });
    });

    await page.goto('/rules/delegate-rules');

    await expect(page.getByRole('heading', { name: '审批委托规则' })).toBeVisible();
    await expect(page.getByText('配置临时代理角色，保障审批任务不断档。')).toBeVisible();
    await expect(page.getByLabel('原审批角色')).toBeVisible();
    await expect(page.getByLabel('代理角色')).toBeVisible();
    await expect(page.getByRole('button', { name: '创建委托规则' })).toBeVisible();
    await expect(page.getByRole('heading', { name: '委托日程' })).toBeVisible();
    await expect(page.getByLabel('批量导入委托规则')).toHaveAttribute('placeholder', /原审批角色,代理角色/);
    await expect(page.getByText('暂无委托规则')).toBeVisible();

    const visibleText = await page.locator('body').innerText();
    expect(visibleText).not.toMatch(/Approval Delegate Rules|Configure temporary|Assignee role|Delegate role|Active from|Active to|Active weekdays|Active dates|Reason|Create delegate rule|Delegate schedule calendar|Existing delegate rules|No delegate rules|Refresh|assigneeRole|delegateRole|activeFrom|activeTo/);
  });

  test('renders a schedule calendar from active dates and recurring delegate rules', async ({ page }) => {
    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { departments: [], roles: [] }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-delegate-rules?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                delegateRuleId: 610,
                assigneeRole: 'finance_manager',
                delegateRole: 'finance_delegate',
                activeFrom: '2026-06-29T08:00:00Z',
                activeTo: '2026-06-29T18:00:00Z',
                activeWeekdays: ['MONDAY'],
                activeDates: ['2026-06-29', '2026-07-01'],
                status: 'enabled',
                reason: 'quarter close',
                createdAt: '2026-06-28T09:00:00Z'
              },
              {
                delegateRuleId: 611,
                assigneeRole: 'legal_manager',
                delegateRole: 'legal_delegate',
                activeFrom: '2026-06-29T08:00:00Z',
                activeTo: '2026-07-05T18:00:00Z',
                activeWeekdays: ['TUESDAY', 'THURSDAY'],
                activeDates: [],
                status: 'enabled',
                reason: 'travel cover',
                createdAt: '2026-06-28T09:00:00Z'
              }
            ],
            page: 1,
            pageSize: 20,
            total: 2
          }
        }
      });
    });

    await page.goto('/rules/delegate-rules');

    await expect(page.getByRole('heading', { name: '委托日程' })).toBeVisible();
    const june29 = page.locator('.calendar-day').filter({
      has: page.locator('strong').filter({ hasText: /^2026-06-29$/ })
    });
    await expect(june29.getByText('finance_manager -> finance_delegate')).toBeVisible();
    await expect(page.locator('.calendar-day > strong').filter({ hasText: /^2026-07-01$/ })).toBeVisible();
    const recurring = page.locator('.calendar-recurring');
    await expect(recurring.getByText('重复 / 时间窗口规则')).toBeVisible();
    await expect(recurring.getByText('legal_manager -> legal_delegate')).toBeVisible();
    await expect(recurring.getByText('星期 TUESDAY, THURSDAY')).toBeVisible();
  });

  test('shows backend schedule conflict errors when creating a delegate rule', async ({ page }) => {
    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { departments: [], roles: [] }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-delegate-rules?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: { items: [], page: 1, pageSize: 20, total: 0 }
        }
      });
    });
    await page.route('**/api/v1/rules/approval-delegate-rules', async (route) => {
      await route.fulfill({
        status: 400,
        json: {
          code: 400,
          message: 'approval delegate rule schedule conflicts with rule: 501',
          data: null
        }
      });
    });

    await page.goto('/rules/delegate-rules');
    await page.getByLabel('原审批角色').fill('finance_manager');
    await page.getByLabel('代理角色').fill('finance_delegate');
    await page.getByLabel('委托生效开始').fill('2026-06-26T08:00:00Z');
    await page.getByLabel('委托生效结束').fill('2026-06-26T18:00:00Z');
    await page.getByLabel('委托生效日期').fill('2026-06-26');
    await page.getByRole('button', { name: '创建委托规则' }).click();

    await expect(page.getByText('approval delegate rule schedule conflicts with rule: 501')).toBeVisible();
  });

  test('creates an approval delegate rule from the rule management menu', async ({ page }) => {
    let createPayload: Record<string, unknown> | null = null;
    let importPayload: Record<string, unknown> | null = null;
    let updatePayload: Record<string, unknown> | null = null;
    let disablePayload: Record<string, unknown> | null = null;
    let enablePayload: Record<string, unknown> | null = null;
    let delegateRules = [
      {
        delegateRuleId: 500,
        assigneeRole: 'legal_manager',
        delegateRole: 'legal_delegate',
        activeFrom: '2026-06-25T08:00:00Z',
        activeTo: '2026-06-30T18:00:00Z',
        activeWeekdays: ['MONDAY'],
        activeDates: ['2026-06-25'],
        status: 'enabled',
        reason: 'legal backup',
        createdByUserId: 66,
        createdAt: '2026-06-25T09:00:00Z'
      }
    ];

    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            departments: [
              {
                department: 'Finance Center',
                positions: [
                  {
                    position: 'Backup Approver',
                    roles: ['finance_delegate'],
                    users: [
                      {
                        userId: 72,
                        username: 'fin.delegate',
                        displayName: 'Derek Delegate',
                        roles: ['finance_delegate']
                      }
                    ]
                  },
                  {
                    position: 'Finance Manager',
                    roles: ['finance_manager'],
                    users: [
                      {
                        userId: 71,
                        username: 'fin.manager',
                        displayName: 'Fiona Manager',
                        roles: ['finance_manager']
                      }
                    ]
                  }
                ]
              },
              {
                department: 'Legal Center',
                positions: [
                  {
                    position: 'Legal Manager',
                    roles: ['legal_manager'],
                    users: [
                      {
                        userId: 73,
                        username: 'legal.manager',
                        displayName: 'Laura Legal',
                        roles: ['legal_manager']
                      }
                    ]
                  }
                ]
              }
            ],
            roles: [
              {
                role: 'finance_delegate',
                department: 'Finance Center',
                position: 'Backup Approver',
                users: [
                  {
                    userId: 72,
                    username: 'fin.delegate',
                    displayName: 'Derek Delegate',
                    roles: ['finance_delegate']
                  }
                ]
              },
              {
                role: 'finance_manager',
                department: 'Finance Center',
                position: 'Finance Manager',
                users: [
                  {
                    userId: 71,
                    username: 'fin.manager',
                    displayName: 'Fiona Manager',
                    roles: ['finance_manager']
                  }
                ]
              },
              {
                role: 'legal_manager',
                department: 'Legal Center',
                position: 'Legal Manager',
                users: [
                  {
                    userId: 73,
                    username: 'legal.manager',
                    displayName: 'Laura Legal',
                    roles: ['legal_manager']
                  }
                ]
              }
            ]
          }
        }
      });
    });

    await page.route('**/api/v1/rules/approval-delegate-rules?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: delegateRules,
            page: 1,
            pageSize: 20,
            total: delegateRules.length
          }
        }
      });
    });

    await page.route('**/api/v1/rules/approval-delegate-rules', async (route) => {
      createPayload = await route.request().postDataJSON();
      delegateRules = [
        {
          delegateRuleId: 501,
          assigneeRole: 'finance_manager',
          delegateRole: 'finance_delegate',
          activeFrom: '2026-06-26T08:00:00Z',
          activeTo: '2026-06-26T18:00:00Z',
          activeWeekdays: ['MONDAY', 'WEDNESDAY'],
          activeDates: ['2026-06-26', '2026-06-28'],
          status: 'enabled',
          reason: 'quarter close coverage',
          createdByUserId: 66,
          createdAt: '2026-06-25T10:00:00Z'
        },
        ...delegateRules
      ];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            delegateRuleId: 501,
            assigneeRole: 'finance_manager',
            delegateRole: 'finance_delegate',
            activeFrom: '2026-06-26T08:00:00Z',
            activeTo: '2026-06-26T18:00:00Z',
            activeWeekdays: ['MONDAY', 'WEDNESDAY'],
            activeDates: ['2026-06-26', '2026-06-28'],
            status: 'enabled',
            reason: 'quarter close coverage',
            createdByUserId: 66,
            createdAt: '2026-06-25T10:00:00Z'
          }
        }
      });
    });

    await page.route('**/api/v1/rules/approval-delegate-rules/batch-import', async (route) => {
      importPayload = await route.request().postDataJSON();
      delegateRules = [
        {
          delegateRuleId: 502,
          assigneeRole: 'audit_manager',
          delegateRole: 'audit_delegate',
          activeFrom: '2026-06-29T08:00:00Z',
          activeTo: '2026-06-29T18:00:00Z',
          activeWeekdays: ['MONDAY'],
          activeDates: ['2026-06-29'],
          status: 'enabled',
          reason: 'audit cover',
          createdByUserId: 66,
          createdAt: '2026-06-29T09:00:00Z'
        },
        ...delegateRules
      ];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            imported: 1,
            failed: 1,
            results: [
              {
                rowNumber: 1,
                status: 'imported',
                delegateRuleId: 502,
                assigneeRole: 'audit_manager',
                delegateRole: 'audit_delegate'
              },
              {
                rowNumber: 2,
                status: 'failed',
                reason: 'assigneeRole is required'
              }
            ]
          }
        }
      });
    });

    await page.route('**/api/v1/rules/approval-delegate-rules/501', async (route) => {
      updatePayload = await route.request().postDataJSON();
      delegateRules = delegateRules.map((rule) => (
        rule.delegateRuleId === 501
          ? {
              ...rule,
              assigneeRole: 'finance_director',
              delegateRole: 'finance_director_delegate',
              activeFrom: '2026-06-27T08:00:00Z',
              activeTo: '2026-06-27T18:00:00Z',
              activeWeekdays: ['TUESDAY'],
              activeDates: ['2026-06-27'],
              reason: 'director travel cover'
            }
          : rule
      ));
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: delegateRules.find((rule) => rule.delegateRuleId === 501)
        }
      });
    });

    await page.route('**/api/v1/rules/approval-delegate-rules/501/disable', async (route) => {
      disablePayload = await route.request().postDataJSON();
      delegateRules = delegateRules.map((rule) => (
        rule.delegateRuleId === 501
          ? { ...rule, status: 'disabled', reason: 'manager returned' }
          : rule
      ));
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: delegateRules.find((rule) => rule.delegateRuleId === 501)
        }
      });
    });

    await page.route('**/api/v1/rules/approval-delegate-rules/501/enable', async (route) => {
      enablePayload = await route.request().postDataJSON();
      delegateRules = delegateRules.map((rule) => (
        rule.delegateRuleId === 501
          ? { ...rule, status: 'enabled', reason: 'manager away again' }
          : rule
      ));
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: delegateRules.find((rule) => rule.delegateRuleId === 501)
        }
      });
    });

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: '审批委托' }).click();

    await expect(page.getByRole('heading', { name: '审批委托规则' })).toBeVisible();
    const ruleList = page.getByLabel('审批委托规则列表');
    await expect(ruleList.getByText('legal_manager -> legal_delegate')).toBeVisible();
    await expect(ruleList.getByText('状态 启用')).toBeVisible();
    await page.getByRole('combobox', { name: '原审批组织角色' }).click();
    await page
      .locator('.el-select-dropdown:visible')
      .getByRole('option', { name: 'Finance Center / Finance Manager / finance_manager / Fiona Manager' })
      .click();
    await expect(page.getByLabel('原审批角色')).toHaveValue('finance_manager');
    await page.getByLabel('原审批角色').fill('finance_manager');
    await expect(page.getByText('原审批候选人 Fiona Manager / Finance Center / Finance Manager')).toBeVisible();
    await expect(page.getByRole('combobox', { name: '代理组织角色' })).toBeVisible();
    await page.getByLabel('代理角色').fill('finance_delegate');
    await expect(page.getByText('代理候选人 Derek Delegate / Finance Center / Backup Approver')).toBeVisible();
    await page.getByLabel('委托生效开始').fill('2026-06-26T08:00:00Z');
    await page.getByLabel('委托生效结束').fill('2026-06-26T18:00:00Z');
    await page.getByLabel('委托生效星期').fill('MONDAY,WEDNESDAY');
    await page.getByLabel('委托生效日期').fill('2026-06-26,2026-06-28');
    await page.getByLabel('委托原因').fill('quarter close coverage');
    await page.getByRole('button', { name: '创建委托规则' }).click();

    expect(createPayload).toMatchObject({
      assigneeRole: 'finance_manager',
      delegateRole: 'finance_delegate',
      activeFrom: '2026-06-26T08:00:00Z',
      activeTo: '2026-06-26T18:00:00Z',
      activeWeekdays: ['MONDAY', 'WEDNESDAY'],
      activeDates: ['2026-06-26', '2026-06-28'],
      reason: 'quarter close coverage'
    });
    await expect(page.getByText('委托规则已创建：finance_manager -> finance_delegate')).toBeVisible();

    await page.getByLabel('批量导入委托规则').fill([
      'audit_manager,audit_delegate,2026-06-29T08:00:00Z,2026-06-29T18:00:00Z,MONDAY,2026-06-29,audit cover',
      ',missing_delegate,,,,,missing assignee'
    ].join('\n'));
    await page.getByRole('button', { name: '导入委托规则' }).click();

    expect(importPayload).toMatchObject({
      rules: [
        {
          assigneeRole: 'audit_manager',
          delegateRole: 'audit_delegate',
          activeFrom: '2026-06-29T08:00:00Z',
          activeTo: '2026-06-29T18:00:00Z',
          activeWeekdays: ['MONDAY'],
          activeDates: ['2026-06-29'],
          reason: 'audit cover'
        },
        {
          assigneeRole: '',
          delegateRole: 'missing_delegate',
          activeWeekdays: [],
          activeDates: [],
          reason: 'missing assignee'
        }
      ]
    });
    await expect(page.getByText('委托规则导入完成：成功 1 行，失败 1 行')).toBeVisible();
    await expect(page.getByText('第 2 行失败：assigneeRole is required')).toBeVisible();
    await expect(page.locator('.delegate-rule-card').filter({
      hasText: 'audit_manager -> audit_delegate'
    })).toBeVisible();

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: '导出委托规则' }).click();
    expect((await download).suggestedFilename()).toBe('approval-delegate-rules.csv');

    const financeRule = page.locator('.delegate-rule-card').filter({
      hasText: 'finance_manager -> finance_delegate'
    });
    await expect(financeRule).toBeVisible();
    await expect(financeRule.getByText('状态 启用')).toBeVisible();
    await financeRule.getByRole('button', { name: '编辑' }).click();
    await expect(financeRule.getByRole('combobox', { name: '编辑原审批组织角色' })).toBeVisible();
    await expect(financeRule.getByRole('combobox', { name: '编辑代理组织角色' })).toBeVisible();
    await financeRule.getByLabel('编辑原审批角色').fill('finance_director');
    await financeRule.getByLabel('编辑代理角色').fill('finance_director_delegate');
    await financeRule.getByLabel('编辑生效开始').fill('2026-06-27T08:00:00Z');
    await financeRule.getByLabel('编辑生效结束').fill('2026-06-27T18:00:00Z');
    await financeRule.getByLabel('编辑生效星期').fill('TUESDAY');
    await financeRule.getByLabel('编辑生效日期').fill('2026-06-27');
    await financeRule.getByLabel('编辑原因').fill('director travel cover');
    await financeRule.getByRole('button', { name: '保存编辑' }).click();

    expect(updatePayload).toMatchObject({
      assigneeRole: 'finance_director',
      delegateRole: 'finance_director_delegate',
      activeFrom: '2026-06-27T08:00:00Z',
      activeTo: '2026-06-27T18:00:00Z',
      activeWeekdays: ['TUESDAY'],
      activeDates: ['2026-06-27'],
      reason: 'director travel cover'
    });
    await expect(page.getByText('委托规则已更新：finance_director -> finance_director_delegate')).toBeVisible();
    const updatedFinanceRule = page.locator('.delegate-rule-card').filter({
      hasText: 'finance_director -> finance_director_delegate'
    });
    await expect(updatedFinanceRule).toBeVisible();
    await expect(updatedFinanceRule.getByText('生效窗口 2026-06-27T08:00:00Z..2026-06-27T18:00:00Z')).toBeVisible();
    await expect(updatedFinanceRule.getByText('星期 TUESDAY')).toBeVisible();
    await expect(updatedFinanceRule.getByText('日期 2026-06-27')).toBeVisible();
    await expect(updatedFinanceRule.getByText('原因 director travel cover')).toBeVisible();
    await updatedFinanceRule.getByLabel('停用原因').fill('manager returned');
    await updatedFinanceRule.getByRole('button', { name: '停用' }).click();

    expect(disablePayload).toMatchObject({ reason: 'manager returned' });
    await expect(page.getByText('委托规则已停用：finance_director -> finance_director_delegate')).toBeVisible();
    await expect(updatedFinanceRule.getByText('状态 停用')).toBeVisible();
    await updatedFinanceRule.getByLabel('启用原因').fill('manager away again');
    await updatedFinanceRule.getByRole('button', { name: '启用' }).click();

    expect(enablePayload).toMatchObject({ reason: 'manager away again' });
    await expect(page.getByText('委托规则已启用：finance_director -> finance_director_delegate')).toBeVisible();
    await expect(updatedFinanceRule.getByText('状态 启用')).toBeVisible();
  });
});
