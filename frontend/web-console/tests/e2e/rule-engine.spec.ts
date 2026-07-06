import { expect, test, type Locator } from '@playwright/test';

test.describe('规则编排 E2E', () => {
  test('REQ-RULE-001：展示规则画布并执行调试样本', async ({ page }) => {
    let currentStatus = 'draft';
    let reviewPayload: Record<string, unknown> | null = null;
    let approvalPayload: Record<string, unknown> | null = null;
    let approvalActionPayload: Record<string, unknown> | null = null;
    let runPayload: Record<string, unknown> | null = null;
    let savePayload: Record<string, unknown> | null = null;
    let retriedActionExecutionId: string | null = null;
    let batchActionPayload: Record<string, unknown> | null = null;
    let actionExecutionListCalls = 0;
    let approvalRecordListCalls = 0;
    let subprocessTopologyCalls = 0;
    let productionRuns: Array<Record<string, unknown>> = [];
    let approvalRecords: Array<Record<string, unknown>> = [];
    let actionExecutions: Array<Record<string, unknown>> = [
      {
        actionExecutionId: 501,
        ruleId: 12,
        runId: 100,
        nodeId: 'writebackRisk',
        actionType: 'webhook',
        status: 'pending_retry',
        attempt: 1,
        maxRetryCount: 0,
        endpoint: 'https://erp.example.com/risk-events',
        idempotencyKey: 'rule-12-run-100-node-writebackRisk',
        nextRetryAt: '2026-06-24T09:00:00Z',
        errorMessage: 'erp timeout',
        metadata: { method: 'POST' }
      },
      {
        actionExecutionId: 500,
        ruleId: 12,
        runId: 99,
        nodeId: 'auditHook',
        actionType: 'webhook',
        status: 'succeeded',
        attempt: 1,
        maxRetryCount: 0,
        endpoint: 'https://erp.example.com/audit',
        idempotencyKey: 'rule-12-run-99-node-auditHook',
        nextRetryAt: '',
        errorMessage: '',
        metadata: { method: 'POST' }
      },
      {
        actionExecutionId: 499,
        ruleId: 12,
        runId: 98,
        nodeId: 'billingHook',
        actionType: 'webhook',
        status: 'compensation_exhausted',
        attempt: 4,
        maxRetryCount: 0,
        endpoint: 'https://erp.example.com/billing',
        idempotencyKey: 'rule-12-run-98-node-billingHook',
        nextRetryAt: '',
        errorMessage: 'max async replay attempts exhausted',
        metadata: { method: 'POST' }
      },
      {
        actionExecutionId: 498,
        ruleId: 12,
        runId: 97,
        nodeId: 'legacyHook',
        actionType: 'webhook',
        status: 'compensation_ignored',
        attempt: 1,
        maxRetryCount: 0,
        endpoint: 'https://erp.example.com/legacy',
        idempotencyKey: 'rule-12-run-97-node-legacyHook',
        nextRetryAt: '',
        errorMessage: 'ignored by operator',
        metadata: { method: 'POST' }
      }
    ];
    let ruleDefinition = {
      nodes: [
        { id: 'start', type: 'start' },
        {
          id: 'sumOverdue',
          type: 'aggregate',
          sourceField: 'invoices',
          operation: 'sum',
          valueField: 'overdueAmount',
          outputField: 'totalOverdueAmount'
        },
        { id: 'riskBranch', type: 'branch', field: 'totalOverdueAmount', operator: '>=', value: 10000 },
        {
          id: 'writebackRisk',
          type: 'action',
          actionType: 'webhook',
          endpoint: 'https://erp.example.com/risk-events',
          method: 'POST',
          maxRetryCount: 0,
          retryBackoffSeconds: 0,
          maxAsyncReplayAttempts: 3,
          signatureSecret: '',
          headers: { 'X-System': 'risk-center' },
          body: { eventType: 'risk_overdue', source: 'rule-engine' }
        },
        { id: 'notifyHighRisk', type: 'action', actionType: 'notify', message: 'high risk' },
        { id: 'archiveLowRisk', type: 'action', actionType: 'archive', message: 'low risk' },
        { id: 'aging', type: 'condition', field: 'daysOverdue', operator: '>', value: 30 },
        { id: 'end', type: 'end' }
      ],
      edges: [
        { source: 'start', target: 'sumOverdue' },
        { source: 'sumOverdue', target: 'riskBranch' },
        { source: 'riskBranch', target: 'writebackRisk', condition: 'true' },
        { source: 'writebackRisk', target: 'notifyHighRisk' },
        { source: 'riskBranch', target: 'notifyHighRisk', condition: 'true' },
        { source: 'riskBranch', target: 'archiveLowRisk', condition: 'false' },
        { source: 'notifyHighRisk', target: 'aging' },
        { source: 'archiveLowRisk', target: 'aging' },
        { source: 'aging', target: 'end' }
      ]
    };
    const ruleResponse = () => ({
      ruleId: 12,
      name: '应收账款风险规则',
      status: currentStatus,
      versionId: 3,
      definition: ruleDefinition
    });

    await page.route('**/api/v1/rules?page=1&pageSize=10', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              ruleResponse()
            ],
            page: 1,
            pageSize: 10,
            total: 1
          }
        }
      });
    });
    await page.route('**/api/v1/reports?page=1&pageSize=20', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              { reportId: 88, title: 'High risk customer report', status: 'completed' },
              { reportId: 89, title: 'Low risk customer report', status: 'draft' }
            ],
            page: 1,
            pageSize: 20,
            total: 2
          }
        }
      });
    });
    await page.route('**/api/v1/reports/88', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            reportId: 88,
            title: 'High risk customer report',
            status: 'completed',
            sections: [
              {
                sectionId: 'summary',
                heading: 'Risk summary',
                content: 'High risk evidence requires finance review before export.'
              },
              {
                sectionId: 'details',
                heading: 'Details',
                content: 'Supporting invoices are overdue.'
              }
            ]
          }
        }
      });
    });
    await page.route('**/api/v1/users?page=1&pageSize=20', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [
              {
                userId: 2,
                username: 'finance-reviewer',
                displayName: 'Finance Reviewer',
                department: 'Finance Center',
                position: 'Report Reviewer',
                status: 'enabled',
                roles: ['reviewer']
              },
              { userId: 3, username: 'disabled-reviewer', displayName: 'Disabled Reviewer', status: 'disabled', roles: ['reviewer'] }
            ],
            page: 1,
            pageSize: 20,
            total: 2
          }
        }
      });
    });
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
                        userId: 6,
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
                        userId: 4,
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
                        userId: 5,
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
                    userId: 6,
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
                    userId: 4,
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
                    userId: 5,
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
    await page.route('**/api/v1/rules/12/review-submissions', async (route) => {
      reviewPayload = await route.request().postDataJSON();
      currentStatus = 'pending_review';
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });
    await page.route('**/api/v1/rules/12/approvals', async (route) => {
      approvalPayload = await route.request().postDataJSON();
      currentStatus = 'published';
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });
    await page.route('**/api/v1/rules/12', async (route) => {
      if (route.request().method() !== 'PUT') {
        await route.fallback();
        return;
      }
      savePayload = await route.request().postDataJSON();
      ruleDefinition = savePayload.definition as typeof ruleDefinition;
      await route.fulfill({ json: { code: 200, message: 'ok', data: ruleResponse() } });
    });
    await page.route((url) => url.pathname === '/api/v1/rules/12/runs', async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          json: {
            code: 200,
            message: 'ok',
            data: {
              items: productionRuns,
              page: 1,
              pageSize: 5,
              total: productionRuns.length
            }
          }
        });
        return;
      }
      runPayload = await route.request().postDataJSON();
      productionRuns = [
        {
          runId: 100,
          ruleId: 12,
          versionId: 3,
          status: 'succeeded',
          runType: 'production',
          matched: true
        }
      ];
      approvalRecords = [
        {
          approvalRecordId: 700,
          ruleId: 12,
          runId: 100,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Finance approval required',
          status: 'pending',
          createdByUserId: 1,
          approvedByUserId: null,
          approvalComment: null,
          createdAt: '2026-06-25T08:00:00Z'
        }
      ];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            debugRunId: 100,
            ruleId: 12,
            versionId: 3,
            status: 'succeeded',
            runType: 'production',
            output: {
              matched: true,
              evaluatedNodes: 6,
              trace: [
                { nodeId: 'start', type: 'start', matched: true },
                { nodeId: 'sumOverdue', type: 'aggregate', matched: true, outputField: 'totalOverdueAmount', value: 11000 },
                { nodeId: 'riskBranch', type: 'branch', matched: true, selectedPath: 'true', nextNodeId: 'notifyHighRisk' },
                { nodeId: 'notifyHighRisk', type: 'action', matched: true },
                { nodeId: 'aging', type: 'condition', matched: true },
                { nodeId: 'end', type: 'end', matched: true }
              ]
            }
          }
        }
      });
    });
    await page.route('**/api/v1/rules/12/runs/100/subprocess-topology', async (route) => {
      subprocessTopologyCalls += 1;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            ruleId: 12,
            runId: 100,
            nodes: [
              { runId: 100, ruleId: 12, role: 'parent', status: 'succeeded', runType: 'production', versionId: 3 },
              { runId: 200, ruleId: 42, role: 'subprocess', status: 'succeeded', runType: 'production', versionId: 1 }
            ],
            edges: [
              {
                parentRunId: 100,
                subprocessRunId: 200,
                nodeId: 'riskSubprocess',
                subprocessRuleId: 42,
                result: 'succeeded'
              }
            ]
          }
        }
      });
    });
    await page.route((url) => url.pathname.startsWith('/api/v1/rules/12/action-executions'), async (route) => {
      const request = route.request();
      const pathname = new URL(request.url()).pathname;
      if (request.method() === 'POST' && pathname.endsWith('/action-executions/batch')) {
        batchActionPayload = await request.postDataJSON();
        actionExecutions = actionExecutions.map((execution) => (
          execution.actionExecutionId === 501
            ? { ...execution, actionExecutionId: 503, status: 'compensation_ignored', errorMessage: 'ignored from rule action ledger' }
            : execution
        ));
        await route.fulfill({
          json: {
            code: 200,
            message: 'ok',
            data: {
              operation: 'ignore',
              requestedCount: 1,
              succeededCount: 1,
              failedCount: 0,
              items: [{ actionExecutionId: 501, handledActionExecutionId: 503, result: 'succeeded', status: 'compensation_ignored' }]
            }
          }
        });
        return;
      }
      if (request.method() === 'POST' && request.url().includes('/501/retry')) {
        retriedActionExecutionId = '501';
        actionExecutions = [
          {
            ...actionExecutions[0],
            actionExecutionId: 502,
            status: 'succeeded',
            attempt: 2,
            errorMessage: ''
          },
          ...actionExecutions
        ];
        await route.fulfill({
          json: {
            code: 200,
            message: 'ok',
            data: actionExecutions[0]
          }
        });
        return;
      }
      if (request.method() === 'GET') {
        actionExecutionListCalls += 1;
        await route.fulfill({
          json: {
            code: 200,
            message: 'ok',
            data: {
              items: actionExecutions,
              page: 1,
              pageSize: 10,
              total: actionExecutions.length
            }
          }
        });
        return;
      }
      await route.fallback();
    });
    await page.route((url) => url.pathname === '/api/v1/rules/12/approval-records', async (route) => {
      approvalRecordListCalls += 1;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: approvalRecords,
            page: 1,
            pageSize: 10,
            total: approvalRecords.length
          }
        }
      });
    });
    await page.route('**/api/v1/rules/12/approval-records/700/actions', async (route) => {
      approvalActionPayload = await route.request().postDataJSON();
      approvalRecords = approvalRecords.map((record) => (
        record.approvalRecordId === 700
          ? {
              ...record,
              status: approvalActionPayload?.action === 'approve' ? 'approved' : 'rejected',
              approvedByUserId: 66,
              approvalComment: approvalActionPayload?.comment ?? null,
              approvedAt: '2026-06-25T08:10:00Z'
            }
          : record
      ));
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: approvalRecords[0]
        }
      });
    });
    await page.route('**/api/v1/rules/12/debug-runs', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            debugRunId: 99,
            ruleId: 12,
            versionId: 3,
            status: 'succeeded',
            output: {
              matched: false,
              evaluatedNodes: 6,
              trace: [
                { nodeId: 'start', type: 'start', matched: true },
                { nodeId: 'sumOverdue', type: 'aggregate', matched: true, outputField: 'totalOverdueAmount', value: 11000 },
                { nodeId: 'riskBranch', type: 'branch', matched: true, selectedPath: 'true', nextNodeId: 'notifyHighRisk' },
                { nodeId: 'notifyHighRisk', type: 'action', matched: true },
                { nodeId: 'aging', type: 'condition', matched: false },
                { nodeId: 'end', type: 'end', matched: true }
              ]
            }
          }
        }
      });
    });

    await page.goto('/rules');

    await expect(page.getByRole('heading', { name: '规则编排' })).toBeVisible();
    await expect(page.getByText('应收账款风险规则')).toBeVisible();
    await expect(page.getByText('draft · v3')).toBeVisible();
    await expect(page.getByText('start -> sumOverdue')).toBeVisible();
    await expect(page.getByText('sumOverdue -> riskBranch')).toBeVisible();
    await expect(page.getByText('riskBranch -> writebackRisk')).toBeVisible();
    await expect(page.getByText('riskBranch -> notifyHighRisk')).toBeVisible();
    await expect(page.getByLabel('Rule node summary list').getByText('sum invoices.overdueAmount -> totalOverdueAmount')).toBeVisible();
    await expect(page.getByLabel('Rule node summary list').getByText('totalOverdueAmount >= 10000 ? true/false')).toBeVisible();
    await expect(page.getByLabel('Rule node summary list').getByText('daysOverdue > 30')).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Interactive rule canvas' })).toBeVisible();
    const startCanvasNode = page.getByTestId('rule-flow-node-start');
    await expect(startCanvasNode).toBeVisible();
    const startNodeBoxBeforeMove = await startCanvasNode.boundingBox();
    expect(startNodeBoxBeforeMove).not.toBeNull();
    if (!startNodeBoxBeforeMove) {
      throw new Error('rule-flow-node-start bounding box missing');
    }
    await page.mouse.move(
      startNodeBoxBeforeMove.x + startNodeBoxBeforeMove.width / 2,
      startNodeBoxBeforeMove.y + startNodeBoxBeforeMove.height / 2
    );
    await page.mouse.down();
    await page.mouse.move(
      startNodeBoxBeforeMove.x + startNodeBoxBeforeMove.width / 2 + 140,
      startNodeBoxBeforeMove.y + startNodeBoxBeforeMove.height / 2 + 60,
      { steps: 8 }
    );
    await page.mouse.up();
    await expect(page.getByRole('heading', { name: 'Rule canvas editor' })).toBeVisible();
    await page.getByLabel('New node id').fill('escalateFinance');
    await page.getByLabel('New node type').selectOption('action');
    await page.getByLabel('New action type').fill('notify');
    await page.getByLabel('New action message').fill('finance escalation required');
    await page.getByRole('button', { name: 'Add node' }).click();
    await expect(page.getByTestId('rule-flow-node-escalateFinance')).toBeVisible();
    await page.getByLabel('New edge source').selectOption('escalateFinance');
    await page.getByLabel('New edge target').selectOption('aging');
    await page.getByLabel('New edge condition').fill('manual');
    await page.getByRole('button', { name: 'Add edge' }).click();
    await expect(page.getByText('escalateFinance -> aging')).toBeVisible();
    await page.getByLabel('New node id').fill('invalidCondition');
    await page.getByLabel('New node type').selectOption('condition');
    await page.getByRole('button', { name: 'Add node' }).click();
    await expect(page.getByText('New condition field is required')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-invalidCondition')).toBeHidden();
    await page.getByLabel('New node id').fill('creditHold');
    await page.getByLabel('New node type').selectOption('condition');
    await page.getByLabel('New condition field').fill('creditStatus');
    await page.getByLabel('New condition operator').fill('=');
    await page.getByLabel('New condition value').fill('hold');
    await page.getByRole('button', { name: 'Add node' }).click();
    await expect(page.getByTestId('rule-flow-node-creditHold')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-creditHold').getByText('creditStatus = hold')).toBeVisible();
    await page.getByLabel('New node id').fill('financeApproval');
    await page.getByLabel('New node type').selectOption('approval');
    await page.getByRole('combobox', { name: 'New approval assignee organization role' }).click();
    await page
      .locator('.el-select-dropdown:visible')
      .getByRole('option', { name: 'Finance Center / Finance Manager / finance_manager / Fiona Manager' })
      .click();
    await expect(page.getByLabel('New approval assignee roles')).toHaveValue('finance_manager');
    await page.getByLabel('New approval assignee roles').fill('finance_manager, legal_manager');
    await page.getByLabel('New approval mode').selectOption('all');
    await page.getByRole('combobox', { name: 'New approval delegate organization role' }).click();
    await expect(page.locator('.el-select-dropdown:visible').getByRole('option', {
      name: 'Finance Center / Backup Approver / finance_delegate / Derek Delegate'
    })).toBeVisible();
    await page.getByLabel('New approval delegate role').fill('finance_delegate');
    await page.getByLabel('New approval delegate active from').fill('2026-06-26T08:00:00Z');
    await page.getByLabel('New approval delegate active to').fill('2026-06-26T18:00:00Z');
    await page.getByLabel('New approval title').fill('Finance approval required');
    await page.getByLabel('New approval SLA hours').fill('4');
    await page.getByLabel('New approval SLA escalation role').fill('finance_director');
    await page.getByLabel('New approval SLA escalation policies').fill('4:finance_director, 8:risk_vp');
    await page.getByRole('button', { name: 'Add node' }).click();
    await expect(page.getByTestId('rule-flow-node-financeApproval')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-financeApproval').getByText('approval all finance_manager, legal_manager delegate finance_delegate 2026-06-26T08:00:00Z..2026-06-26T18:00:00Z escalate finance_director policies 4h:finance_director, 8h:risk_vp -> Finance approval required')).toBeVisible();
    await page.getByLabel('New node id').fill('createReviewTask');
    await page.getByLabel('New node type').selectOption('action');
    await page.getByLabel('New action type').fill('create_task');
    await page.getByLabel('New task report selector').selectOption('88');
    await page.getByLabel('New task assignee selector').selectOption('2');
    await expect(page.getByLabel('New task report id')).toHaveValue('88');
    await expect(page.getByLabel('New task assignee user id')).toHaveValue('2');
    await page.getByLabel('New task content').fill('Review high risk evidence');
    await expect(page.getByText('Risk summary')).toBeVisible();
    const anchorText = page.getByText('High risk evidence requires finance review before export.');
    await selectText(anchorText, 'High risk');
    await page.getByRole('button', { name: 'Use selected text as task anchor' }).click();
    await expect(page.getByLabel('New task section id')).toHaveValue('summary');
    await expect(page.getByLabel('New task start offset')).toHaveValue('0');
    await expect(page.getByLabel('New task end offset')).toHaveValue('9');
    await expect(page.getByLabel('New task selected text')).toHaveValue('High risk');
    await page.getByRole('button', { name: 'Add node' }).click();
    await expect(page.getByTestId('rule-flow-node-createReviewTask')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-createReviewTask').getByText('task report 88 -> user 2')).toBeVisible();
    await page.getByLabel('New node id').fill('riskSubprocess');
    await page.getByLabel('New node type').selectOption('subprocess');
    await page.getByLabel('New subprocess rule id').fill('42');
    await page.getByLabel('New subprocess name').fill('Risk review flow');
    await page.getByRole('button', { name: 'Add node' }).click();
    await expect(page.getByTestId('rule-flow-node-riskSubprocess')).toBeVisible();
    await expect(page.getByTestId('rule-flow-node-riskSubprocess').getByText('subprocess 42 -> Risk review flow')).toBeVisible();
    await page.getByLabel('New edge source').selectOption('financeApproval');
    await page.getByLabel('New edge target').selectOption('createReviewTask');
    await page.getByLabel('New edge condition').fill('rejected');
    await page.getByRole('button', { name: 'Add edge' }).click();
    await expect(page.getByText('financeApproval -> createReviewTask')).toBeVisible();
    await page.getByLabel('Delete edge').selectOption('riskBranch->notifyHighRisk');
    await page.getByRole('button', { name: 'Delete edge' }).click();
    await expect(page.getByText('riskBranch -> notifyHighRisk')).toBeHidden();
    await page.getByLabel('Delete node').selectOption('archiveLowRisk');
    await page.getByRole('button', { name: 'Delete node' }).click();
    await expect(page.getByTestId('rule-flow-node-archiveLowRisk')).toBeHidden();
    await page.getByRole('button', { name: 'Save canvas changes' }).click();
    expect(savePayload).toMatchObject({
      status: 'draft',
      definition: {
        nodes: expect.arrayContaining([
          expect.objectContaining({
            id: 'escalateFinance',
            type: 'action',
            actionType: 'notify',
            message: 'finance escalation required'
          }),
          expect.objectContaining({
            id: 'creditHold',
            type: 'condition',
            field: 'creditStatus',
            operator: '=',
            value: 'hold'
          }),
          expect.objectContaining({
            id: 'financeApproval',
            type: 'approval',
            assigneeRoles: ['finance_manager', 'legal_manager'],
            approvalMode: 'all',
            delegateRole: 'finance_delegate',
            delegateActiveFrom: '2026-06-26T08:00:00Z',
            delegateActiveTo: '2026-06-26T18:00:00Z',
            slaHours: 4,
            slaEscalationRole: 'finance_director',
            slaEscalations: [
              { afterHours: 4, role: 'finance_director' },
              { afterHours: 8, role: 'risk_vp' }
            ],
            approvalTitle: 'Finance approval required'
          }),
          expect.objectContaining({
            id: 'createReviewTask',
            type: 'action',
            actionType: 'create_task',
            reportId: 88,
            assigneeUserId: 2,
            content: 'Review high risk evidence',
            anchor: {
              sectionId: 'summary',
              startOffset: 0,
              endOffset: 9,
              selectedText: 'High risk'
            }
          }),
          expect.objectContaining({
            id: 'riskSubprocess',
            type: 'subprocess',
            subprocessRuleId: 42,
            subprocessName: 'Risk review flow'
          })
        ])
      }
    });
    expect((savePayload?.definition as { nodes: Array<{ id: string }>; edges: Array<{ source: string; target: string }> }).nodes)
      .not.toEqual(expect.arrayContaining([expect.objectContaining({ id: 'archiveLowRisk' })]));
    expect((savePayload?.definition as { nodes: Array<{ id: string }>; edges: Array<{ source: string; target: string }> }).edges)
      .not.toEqual(expect.arrayContaining([
        expect.objectContaining({ source: 'riskBranch', target: 'archiveLowRisk' }),
        expect.objectContaining({ source: 'archiveLowRisk', target: 'aging' }),
        expect.objectContaining({ source: 'riskBranch', target: 'notifyHighRisk' })
      ]));
    expect((savePayload?.definition as { nodes: Array<{ id: string }>; edges: Array<{ source: string; target: string; condition?: string }> }).edges)
      .toEqual(expect.arrayContaining([
        expect.objectContaining({ source: 'escalateFinance', target: 'aging', condition: 'manual' }),
        expect.objectContaining({ source: 'financeApproval', target: 'createReviewTask', condition: 'rejected' })
      ]));
    expect((savePayload?.definition as {
      nodes: Array<{ id: string; position?: { x: number; y: number } }>;
    }).nodes).toEqual(expect.arrayContaining([
      expect.objectContaining({
        id: 'start',
        position: expect.objectContaining({
          x: expect.any(Number),
          y: expect.any(Number)
        })
      })
    ]));
    const savedStartNode = (savePayload?.definition as {
      nodes: Array<{ id: string; position?: { x: number; y: number } }>;
    }).nodes.find((node) => node.id === 'start');
    expect(savedStartNode?.position?.x).toBeGreaterThan(20);
    expect(savedStartNode?.position?.y).toBeGreaterThan(20);
    await expect(page.getByRole('heading', { name: 'Webhook node configuration' })).toBeVisible();
    await expect(page.getByLabel('Webhook endpoint')).toHaveValue('https://erp.example.com/risk-events');
    await expect(page.getByLabel('Webhook method')).toHaveValue('POST');
    await expect(page.getByLabel('Webhook max retry count')).toHaveValue('0');
    await expect(page.getByLabel('Webhook retry backoff seconds')).toHaveValue('0');
    await expect(page.getByLabel('Webhook max async replay attempts')).toHaveValue('3');
    await expect(page.getByLabel('Webhook headers JSON')).toHaveValue(JSON.stringify({ 'X-System': 'risk-center' }, null, 2));
    await expect(page.getByLabel('Webhook body JSON')).toHaveValue(JSON.stringify({ eventType: 'risk_overdue', source: 'rule-engine' }, null, 2));
    await page.getByLabel('Webhook endpoint').fill('https://erp.example.com/risk-events-v2');
    await page.getByLabel('Webhook method').fill('PUT');
    await page.getByLabel('Webhook max retry count').fill('2');
    await page.getByLabel('Webhook retry backoff seconds').fill('30');
    await page.getByLabel('Webhook max async replay attempts').fill('5');
    await page.getByLabel('Webhook signature secret').fill('secret-v2');
    await page.getByLabel('Webhook headers JSON').fill(JSON.stringify({ 'X-System': 'risk-center-v2', 'X-Trace': 'enabled' }, null, 2));
    await page.getByLabel('Webhook body JSON').fill(JSON.stringify({ eventType: 'risk_overdue_v2', source: 'rule-engine', severity: 'high' }, null, 2));
    await page.getByRole('button', { name: 'Save webhook config' }).click();
    await expect(page.getByLabel('Webhook endpoint')).toHaveValue('https://erp.example.com/risk-events-v2');
    expect(savePayload).toMatchObject({
      status: 'draft',
      definition: {
        nodes: expect.arrayContaining([
          expect.objectContaining({
            id: 'writebackRisk',
            actionType: 'webhook',
            endpoint: 'https://erp.example.com/risk-events-v2',
            method: 'PUT',
            maxRetryCount: 2,
            retryBackoffSeconds: 30,
            maxAsyncReplayAttempts: 5,
            signatureSecret: 'secret-v2',
            headers: { 'X-System': 'risk-center-v2', 'X-Trace': 'enabled' },
            body: { eventType: 'risk_overdue_v2', source: 'rule-engine', severity: 'high' }
          })
        ])
      }
    });

    await expect(page.getByRole('button', { name: '生产运行' })).toBeDisabled();
    await page.getByRole('button', { name: '提交审核' }).click();
    await expect(page.getByText('pending_review · v3')).toBeVisible();
    await page.getByRole('button', { name: '审批发布' }).click();
    await expect(page.getByText('published · v3')).toBeVisible();
    await page.getByRole('button', { name: '生产运行' }).click();
    await expect(page.getByText('生产运行：命中')).toBeVisible();
    await expect(page.getByText('runType: production · versionId: 3')).toBeVisible();

    await page.getByLabel('调试样本 JSON').fill('{"daysOverdue":12}');
    await page.getByRole('button', { name: '调试运行' }).click();

    await expect(page.getByText('未命中')).toBeVisible();
    await expect(page.getByText('sumOverdue · aggregate · true · totalOverdueAmount: 11000')).toBeVisible();
    await expect(page.getByText('aging · condition · false')).toBeVisible();
    await expect(page.getByText(/riskBranch.*branch.*true.*notifyHighRisk/)).toBeVisible();
    await expect(page.getByText('evaluatedNodes: 6')).toBeVisible();
    await expect(page.getByText(/#100.*production.*succeeded/)).toBeVisible();
    await page.getByRole('button', { name: 'Topology 100' }).click();
    await expect(page.getByRole('heading', { name: 'Subprocess topology' })).toBeVisible();
    await expect(page.getByText('parent run 100')).toBeVisible();
    await expect(page.getByText('subprocess run 200')).toBeVisible();
    await expect(page.getByText(/riskSubprocess.*100.*200/)).toBeVisible();
    expect(subprocessTopologyCalls).toBeGreaterThan(0);
    await expect(page.getByRole('button', { name: 'Approve' })).toBeVisible();
    await page.getByRole('button', { name: 'Approve' }).click();
    await expect(page.getByText('financeApproval 路 finance_manager 路 approved')).toBeVisible();
    await expect(page.getByText('approved from rule page')).toBeVisible();
    expect(approvalActionPayload).toMatchObject({
      action: 'approve',
      comment: 'approved from rule page'
    });
    await expect(page.getByRole('heading', { name: 'Webhook action ledger' })).toBeVisible();
    expect(actionExecutionListCalls).toBeGreaterThan(0);
    await expect(page.getByText('Compensation operations')).toBeVisible();
    await expect(page.getByText('Pending 1')).toBeVisible();
    await expect(page.getByText('Succeeded 1')).toBeVisible();
    await expect(page.getByText('Exhausted 1')).toBeVisible();
    await expect(page.getByText('Ignored 1')).toBeVisible();
    await expect(page.getByText('Success rate 25%')).toBeVisible();
    await expect(page.locator('.ledger-row').filter({ hasText: 'writebackRisk' })).toBeVisible();
    await expect(page.getByText('pending_retry')).toBeVisible();
    await expect(page.locator('.ledger-row').filter({ hasText: 'https://erp.example.com/risk-events' })).toBeVisible();
    await page.getByRole('button', { name: 'Only pending/failed' }).click();
    await expect(page.getByText('auditHook')).toBeHidden();
    await page.getByLabel('Select action 501').check();
    await page.getByRole('button', { name: 'Batch ignore' }).click();
    await page.getByRole('button', { name: 'Only pending/failed' }).click();
    await expect(page.locator('.ledger-row').filter({ hasText: 'writebackRisk' }).filter({ hasText: 'compensation_ignored' })).toBeVisible();
    expect(batchActionPayload).toMatchObject({
      operation: 'ignore',
      actionExecutionIds: [501],
      reason: 'ignored from rule action ledger'
    });
    actionExecutions = [
      {
        actionExecutionId: 501,
        ruleId: 12,
        runId: 100,
        nodeId: 'writebackRisk',
        actionType: 'webhook',
        status: 'pending_retry',
        attempt: 1,
        maxRetryCount: 0,
        endpoint: 'https://erp.example.com/risk-events',
        idempotencyKey: 'rule-12-run-100-node-writebackRisk',
        nextRetryAt: '2026-06-24T09:00:00Z',
        errorMessage: 'erp timeout',
        metadata: { method: 'POST' }
      },
      ...actionExecutions.filter((execution) => execution.actionExecutionId !== 501)
    ];
    await page.getByRole('button', { name: 'Refresh' }).click();
    await page.getByRole('button', { name: 'Retry 501' }).click();
    await expect(page.getByText(/succeeded.*attempt 2/)).toBeVisible();
    expect(retriedActionExecutionId).toBe('501');
    expect(reviewPayload).toMatchObject({ comment: 'ready for approval' });
    expect(approvalPayload).toMatchObject({ comment: 'approved for production' });
    expect(runPayload).toMatchObject({ sample: { daysOverdue: 45 } });
  });
});

async function selectText(locator: Locator, text: string) {
  await locator.evaluate((element, selectedText) => {
    const content = element.textContent ?? '';
    const start = content.indexOf(selectedText);
    if (start < 0 || !element.firstChild) {
      throw new Error(`text not found: ${selectedText}`);
    }
    const range = document.createRange();
    range.setStart(element.firstChild, start);
    range.setEnd(element.firstChild, start + selectedText.length);
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
  }, text);
}
