import { expect, test } from '@playwright/test';

test.describe('Rule approval template application E2E', () => {
  async function routeCommonReferenceApis(page: Parameters<Parameters<typeof test>[1]>[0]['page']) {
    await page.route('**/api/v1/reports?page=1&pageSize=20', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], page: 1, pageSize: 20, total: 0 } } });
    });
    await page.route('**/api/v1/users?page=1&pageSize=20', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], page: 1, pageSize: 20, total: 0 } } });
    });
    await page.route('**/api/v1/organization-directory', async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { departments: [], roles: [] } } });
    });
  }

  async function routeApprovalTemplateApi(page: Parameters<Parameters<typeof test>[1]>[0]['page']) {
    await page.route('**/api/v1/rules/approval-templates?**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                approvalTemplateId: 901,
                version: 1,
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
              }
            ],
            page: 1,
            pageSize: 20,
            total: 1
          }
        }
      });
    });
  }

  async function routeRuleChildApis(page: Parameters<Parameters<typeof test>[1]>[0]['page'], ruleId: number) {
    await page.route(`**/api/v1/rules/${ruleId}/approval-records?**`, async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], page: 1, pageSize: 10, total: 0 } } });
    });
    await page.route(`**/api/v1/rules/${ruleId}/action-executions?**`, async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], page: 1, pageSize: 10, total: 0 } } });
    });
    await page.route(`**/api/v1/rules/${ruleId}/runs?**`, async (route) => {
      await route.fulfill({ json: { code: 200, message: 'ok', data: { items: [], page: 1, pageSize: 5, total: 0 } } });
    });
  }

  test('applies reusable approval template steps to the rule canvas', async ({ page }) => {
    let ruleDefinition = {
      nodes: [
        { id: 'start', type: 'start' },
        { id: 'end', type: 'end' }
      ],
      edges: [
        { source: 'start', target: 'end' }
      ]
    };
    let savePayload: Record<string, unknown> | null = null;

    const ruleResponse = () => ({
      ruleId: 12,
      name: 'Template enabled rule',
      status: 'draft',
      versionId: 3,
      definition: ruleDefinition
    });

    await page.route('**/api/v1/rules?page=1&pageSize=10', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [ruleResponse()],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await routeCommonReferenceApis(page);
    await routeApprovalTemplateApi(page);
    await routeRuleChildApis(page, 12);
    await page.route('**/api/v1/rules/12', async (route) => {
      if (route.request().method() !== 'PUT') {
        await route.fallback();
        return;
      }
      savePayload = await route.request().postDataJSON();
      ruleDefinition = savePayload.definition as typeof ruleDefinition;
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });

    await page.goto('/rules');

    await page.getByLabel('Approval template selector').selectOption('901');
    await page.getByLabel('Insert approval template after node').selectOption('start');
    await expect(page.getByText('Preview path start -> tpl901_financeManager -> tpl901_financeDirector -> end')).toBeVisible();
    await page.getByRole('button', { name: 'Apply approval template' }).click();

    await expect(page.getByTestId('rule-flow-node-tpl901_financeManager')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-tpl901_financeDirector')).toBeVisible();
    await expect(page.getByText('start -> tpl901_financeManager', { exact: true })).toBeVisible();
    await expect(page.getByText('tpl901_financeManager -> tpl901_financeDirector', { exact: true })).toBeVisible();
    await expect(page.getByText('tpl901_financeDirector -> end', { exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Save canvas changes' }).click();

    expect(savePayload).toMatchObject({
      status: 'draft',
      definition: {
        nodes: expect.arrayContaining([
          expect.objectContaining({
            id: 'tpl901_financeManager',
            type: 'approval',
            approvalTemplateId: 901,
            approvalTemplateVersion: 1,
            approvalTemplateStepId: 'financeManager',
            approvalTitle: 'Finance manager approval',
            assigneeRoles: ['finance_manager'],
            approvalMode: 'all',
            slaHours: 4,
            slaEscalations: [{ afterHours: 8, role: 'finance_director' }]
          }),
          expect.objectContaining({
            id: 'tpl901_financeDirector',
            type: 'approval',
            approvalTemplateId: 901,
            approvalTemplateVersion: 1,
            approvalTemplateStepId: 'financeDirector',
            approvalTitle: 'Finance director approval',
            assigneeRoles: ['finance_director'],
            approvalMode: 'all',
            slaHours: 8
          })
        ]),
        edges: expect.arrayContaining([
          expect.objectContaining({ source: 'start', target: 'tpl901_financeManager' }),
          expect.objectContaining({ source: 'tpl901_financeDirector', target: 'end' }),
          expect.objectContaining({ source: 'tpl901_financeManager', target: 'tpl901_financeDirector' })
        ])
      }
    });
    expect((savePayload?.definition as typeof ruleDefinition).edges).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ source: 'start', target: 'end' })
      ])
    );
  });

  test('blocks saving a rule canvas when local topology cannot reach end', async ({ page }) => {
    const ruleDefinition = {
      nodes: [
        { id: 'start', type: 'start' },
        { id: 'end', type: 'end' }
      ],
      edges: [
        { source: 'start', target: 'end' }
      ]
    };
    let saveRequests = 0;

    const ruleResponse = () => ({
      ruleId: 14,
      name: 'Invalid topology guard rule',
      status: 'draft',
      versionId: 1,
      definition: ruleDefinition
    });

    await page.route('**/api/v1/rules?page=1&pageSize=10', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [ruleResponse()],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await routeCommonReferenceApis(page);
    await routeApprovalTemplateApi(page);
    await routeRuleChildApis(page, 14);
    await page.route('**/api/v1/rules/14', async (route) => {
      if (route.request().method() !== 'PUT') {
        await route.fallback();
        return;
      }
      saveRequests += 1;
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });

    await page.goto('/rules');

    await page.getByLabel('Delete edge').selectOption('start->end');
    await page.getByRole('button', { name: 'Delete edge' }).click();
    await page.getByRole('button', { name: 'Save canvas changes' }).click();

    await expect(page.getByText('Rule topology path cannot reach end')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-start')).toContainText('Topology blocked');
    expect(saveRequests).toBe(0);
  });

  test('marks the broken incoming path when a downstream node cannot reach end', async ({ page }) => {
    const ruleDefinition = {
      nodes: [
        { id: 'start', type: 'start' },
        { id: 'review', type: 'action', actionType: 'notify', message: 'review' },
        { id: 'end', type: 'end' }
      ],
      edges: [
        { source: 'start', target: 'review' }
      ]
    };
    let saveRequests = 0;

    const ruleResponse = () => ({
      ruleId: 15,
      name: 'Broken path marker rule',
      status: 'draft',
      versionId: 1,
      definition: ruleDefinition
    });

    await page.route('**/api/v1/rules?page=1&pageSize=10', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [ruleResponse()],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await routeCommonReferenceApis(page);
    await routeApprovalTemplateApi(page);
    await routeRuleChildApis(page, 15);
    await page.route('**/api/v1/rules/15', async (route) => {
      if (route.request().method() !== 'PUT') {
        await route.fallback();
        return;
      }
      saveRequests += 1;
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });

    await page.goto('/rules');

    await page.getByRole('button', { name: 'Save canvas changes' }).click();

    await expect(page.getByText('Rule topology path cannot reach end')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-review')).toContainText('Topology blocked');
    await expect(page.locator('.edge-list span.topology-broken-edge-label')).toHaveText('Broken path start -> review');
    expect(saveRequests).toBe(0);
  });

  test('inserts approval templates into one selected branch edge without rewriting sibling branches', async ({ page }) => {
    let ruleDefinition = {
      nodes: [
        { id: 'start', type: 'start' },
        { id: 'riskBranch', type: 'branch', field: 'amount', operator: '>', value: '10000' },
        { id: 'approvePath', type: 'action', actionType: 'notify', message: 'approve' },
        { id: 'rejectPath', type: 'action', actionType: 'notify', message: 'reject' },
        { id: 'end', type: 'end' }
      ],
      edges: [
        { source: 'start', target: 'riskBranch' },
        { source: 'riskBranch', target: 'approvePath', condition: 'true' },
        { source: 'riskBranch', target: 'rejectPath', condition: 'false' },
        { source: 'approvePath', target: 'end' },
        { source: 'rejectPath', target: 'end' }
      ]
    };
    let savePayload: Record<string, unknown> | null = null;

    const ruleResponse = () => ({
      ruleId: 13,
      name: 'Branch insertion rule',
      status: 'draft',
      versionId: 4,
      definition: ruleDefinition
    });

    await page.route('**/api/v1/rules?page=1&pageSize=10', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [ruleResponse()],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await routeCommonReferenceApis(page);
    await routeApprovalTemplateApi(page);
    await routeRuleChildApis(page, 13);
    await page.route('**/api/v1/rules/13', async (route) => {
      if (route.request().method() !== 'PUT') {
        await route.fallback();
        return;
      }
      savePayload = await route.request().postDataJSON();
      ruleDefinition = savePayload.definition as typeof ruleDefinition;
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });

    await page.goto('/rules');

    await page.getByLabel('Approval template selector').selectOption('901');
    await page.getByLabel('Insert approval template after node').selectOption('riskBranch');
    await page.getByLabel('Insert approval template into branch path').selectOption('riskBranch->approvePath');
    await expect(page.getByText('Preview path riskBranch -> tpl901_financeManager -> tpl901_financeDirector -> approvePath')).toBeVisible();
    await expect(page.getByText('Remove edge riskBranch -> approvePath (true)')).toBeVisible();
    await expect(page.getByText('Add edge riskBranch -> tpl901_financeManager (true)')).toBeVisible();
    await expect(page.getByText('Add edge tpl901_financeDirector -> approvePath (true)')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-riskBranch')).toContainText('Preview insert point');
    await expect(page.getByTestId('rule-flow-node-approvePath')).toContainText('Preview target');
    await expect(page.getByTestId('rule-flow-node-preview-tpl901_financeManager')).toContainText('Preview approval step');
    await expect(page.getByTestId('rule-flow-node-preview-tpl901_financeDirector')).toContainText('Preview approval step');
    await expect(page.getByText('Preview remove edge riskBranch -> approvePath')).toBeVisible();
    await expect(page.getByText('Preview add edge riskBranch -> tpl901_financeManager')).toBeVisible();
    await expect(page.getByText('Preview add edge tpl901_financeDirector -> approvePath')).toBeVisible();
    await page.getByRole('button', { name: 'Apply approval template' }).click();
    await page.getByRole('button', { name: 'Save canvas changes' }).click();

    const savedEdges = (savePayload?.definition as typeof ruleDefinition).edges;
    expect(savedEdges).toEqual(expect.arrayContaining([
      expect.objectContaining({ source: 'riskBranch', target: 'tpl901_financeManager', condition: 'true' }),
      expect.objectContaining({ source: 'tpl901_financeDirector', target: 'approvePath', condition: 'true' }),
      expect.objectContaining({ source: 'riskBranch', target: 'rejectPath', condition: 'false' }),
      expect.objectContaining({ source: 'approvePath', target: 'end' }),
      expect.objectContaining({ source: 'rejectPath', target: 'end' })
    ]));
    expect(savedEdges).not.toEqual(expect.arrayContaining([
      expect.objectContaining({ source: 'tpl901_financeDirector', target: 'rejectPath' }),
      expect.objectContaining({ source: 'riskBranch', target: 'approvePath' })
    ]));
  });
});
