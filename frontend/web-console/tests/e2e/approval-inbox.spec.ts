import { expect, test } from '@playwright/test';

test.describe('Approval inbox E2E', () => {
  test('P0：审批待办页面使用中文业务文案', async ({ page }) => {
    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items: [],
            page: 1,
            pageSize: 20,
            total: 0
          }
        }
      });
    });

    await page.goto('/rules/approvals');

    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();
    await expect(page.getByText('集中处理规则运行中的审批任务、补充材料和催办。')).toBeVisible();
    await expect(page.getByRole('button', { name: '刷新' })).toBeVisible();
    await expect(page.getByRole('button', { name: '待审批' })).toBeVisible();
    await expect(page.getByRole('button', { name: '已通过' })).toBeVisible();
    await expect(page.getByRole('button', { name: '已驳回' })).toBeVisible();
    await expect(page.getByRole('button', { name: '待补充' })).toBeVisible();
    await expect(page.getByRole('button', { name: '已重提' })).toBeVisible();
    await expect(page.getByRole('button', { name: '已关闭' })).toBeVisible();
    await expect(page.getByText('待审批 0', { exact: true })).toBeVisible();
    await expect(page.getByLabel('规则编号筛选')).toBeVisible();
    await expect(page.getByLabel('审批角色筛选')).toBeVisible();
    await expect(page.getByLabel('审批标题筛选')).toBeVisible();
    await expect(page.getByLabel('发起人编号筛选')).toBeVisible();
    await expect(page.getByLabel('审批人编号筛选')).toBeVisible();
    await expect(page.getByRole('button', { name: '应用筛选' })).toBeVisible();
    await expect(page.getByRole('button', { name: '重置' })).toBeVisible();
    await expect(page.getByText('暂无待审批记录')).toBeVisible();

    const visibleText = await page.locator('body').innerText();
    expect(visibleText).not.toMatch(/Approval Inbox|Review pending|Refresh|Pending|Approved|Rejected|Supplement required|Resubmitted|Closed|Batch approve|Rule ID|Assignee role|Approval title|Created by|Approved by|Apply filters|Reset|No pending approvals/);
  });

  test('shows pending approvals across rules and refreshes after approve', async ({ page }) => {
    let actionPayload: Record<string, unknown> | null = null;
    const capturedStatuses: string[] = [];
    const capturedRuleIds: string[] = [];
    const capturedRoles: string[] = [];
    let approvalRecordsByStatus: Record<string, Array<Record<string, unknown>>> = {
      pending: [
        {
          approvalRecordId: 701,
          ruleId: 12,
          runId: 100,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          assigneeRoles: ['finance_manager', 'legal_manager'],
          approvalMode: 'all',
          approvalGroupKey: 'rule:12:run:100:node:financeApproval',
          approvalGroupTotalCount: 2,
          approvalGroupApprovedCount: 1,
          approvalGroupPendingCount: 1,
          approvalGroupRejectedCount: 0,
          approvalGroupClosedCount: 0,
          approvalTitle: 'Finance approval required',
          assigneeUsers: [
            { userId: 77, username: 'fin.approver', displayName: 'Finance Approver', role: 'finance_manager' }
          ],
          delegateRole: 'finance_delegate',
          delegateUsers: [
            { userId: 88, username: 'fin.delegate', displayName: 'Finance Delegate', role: 'finance_delegate' }
          ],
          delegateActiveFrom: '2026-06-26T08:00:00Z',
          delegateActiveTo: '2026-06-26T18:00:00Z',
          status: 'pending',
          createdAt: '2026-06-25T08:00:00Z'
        },
        {
          approvalRecordId: 702,
          ruleId: 18,
          runId: 108,
          nodeId: 'legalApproval',
          assigneeRole: 'legal_manager',
          approvalTitle: 'Legal approval required',
          status: 'pending',
          createdAt: '2026-06-25T08:05:00Z'
        }
      ],
      approved: [
        {
          approvalRecordId: 799,
          ruleId: 30,
          runId: 160,
          nodeId: 'opsApproval',
          assigneeRole: 'ops_manager',
          approvalTitle: 'Historical approved record',
          status: 'approved',
          createdAt: '2026-06-25T07:30:00Z',
          approvedAt: '2026-06-25T07:45:00Z'
        }
      ],
      rejected: []
      ,
      closed: []
    };

    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      const url = new URL(route.request().url());
      const status = url.searchParams.get('status') ?? 'pending';
      const ruleId = url.searchParams.get('ruleId');
      const assigneeRole = url.searchParams.get('assigneeRole');
      capturedStatuses.push(status);
      capturedRuleIds.push(ruleId ?? '');
      capturedRoles.push(assigneeRole ?? '');
      const items = (approvalRecordsByStatus[status] ?? []).filter((item) => {
        const matchesRule = !ruleId || String(item.ruleId) === ruleId;
        const matchesRole = !assigneeRole || String(item.assigneeRole) === assigneeRole;
        return matchesRule && matchesRole;
      });
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items,
            page: 1,
            pageSize: 20,
            total: items.length
          }
        }
      });
    });

    await page.route('**/api/v1/rules/12/approval-records/701/actions', async (route) => {
      actionPayload = await route.request().postDataJSON();
      const approvedRecord = {
        ...approvalRecordsByStatus.pending.find((item) => item.approvalRecordId === 701),
        status: 'approved',
        approvedAt: '2026-06-25T08:20:00Z'
      } as Record<string, unknown>;
      approvalRecordsByStatus.pending = approvalRecordsByStatus.pending.filter((item) => item.approvalRecordId !== 701);
      approvalRecordsByStatus.approved = [approvedRecord, ...approvalRecordsByStatus.approved];
      approvalRecordsByStatus.closed = [
        {
          approvalRecordId: 702,
          ruleId: 12,
          runId: 100,
          nodeId: 'financeApproval',
          assigneeRole: 'legal_manager',
          assigneeRoles: ['finance_manager', 'legal_manager'],
          approvalMode: 'any',
          approvalGroupKey: 'rule:12:run:100:node:financeApproval',
          approvalGroupTotalCount: 2,
          approvalGroupApprovedCount: 1,
          approvalGroupPendingCount: 0,
          approvalGroupRejectedCount: 0,
          approvalGroupClosedCount: 1,
          approvalTitle: 'Legal approval required',
          status: 'closed',
          approvalComment: 'closed because approval group was approved by any assignee',
          createdAt: '2026-06-25T08:05:00Z',
          approvedAt: '2026-06-25T08:20:01Z'
        }
      ];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            approvalRecordId: 701,
            ruleId: 12,
            status: 'approved'
          }
        }
      });
    });

    await page.goto('/rules/approvals');

    await expect(page.getByRole('heading', { name: '审批待办' })).toBeVisible();
    await expect(page.getByText('待审批 2', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance approval required')).toBeVisible();
    await expect(page.getByText('审批组 全部审批 已通过 1/2, 待审批 1')).toBeVisible();
    await expect(page.getByText('审批组角色 finance_manager, legal_manager')).toBeVisible();
    await expect(page.getByText('候选审批人 Finance Approver (fin.approver)')).toBeVisible();
    await expect(page.getByText('代理角色 finance_delegate')).toBeVisible();
    await expect(page.getByText('代理用户 Finance Delegate (fin.delegate)')).toBeVisible();
    await expect(page.getByText('代理时段 2026-06-26 08:00:00..2026-06-26 18:00:00')).toBeVisible();
    await expect(page.getByText('Legal approval required')).toBeVisible();

    await page.getByLabel('规则编号筛选').fill('12');
    await page.getByLabel('审批角色筛选').fill('finance_manager');
    await page.getByRole('button', { name: '应用筛选' }).click();

    await expect(page.getByText('待审批 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance approval required')).toBeVisible();
    await expect(page.getByText('Legal approval required')).toHaveCount(0);
    expect(capturedStatuses).toContain('pending');
    expect(capturedRuleIds).toContain('12');
    expect(capturedRoles).toContain('finance_manager');

    await page.locator('.approval-card').first().getByRole('button', { name: '通过', exact: true }).click();

    await expect(page.getByText('待审批 0', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance approval required')).toHaveCount(0);
    await expect(page.getByText('暂无待审批记录')).toBeVisible();
    expect(actionPayload).toMatchObject({
      action: 'approve',
      comment: '在审批待办中通过'
    });

    await page.getByRole('button', { name: '已通过' }).click();
    await expect(page.getByText('已通过 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance approval required')).toBeVisible();
    await expect(page.getByText('Historical approved record')).toHaveCount(0);
    await expect(page.getByText('处理时间 2026-06-25 08:20:00')).toBeVisible();

    await page.getByRole('button', { name: '重置' }).click();
    await page.getByRole('button', { name: '已关闭' }).click();
    await expect(page.getByText('已关闭 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Legal approval required')).toBeVisible();
    await expect(page.getByText('状态 已关闭')).toBeVisible();
    await expect(page.getByText('审批备注 closed because approval group was approved by any assignee')).toBeVisible();
    await expect(page.getByText('审批组 任一审批 已通过 1/2, 待审批 0, 已关闭 1')).toBeVisible();
  });

  test('shows reject fallback guidance and rejected history after reject', async ({ page }) => {
    let actionPayload: Record<string, unknown> | null = null;
    let approvalRecordsByStatus: Record<string, Array<Record<string, unknown>>> = {
      pending: [
        {
          approvalRecordId: 703,
          ruleId: 22,
          runId: 120,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Finance approval required',
          status: 'pending',
          createdAt: '2026-06-25T08:10:00Z'
        }
      ],
      approved: [],
      rejected: [
        {
          approvalRecordId: 744,
          ruleId: 25,
          runId: 140,
          nodeId: 'riskApproval',
          assigneeRole: 'risk_manager',
          approvalTitle: 'Historical rejected record',
          status: 'rejected',
          approvalComment: 'historical rejected note',
          createdAt: '2026-06-25T07:50:00Z',
          approvedAt: '2026-06-25T08:00:00Z'
        }
      ],
      closed: [
        {
          approvalRecordId: 755,
          ruleId: 22,
          runId: 120,
          nodeId: 'financeApproval',
          assigneeRole: 'legal_manager',
          assigneeRoles: ['finance_manager', 'legal_manager'],
          approvalMode: 'all',
          approvalGroupKey: 'rule:22:run:120:node:financeApproval',
          approvalGroupTotalCount: 2,
          approvalGroupApprovedCount: 0,
          approvalGroupPendingCount: 0,
          approvalGroupRejectedCount: 1,
          approvalGroupClosedCount: 1,
          approvalTitle: 'Finance approval sibling',
          status: 'closed',
          approvalComment: 'closed because approval group was rejected',
          createdAt: '2026-06-25T08:11:00Z',
          approvedAt: '2026-06-25T08:18:01Z'
        }
      ]
    };

    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      const url = new URL(route.request().url());
      const status = url.searchParams.get('status') ?? 'pending';
      const approvalTitle = url.searchParams.get('approvalTitle');
      const items = (approvalRecordsByStatus[status] ?? []).filter((item) => {
        return !approvalTitle || String(item.approvalTitle).includes(approvalTitle);
      });
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items,
            page: 1,
            pageSize: 20,
            total: items.length
          }
        }
      });
    });

    await page.route('**/api/v1/rules/22/approval-records/703/actions', async (route) => {
      actionPayload = await route.request().postDataJSON();
      const rejectedRecord = {
        ...approvalRecordsByStatus.pending.find((item) => item.approvalRecordId === 703),
        status: 'rejected',
        approvalComment: '在审批待办中驳回',
        approvedAt: '2026-06-25T08:18:00Z'
      } as Record<string, unknown>;
      approvalRecordsByStatus.pending = [];
      approvalRecordsByStatus.rejected = [rejectedRecord, ...approvalRecordsByStatus.rejected];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            approvalRecordId: 703,
            ruleId: 22,
            status: 'rejected'
          }
        }
      });
    });

    await page.goto('/rules/approvals');

    await page.getByLabel('审批标题筛选').fill('Finance');
    await page.getByRole('button', { name: '应用筛选' }).click();
    await expect(page.getByText('Finance approval required')).toBeVisible();

    await page.locator('.approval-card').first().getByRole('button', { name: '驳回', exact: true }).click();

    await expect(page.getByText('审批已驳回，请补充材料后重新提交。')).toBeVisible();
    await expect(page.getByText('暂无待审批记录')).toBeVisible();
    expect(actionPayload).toMatchObject({
      action: 'reject',
      comment: '在审批待办中驳回'
    });

    await page.getByRole('button', { name: '已驳回' }).click();
    await expect(page.getByText('已驳回 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance approval required')).toBeVisible();
    await expect(page.getByText('Historical rejected record')).toHaveCount(0);
    await expect(page.getByText('审批备注 在审批待办中驳回')).toBeVisible();
    await expect(page.getByText('审批备注 historical rejected note')).toHaveCount(0);

    await page.getByRole('button', { name: '已关闭' }).click();
    await expect(page.getByText('已关闭 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance approval sibling')).toBeVisible();
    await expect(page.getByText('状态 已关闭')).toBeVisible();
    await expect(page.getByText('审批备注 closed because approval group was rejected')).toBeVisible();
    await expect(page.getByText('审批组 全部审批 已通过 0/2, 待审批 0, 已驳回 1, 已关闭 1')).toBeVisible();
  });

  test('submits supplement from rejected approval history and refreshes pending approval', async ({ page }) => {
    let supplementPayload: Record<string, unknown> | null = null;
    let uploadedContentType = '';
    let uploadedBodyLength = 0;
    let approvalRecordsByStatus: Record<string, Array<Record<string, unknown>>> = {
      pending: [],
      approved: [],
      rejected: [
        {
          approvalRecordId: 903,
          ruleId: 52,
          runId: 320,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Finance evidence correction',
          status: 'rejected',
          approvalComment: 'missing invoice evidence',
          createdAt: '2026-06-25T08:10:00Z',
          approvedAt: '2026-06-25T08:20:00Z'
        }
      ],
      supplement_required: [
        {
          approvalRecordId: 904,
          ruleId: 52,
          runId: 320,
          nodeId: 'financeApproval',
          assigneeRole: null,
          approvalTitle: 'Finance evidence correction',
          status: 'supplement_required',
          approvalComment: 'missing invoice evidence',
          createdAt: '2026-06-25T08:20:01Z'
        }
      ],
      resubmitted: []
    };

    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      const url = new URL(route.request().url());
      const status = url.searchParams.get('status') ?? 'pending';
      const items = approvalRecordsByStatus[status] ?? [];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items,
            page: 1,
            pageSize: 20,
            total: items.length
          }
        }
      });
    });

    await page.route('**/api/v1/rules/52/approval-records/903/supplement-attachments', async (route) => {
      uploadedContentType = route.request().headers()['content-type'] ?? '';
      uploadedBodyLength = (route.request().postDataBuffer() ?? Buffer.from('')).length;
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            ruleId: 52,
            approvalRecordId: 903,
            fileName: 'invoice-package.pdf',
            bucket: 'approval-supplements',
            objectKey: 'rule-52/approval-903/invoice-package.pdf',
            evidenceUrl: 'minio://approval-supplements/rule-52/approval-903/invoice-package.pdf'
          }
        }
      });
    });

    await page.route('**/api/v1/rules/52/approval-records/903/supplements', async (route) => {
      supplementPayload = await route.request().postDataJSON();
      approvalRecordsByStatus.rejected = [];
      approvalRecordsByStatus.supplement_required = [];
      approvalRecordsByStatus.resubmitted = [
        {
          approvalRecordId: 904,
          ruleId: 52,
          runId: 320,
          nodeId: 'financeApproval',
          assigneeRole: null,
          approvalTitle: 'Finance evidence correction',
          status: 'resubmitted',
          approvalComment: 'uploaded corrected invoice package',
          createdAt: '2026-06-25T08:20:01Z',
          approvedAt: '2026-06-25T08:25:00Z'
        }
      ];
      approvalRecordsByStatus.pending = [
        {
          approvalRecordId: 905,
          ruleId: 52,
          runId: 320,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Finance evidence correction',
          status: 'pending',
          approvalComment: 'uploaded corrected invoice package',
          createdAt: '2026-06-25T08:25:00Z'
        }
      ];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            sourceRejectedApprovalRecordId: 903,
            newApprovalRecordId: 905,
            supplementStatus: 'resubmitted'
          }
        }
      });
    });

    await page.goto('/rules/approvals');
    await page.getByRole('button', { name: '已驳回' }).click();

    const rejectedCard = page.locator('.approval-card').filter({
      has: page.getByText('Finance evidence correction')
    });
    await expect(rejectedCard).toBeVisible();
    await rejectedCard.getByLabel('补充说明').fill('uploaded corrected invoice package');
    await rejectedCard.getByLabel('补充附件').setInputFiles({
      name: 'invoice-package.pdf',
      mimeType: 'application/pdf',
      buffer: Buffer.from('corrected invoice package')
    });
    await expect(rejectedCard.getByText('invoice-package.pdf')).toBeVisible();
    await rejectedCard.getByRole('button', { name: '提交补充' }).click();

    await expect(page.getByText('补充材料已提交，审批已回到待处理状态。')).toBeVisible();
    expect(uploadedContentType).toContain('multipart/form-data');
    expect(uploadedBodyLength).toBeGreaterThan(0);
    expect(supplementPayload).toMatchObject({
      comment: 'uploaded corrected invoice package',
      evidenceUrl: 'minio://approval-supplements/rule-52/approval-903/invoice-package.pdf'
    });

    await page.getByRole('button', { name: '待审批' }).click();
    await expect(page.getByText('待审批 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Finance evidence correction')).toBeVisible();
    await expect(page.getByText('状态 待审批')).toBeVisible();

    await page.getByRole('button', { name: '已重提' }).click();
    await expect(page.getByText('已重提 1', { exact: true })).toBeVisible();
    await expect(page.getByText('审批备注 uploaded corrected invoice package')).toBeVisible();
  });

  test('shows overdue approvals and sends a reminder from the inbox', async ({ page }) => {
    let reminderCallCount = 0;
    let approvalRecordsByStatus: Record<string, Array<Record<string, unknown>>> = {
      pending: [
        {
          approvalRecordId: 711,
          ruleId: 12,
          runId: 100,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Finance approval overdue',
          status: 'pending',
          createdAt: '2026-06-24T08:00:00Z',
          slaHours: 4,
          slaDueAt: '2026-06-24T12:00:00Z',
          isOverdue: true,
          remindCount: 0,
          lastRemindedAt: null
        }
      ],
      approved: [],
      rejected: []
    };

    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      const url = new URL(route.request().url());
      const status = url.searchParams.get('status') ?? 'pending';
      const items = approvalRecordsByStatus[status] ?? [];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items,
            page: 1,
            pageSize: 20,
            total: items.length
          }
        }
      });
    });

    await page.route('**/api/v1/rules/12/approval-records/711/reminders', async (route) => {
      reminderCallCount += 1;
      approvalRecordsByStatus.pending = approvalRecordsByStatus.pending.map((item) => (
        item.approvalRecordId === 711
          ? {
              ...item,
              remindCount: 1,
              lastRemindedAt: '2026-06-25T08:22:00Z'
            }
          : item
      ));
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: approvalRecordsByStatus.pending[0]
        }
      });
    });

    await page.goto('/rules/approvals');

    const approvalCard = page.locator('.approval-card').filter({
      has: page.getByText('Finance approval overdue')
    });
    await expect(approvalCard).toBeVisible();
    await expect(approvalCard.getByText('已逾期', { exact: true })).toBeVisible();
    await expect(approvalCard.getByText('催办次数 0')).toBeVisible();

    await approvalCard.getByRole('button', { name: '催办' }).click();

    await expect(page.getByText('已发送审批催办。')).toBeVisible();
    await expect(approvalCard.getByText('催办次数 1')).toBeVisible();
    await expect(approvalCard.getByText('最近催办 2026-06-25 08:22:00')).toBeVisible();
    expect(reminderCallCount).toBe(1);
  });

  test('applies extended operator filters for initiator, approver, and created time range', async ({ page }) => {
    const capturedQueries: Array<Record<string, string>> = [];
    const approvalRecordsByStatus: Record<string, Array<Record<string, unknown>>> = {
      pending: [
        {
          approvalRecordId: 801,
          ruleId: 31,
          runId: 201,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Quarter close approval',
          status: 'pending',
          createdByUserId: 1001,
          approvedByUserId: null,
          createdAt: '2026-06-25T08:10:00Z'
        },
        {
          approvalRecordId: 802,
          ruleId: 32,
          runId: 202,
          nodeId: 'opsApproval',
          assigneeRole: 'ops_manager',
          approvalTitle: 'Ops budget approval',
          status: 'pending',
          createdByUserId: 2002,
          approvedByUserId: null,
          createdAt: '2026-06-25T09:40:00Z'
        }
      ],
      approved: [
        {
          approvalRecordId: 803,
          ruleId: 33,
          runId: 203,
          nodeId: 'auditApproval',
          assigneeRole: 'audit_manager',
          approvalTitle: 'Approved finance exception',
          status: 'approved',
          createdByUserId: 1001,
          approvedByUserId: 66,
          createdAt: '2026-06-25T08:12:00Z',
          approvedAt: '2026-06-25T08:20:00Z'
        },
        {
          approvalRecordId: 804,
          ruleId: 34,
          runId: 204,
          nodeId: 'legalApproval',
          assigneeRole: 'legal_manager',
          approvalTitle: 'Approved legal exception',
          status: 'approved',
          createdByUserId: 3003,
          approvedByUserId: 77,
          createdAt: '2026-06-25T10:00:00Z',
          approvedAt: '2026-06-25T10:20:00Z'
        }
      ],
      rejected: []
    };

    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      const url = new URL(route.request().url());
      const query = Object.fromEntries(url.searchParams.entries());
      capturedQueries.push(query);
      const items = (approvalRecordsByStatus[query.status ?? 'pending'] ?? []).filter((item) => {
        const matchesCreatedBy = !query.createdByUserId || String(item.createdByUserId) === query.createdByUserId;
        const matchesApprovedBy = !query.approvedByUserId || String(item.approvedByUserId) === query.approvedByUserId;
        const matchesCreatedAtFrom = !query.createdAtFrom || String(item.createdAt) >= query.createdAtFrom;
        const matchesCreatedAtTo = !query.createdAtTo || String(item.createdAt) <= query.createdAtTo;
        return matchesCreatedBy && matchesApprovedBy && matchesCreatedAtFrom && matchesCreatedAtTo;
      });
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items,
            page: 1,
            pageSize: 20,
            total: items.length
          }
        }
      });
    });

    await page.goto('/rules/approvals');

    await page.getByLabel('发起人编号筛选').fill('1001');
    await page.getByLabel('创建开始时间筛选').fill('2026-06-25T08:00');
    await page.getByLabel('创建结束时间筛选').fill('2026-06-25T08:30');
    await page.getByRole('button', { name: '应用筛选' }).click();

    await expect(page.getByText('待审批 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Quarter close approval')).toBeVisible();
    await expect(page.getByText('Ops budget approval')).toHaveCount(0);

    const matchingQuery = capturedQueries.find((query) => query.createdByUserId === '1001');
    expect(matchingQuery).toMatchObject({
      createdByUserId: '1001',
      createdAtFrom: '2026-06-25T08:00:00Z',
      createdAtTo: '2026-06-25T08:30:00Z'
    });

    await page.getByRole('button', { name: '已通过' }).click();
    await page.getByLabel('审批人编号筛选').fill('66');
    await page.getByRole('button', { name: '应用筛选' }).click();

    await expect(page.getByText('已通过 1', { exact: true })).toBeVisible();
    await expect(page.getByText('Approved finance exception')).toBeVisible();
    await expect(page.getByText('Approved legal exception')).toHaveCount(0);

    const approvedQuery = [...capturedQueries].reverse().find((query) => query.status === 'approved' && query.approvedByUserId === '66');
    expect(approvedQuery).toMatchObject({
      status: 'approved',
      createdByUserId: '1001',
      approvedByUserId: '66',
      createdAtFrom: '2026-06-25T08:00:00Z',
      createdAtTo: '2026-06-25T08:30:00Z'
    });
  });

  test('batch approves selected pending approvals and refreshes approved history', async ({ page }) => {
    let batchActionPayload: Record<string, unknown> | null = null;
    let approvalRecordsByStatus: Record<string, Array<Record<string, unknown>>> = {
      pending: [
        {
          approvalRecordId: 901,
          ruleId: 41,
          runId: 301,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Batch approval A',
          status: 'pending',
          createdAt: '2026-06-25T08:10:00Z'
        },
        {
          approvalRecordId: 902,
          ruleId: 42,
          runId: 302,
          nodeId: 'financeApproval',
          assigneeRole: 'finance_manager',
          approvalTitle: 'Batch approval B',
          status: 'pending',
          createdAt: '2026-06-25T08:12:00Z'
        }
      ],
      approved: [],
      rejected: []
    };

    await page.route('**/api/v1/rules/approval-records/batch-actions', async (route) => {
      batchActionPayload = await route.request().postDataJSON();
      const approvedAt = '2026-06-25T08:20:00Z';
      const selectedIds = new Set(((batchActionPayload?.approvalRecordIds as Array<number>) ?? []).map(String));
      const selectedItems = approvalRecordsByStatus.pending.filter((item) => selectedIds.has(String(item.approvalRecordId)));
      approvalRecordsByStatus.pending = approvalRecordsByStatus.pending.filter((item) => !selectedIds.has(String(item.approvalRecordId)));
      approvalRecordsByStatus.approved = selectedItems.map((item) => ({
        ...item,
        status: 'approved',
        approvalComment: '批量通过',
        approvedAt
      }));
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            action: 'approve',
            requestedCount: 2,
            succeededCount: 2,
            failedCount: 0,
            items: [
              { approvalRecordId: 901, result: 'succeeded', status: 'approved' },
              { approvalRecordId: 902, result: 'succeeded', status: 'approved' }
            ]
          }
        }
      });
    });

    await page.route('**/api/v1/rules/approval-records**', async (route) => {
      if (route.request().url().includes('/batch-actions')) {
        await route.fallback();
        return;
      }
      const url = new URL(route.request().url());
      const status = url.searchParams.get('status') ?? 'pending';
      const items = approvalRecordsByStatus[status] ?? [];
      await route.fulfill({
        json: {
          code: 200,
          message: 'ok',
          data: {
            items,
            page: 1,
            pageSize: 20,
            total: items.length
          }
        }
      });
    });

    await page.goto('/rules/approvals');

    await expect(page.getByText('待审批 2', { exact: true })).toBeVisible();
    await page.getByLabel('选择审批记录 901').check();
    await page.getByLabel('选择审批记录 902').check();
    await page.getByRole('button', { name: '批量通过' }).click();

    await expect(page.getByText('批量审批完成：成功 2 条，失败 0 条。')).toBeVisible();
    await expect(page.getByText('待审批 0', { exact: true })).toBeVisible();
    expect(batchActionPayload).toMatchObject({
      action: 'approve',
      approvalRecordIds: [901, 902],
      comment: '批量通过'
    });

    await page.getByRole('button', { name: '已通过' }).click();
    await expect(page.getByText('已通过 2', { exact: true })).toBeVisible();
    await expect(page.getByText('Batch approval A')).toBeVisible();
    await expect(page.getByText('Batch approval B')).toBeVisible();
    await expect(page.getByText('审批备注 批量通过')).toHaveCount(2);
  });
});
