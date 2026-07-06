import { expect, test } from '@playwright/test';

test.describe('Approval delegate rules E2E', () => {
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

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: 'Approval delegates' }).click();

    await expect(page.getByRole('heading', { name: 'Delegate schedule calendar' })).toBeVisible();
    const june29 = page.locator('.calendar-day').filter({
      has: page.locator('strong').filter({ hasText: /^2026-06-29$/ })
    });
    await expect(june29.getByText('finance_manager -> finance_delegate')).toBeVisible();
    await expect(page.locator('.calendar-day > strong').filter({ hasText: /^2026-07-01$/ })).toBeVisible();
    const recurring = page.locator('.calendar-recurring');
    await expect(recurring.getByText('Recurring / window based')).toBeVisible();
    await expect(recurring.getByText('legal_manager -> legal_delegate')).toBeVisible();
    await expect(recurring.getByText('Weekdays TUESDAY, THURSDAY')).toBeVisible();
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

    await page.goto('/dashboard');
    await page.getByRole('menuitem', { name: 'Approval delegates' }).click();
    await page.getByLabel('Delegate assignee role').fill('finance_manager');
    await page.getByLabel('Delegate role').fill('finance_delegate');
    await page.getByLabel('Delegate active from').fill('2026-06-26T08:00:00Z');
    await page.getByLabel('Delegate active to').fill('2026-06-26T18:00:00Z');
    await page.getByLabel('Delegate active dates').fill('2026-06-26');
    await page.getByRole('button', { name: 'Create delegate rule' }).click();

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
    await page.getByRole('menuitem', { name: 'Approval delegates' }).click();

    await expect(page.getByRole('heading', { name: 'Approval Delegate Rules' })).toBeVisible();
    const ruleList = page.getByLabel('Approval delegate rules');
    await expect(ruleList.getByText('legal_manager -> legal_delegate')).toBeVisible();
    await expect(ruleList.getByText('Status enabled')).toBeVisible();
    await page.getByRole('combobox', { name: 'Assignee organization role' }).click();
    await page
      .locator('.el-select-dropdown:visible')
      .getByRole('option', { name: 'Finance Center / Finance Manager / finance_manager / Fiona Manager' })
      .click();
    await expect(page.getByLabel('Delegate assignee role')).toHaveValue('finance_manager');
    await page.getByLabel('Delegate assignee role').fill('finance_manager');
    await expect(page.getByText('Assignee candidates Fiona Manager / Finance Center / Finance Manager')).toBeVisible();
    await expect(page.getByRole('combobox', { name: 'Delegate organization role' })).toBeVisible();
    await page.getByLabel('Delegate role').fill('finance_delegate');
    await expect(page.getByText('Delegate candidates Derek Delegate / Finance Center / Backup Approver')).toBeVisible();
    await page.getByLabel('Delegate active from').fill('2026-06-26T08:00:00Z');
    await page.getByLabel('Delegate active to').fill('2026-06-26T18:00:00Z');
    await page.getByLabel('Delegate active weekdays').fill('MONDAY,WEDNESDAY');
    await page.getByLabel('Delegate active dates').fill('2026-06-26,2026-06-28');
    await page.getByLabel('Delegate reason').fill('quarter close coverage');
    await page.getByRole('button', { name: 'Create delegate rule' }).click();

    expect(createPayload).toMatchObject({
      assigneeRole: 'finance_manager',
      delegateRole: 'finance_delegate',
      activeFrom: '2026-06-26T08:00:00Z',
      activeTo: '2026-06-26T18:00:00Z',
      activeWeekdays: ['MONDAY', 'WEDNESDAY'],
      activeDates: ['2026-06-26', '2026-06-28'],
      reason: 'quarter close coverage'
    });
    await expect(page.getByText('Delegate rule created: finance_manager -> finance_delegate')).toBeVisible();

    await page.getByLabel('Batch import delegate rules').fill([
      'audit_manager,audit_delegate,2026-06-29T08:00:00Z,2026-06-29T18:00:00Z,MONDAY,2026-06-29,audit cover',
      ',missing_delegate,,,,,missing assignee'
    ].join('\n'));
    await page.getByRole('button', { name: 'Import delegate rules' }).click();

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
    await expect(page.getByText('Delegate rules imported: 1 imported, 1 failed')).toBeVisible();
    await expect(page.getByText('Row 2 failed: assigneeRole is required')).toBeVisible();
    await expect(page.locator('.delegate-rule-card').filter({
      hasText: 'audit_manager -> audit_delegate'
    })).toBeVisible();

    const download = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export delegate rules' }).click();
    expect((await download).suggestedFilename()).toBe('approval-delegate-rules.csv');

    const financeRule = page.locator('.delegate-rule-card').filter({
      hasText: 'finance_manager -> finance_delegate'
    });
    await expect(financeRule).toBeVisible();
    await expect(financeRule.getByText('Status enabled')).toBeVisible();
    await financeRule.getByRole('button', { name: 'Edit' }).click();
    await expect(financeRule.getByRole('combobox', { name: 'Edit assignee organization role' })).toBeVisible();
    await expect(financeRule.getByRole('combobox', { name: 'Edit delegate organization role' })).toBeVisible();
    await financeRule.getByLabel('Edit assignee role').fill('finance_director');
    await financeRule.getByLabel('Edit delegate role').fill('finance_director_delegate');
    await financeRule.getByLabel('Edit active from').fill('2026-06-27T08:00:00Z');
    await financeRule.getByLabel('Edit active to').fill('2026-06-27T18:00:00Z');
    await financeRule.getByLabel('Edit active weekdays').fill('TUESDAY');
    await financeRule.getByLabel('Edit active dates').fill('2026-06-27');
    await financeRule.getByLabel('Edit reason').fill('director travel cover');
    await financeRule.getByRole('button', { name: 'Save edit' }).click();

    expect(updatePayload).toMatchObject({
      assigneeRole: 'finance_director',
      delegateRole: 'finance_director_delegate',
      activeFrom: '2026-06-27T08:00:00Z',
      activeTo: '2026-06-27T18:00:00Z',
      activeWeekdays: ['TUESDAY'],
      activeDates: ['2026-06-27'],
      reason: 'director travel cover'
    });
    await expect(page.getByText('Delegate rule updated: finance_director -> finance_director_delegate')).toBeVisible();
    const updatedFinanceRule = page.locator('.delegate-rule-card').filter({
      hasText: 'finance_director -> finance_director_delegate'
    });
    await expect(updatedFinanceRule).toBeVisible();
    await expect(updatedFinanceRule.getByText('Window 2026-06-27T08:00:00Z..2026-06-27T18:00:00Z')).toBeVisible();
    await expect(updatedFinanceRule.getByText('Weekdays TUESDAY')).toBeVisible();
    await expect(updatedFinanceRule.getByText('Dates 2026-06-27')).toBeVisible();
    await expect(updatedFinanceRule.getByText('Reason director travel cover')).toBeVisible();
    await updatedFinanceRule.getByLabel('Disable reason').fill('manager returned');
    await updatedFinanceRule.getByRole('button', { name: 'Disable' }).click();

    expect(disablePayload).toMatchObject({ reason: 'manager returned' });
    await expect(page.getByText('Delegate rule disabled: finance_director -> finance_director_delegate')).toBeVisible();
    await expect(updatedFinanceRule.getByText('Status disabled')).toBeVisible();
    await updatedFinanceRule.getByLabel('Enable reason').fill('manager away again');
    await updatedFinanceRule.getByRole('button', { name: 'Enable' }).click();

    expect(enablePayload).toMatchObject({ reason: 'manager away again' });
    await expect(page.getByText('Delegate rule enabled: finance_director -> finance_director_delegate')).toBeVisible();
    await expect(updatedFinanceRule.getByText('Status enabled')).toBeVisible();
  });
});
