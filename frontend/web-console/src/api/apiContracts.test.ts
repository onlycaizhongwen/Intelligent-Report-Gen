import { describe, expect, it, vi } from 'vitest';
import { collaborationApi } from './collaborationApi';
import { ruleApi } from './ruleApi';
import { shareApi } from './shareApi';
import { auditApi, type DashboardOverview } from './auditApi';
import { knowledgeApi } from './knowledgeApi';
import { reportApi } from './reportApi';
import { apiClient } from './client';
import { adminApi } from './adminApi';
import { notificationApi } from './notificationApi';

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn()
  }
}));

describe('frontend API contracts', () => {
  it('uses Java collaboration endpoints from the S4 contract', () => {
    collaborationApi.createComment('42', {
      content: 'needs evidence',
      assigneeUserId: '7',
      anchor: {
        sectionId: 'summary',
        startOffset: 8,
        endOffset: 16,
        selectedText: 'evidence'
      }
    });
    collaborationApi.updateTaskStatus('99', { status: 'done' });

    expect(apiClient.post).toHaveBeenCalledWith('/reports/42/annotations', {
      content: 'needs evidence',
      assigneeUserId: '7',
      anchor: {
        sectionId: 'summary',
        startOffset: 8,
        endOffset: 16,
        selectedText: 'evidence'
      }
    });
    expect(apiClient.put).toHaveBeenCalledWith('/tasks/99/status', { status: 'done' });
  });

  it('uses admin user management endpoints from the S4 contract', () => {
    adminApi.listUsers({ page: 1, pageSize: 100 });
    adminApi.batchImportUsers({
      users: [
        {
          username: 'analyst.one',
          displayName: 'Analyst One',
          department: 'Finance Center',
          position: 'Senior Analyst',
          roles: ['analyst']
        },
        { username: 'viewer.one', displayName: 'Viewer One', roles: ['viewer'] }
      ]
    });
    adminApi.updateUserStatus(42, { status: 'disabled' });
    adminApi.organizationDirectory();
    adminApi.createOrganizationUnit({
      code: 'FIN',
      name: 'Finance Center',
      parentId: 1,
      unitType: 'department',
      sortOrder: 20
    });
    adminApi.updateOrganizationUnit(3, {
      name: 'Finance Risk Team',
      parentId: 2,
      unitType: 'team',
      sortOrder: 5
    });
    adminApi.createOrganizationPosition({
      organizationUnitId: 2,
      code: 'finance_manager',
      name: 'Finance Manager',
      roles: ['finance_manager', 'report_reviewer'],
      managerUserId: 71,
      sortOrder: 10
    });
    adminApi.assignUserToOrganizationPosition({
      userId: 71,
      positionId: 10,
      primary: true,
      activeFrom: '2026-01-01T00:00:00Z',
      activeTo: '2026-12-31T23:59:59Z'
    });
    adminApi.batchImportOrganizationPositionAssignments({
      assignments: [
        {
          userId: 71,
          positionId: 10,
          primary: true,
          activeFrom: '2026-01-01T00:00:00Z',
          activeTo: '2026-12-31T23:59:59Z'
        }
      ]
    });
    adminApi.disableOrganizationPositionAssignment(99, { reason: 'role ended' });
    adminApi.updateOrganizationPositionAssignment(99, {
      primary: true,
      activeFrom: '2026-01-01T00:00:00Z',
      activeTo: '2026-12-31T23:59:59Z'
    });

    expect(apiClient.get).toHaveBeenCalledWith('/users', {
      params: { page: 1, pageSize: 100 }
    });
    expect(apiClient.post).toHaveBeenCalledWith('/users/batch-import', {
      users: [
        {
          username: 'analyst.one',
          displayName: 'Analyst One',
          department: 'Finance Center',
          position: 'Senior Analyst',
          roles: ['analyst']
        },
        { username: 'viewer.one', displayName: 'Viewer One', roles: ['viewer'] }
      ]
    });
    expect(apiClient.put).toHaveBeenCalledWith('/users/42/status', { status: 'disabled' });
    expect(apiClient.get).toHaveBeenCalledWith('/organization-directory');
    expect(apiClient.post).toHaveBeenCalledWith('/organization-units', {
      code: 'FIN',
      name: 'Finance Center',
      parentId: 1,
      unitType: 'department',
      sortOrder: 20
    });
    expect(apiClient.put).toHaveBeenCalledWith('/organization-units/3', {
      name: 'Finance Risk Team',
      parentId: 2,
      unitType: 'team',
      sortOrder: 5
    });
    expect(apiClient.post).toHaveBeenCalledWith('/organization-positions', {
      organizationUnitId: 2,
      code: 'finance_manager',
      name: 'Finance Manager',
      roles: ['finance_manager', 'report_reviewer'],
      managerUserId: 71,
      sortOrder: 10
    });
    expect(apiClient.post).toHaveBeenCalledWith('/organization-position-assignments', {
      userId: 71,
      positionId: 10,
      primary: true,
      activeFrom: '2026-01-01T00:00:00Z',
      activeTo: '2026-12-31T23:59:59Z'
    });
    expect(apiClient.post).toHaveBeenCalledWith('/organization-position-assignments/batch-import', {
      assignments: [
        {
          userId: 71,
          positionId: 10,
          primary: true,
          activeFrom: '2026-01-01T00:00:00Z',
          activeTo: '2026-12-31T23:59:59Z'
        }
      ]
    });
    expect(apiClient.post).toHaveBeenCalledWith('/organization-position-assignments/99/disable', {
      reason: 'role ended'
    });
    expect(apiClient.put).toHaveBeenCalledWith('/organization-position-assignments/99', {
      primary: true,
      activeFrom: '2026-01-01T00:00:00Z',
      activeTo: '2026-12-31T23:59:59Z'
    });
  });

  it('types organization directory with departments, positions, roles and users', () => {
    const directory: import('./adminApi').OrganizationDirectory = {
      departments: [
        {
          department: 'Finance Center',
          positions: [
            {
              position: 'Finance Manager',
              roles: ['finance_manager'],
              users: [
                {
                  userId: 1,
                  username: 'fin.manager',
                  displayName: 'Fiona Manager',
                  roles: ['finance_manager']
                }
              ]
            }
          ]
        }
      ],
      organizationTree: [
        {
          unitId: 1,
          code: 'HQ',
          name: 'Headquarters',
          parentId: null,
          unitType: 'company',
          positions: [],
          children: [
            {
              unitId: 2,
              code: 'FIN',
              name: 'Finance Center',
              parentId: 1,
              unitType: 'department',
              positions: [
                {
                  positionId: 10,
                  organizationUnitId: 2,
                  code: 'finance_manager',
                  name: 'Finance Manager',
                  roles: ['finance_manager'],
                  managerUserId: 1
                }
              ],
              children: []
            }
          ]
        }
      ],
      roles: [
        {
          role: 'finance_manager',
          department: 'Finance Center',
          position: 'Finance Manager',
          users: [
            {
              userId: 1,
              username: 'fin.manager',
              displayName: 'Fiona Manager',
              roles: ['finance_manager']
            }
          ]
        }
      ]
    };

    expect(directory.departments[0].positions[0].roles).toContain('finance_manager');
    expect(directory.organizationTree?.[0].children[0].positions[0].managerUserId).toBe(1);
  });

  it('uses rule debug-run endpoint instead of a non-existent validate endpoint', () => {
    ruleApi.debugRule('12', { sample: { daysOverdue: 12 } });

    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/debug-runs', { sample: { daysOverdue: 12 } });
  });

  it('creates approval delegate rules through the rule management endpoint', () => {
    ruleApi.createApprovalDelegateRule({
      assigneeRole: 'finance_manager',
      delegateRole: 'finance_delegate',
      activeFrom: '2026-06-26T08:00:00Z',
      activeTo: '2026-06-26T18:00:00Z',
      activeWeekdays: ['MONDAY', 'WEDNESDAY'],
      activeDates: ['2026-06-26', '2026-06-28'],
      reason: 'quarter close coverage'
    });

    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-delegate-rules', {
      assigneeRole: 'finance_manager',
      delegateRole: 'finance_delegate',
      activeFrom: '2026-06-26T08:00:00Z',
      activeTo: '2026-06-26T18:00:00Z',
      activeWeekdays: ['MONDAY', 'WEDNESDAY'],
      activeDates: ['2026-06-26', '2026-06-28'],
      reason: 'quarter close coverage'
    });
  });

  it('lists, disables and enables approval delegate rules through management endpoints', () => {
    ruleApi.listApprovalDelegateRules({
      page: 1,
      pageSize: 20,
      status: 'enabled',
      assigneeRole: 'finance_manager'
    });
    ruleApi.disableApprovalDelegateRule('501', { reason: 'manager returned' });
    ruleApi.enableApprovalDelegateRule('501', { reason: 'manager away again' });
    ruleApi.updateApprovalDelegateRule('501', {
      assigneeRole: 'finance_director',
      delegateRole: 'finance_director_delegate',
      activeFrom: '2026-06-27T08:00:00Z',
      activeTo: '2026-06-27T18:00:00Z',
      activeWeekdays: ['TUESDAY'],
      activeDates: ['2026-06-27'],
      reason: 'director travel cover'
    });
    ruleApi.batchImportApprovalDelegateRules({
      rules: [
        {
          assigneeRole: 'finance_manager',
          delegateRole: 'finance_delegate',
          activeFrom: '2026-06-29T08:00:00Z',
          activeTo: '2026-06-29T18:00:00Z',
          activeWeekdays: ['MONDAY'],
          activeDates: ['2026-06-29'],
          reason: 'quarter close cover'
        }
      ]
    });

    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-delegate-rules', {
      params: {
        page: 1,
        pageSize: 20,
        status: 'enabled',
        assigneeRole: 'finance_manager'
      }
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-delegate-rules/501/disable', {
      reason: 'manager returned'
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-delegate-rules/501/enable', {
      reason: 'manager away again'
    });
    expect(apiClient.put).toHaveBeenCalledWith('/rules/approval-delegate-rules/501', {
      assigneeRole: 'finance_director',
      delegateRole: 'finance_director_delegate',
      activeFrom: '2026-06-27T08:00:00Z',
      activeTo: '2026-06-27T18:00:00Z',
      activeWeekdays: ['TUESDAY'],
      activeDates: ['2026-06-27'],
      reason: 'director travel cover'
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-delegate-rules/batch-import', {
      rules: [
        {
          assigneeRole: 'finance_manager',
          delegateRole: 'finance_delegate',
          activeFrom: '2026-06-29T08:00:00Z',
          activeTo: '2026-06-29T18:00:00Z',
          activeWeekdays: ['MONDAY'],
          activeDates: ['2026-06-29'],
          reason: 'quarter close cover'
        }
      ]
    });
  });

  it('uses approval template management endpoints from REQ-RULE-001', () => {
    ruleApi.createApprovalTemplate({
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
          slaHours: 8
        }
      ]
    });
    ruleApi.listApprovalTemplates({ page: 1, pageSize: 20, status: 'enabled' });
    ruleApi.listApprovalTemplateUsage('900', { page: 2, pageSize: 10 });
    ruleApi.updateApprovalTemplate('900', {
      name: 'Finance two-level approval v2',
      description: 'Finance manager, finance director, then CFO',
      status: 'enabled',
      steps: [
        {
          stepId: 'financeManager',
          approvalTitle: 'Finance manager approval',
          assigneeRoles: ['finance_manager'],
          approvalMode: 'all',
          slaHours: 4
        },
        {
          stepId: 'financeDirector',
          approvalTitle: 'Finance director approval',
          assigneeRoles: ['finance_director'],
          approvalMode: 'all',
          slaHours: 8
        },
        {
          stepId: 'cfoApproval',
          approvalTitle: 'CFO approval',
          assigneeRoles: ['cfo'],
          approvalMode: 'all',
          slaHours: 12
        }
      ]
    });
    ruleApi.listApprovalTemplateVersions('900');
    ruleApi.compareApprovalTemplateVersions('900', { baseVersion: 1, targetVersion: 2 });
    ruleApi.getApprovalTemplateVersion('900', 2);
    ruleApi.rollbackApprovalTemplateVersion('900', 1);
    ruleApi.disableApprovalTemplate('900');
    ruleApi.enableApprovalTemplate('900');

    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-templates', {
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
          slaHours: 8
        }
      ]
    });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-templates', {
      params: { page: 1, pageSize: 20, status: 'enabled' }
    });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-templates/900/usage', {
      params: { page: 2, pageSize: 10 }
    });
    expect(apiClient.put).toHaveBeenCalledWith('/rules/approval-templates/900', {
      name: 'Finance two-level approval v2',
      description: 'Finance manager, finance director, then CFO',
      status: 'enabled',
      steps: [
        {
          stepId: 'financeManager',
          approvalTitle: 'Finance manager approval',
          assigneeRoles: ['finance_manager'],
          approvalMode: 'all',
          slaHours: 4
        },
        {
          stepId: 'financeDirector',
          approvalTitle: 'Finance director approval',
          assigneeRoles: ['finance_director'],
          approvalMode: 'all',
          slaHours: 8
        },
        {
          stepId: 'cfoApproval',
          approvalTitle: 'CFO approval',
          assigneeRoles: ['cfo'],
          approvalMode: 'all',
          slaHours: 12
        }
      ]
    });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-templates/900/versions/2');
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-templates/900/versions');
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-templates/900/versions/diff', {
      params: { baseVersion: 1, targetVersion: 2 }
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-templates/900/versions/1/rollback', {});
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-templates/900/disable', {});
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-templates/900/enable', {});
  });

  it('uses rule review approval and production run endpoints from REQ-RULE-001', () => {
    ruleApi.submitForReview('12', { comment: 'ready for approval' });
    ruleApi.approveRule('12', { comment: 'approved for production' });
    ruleApi.executeRule('12', { sample: { daysOverdue: 45 } });
    ruleApi.listRuns('12', { page: 1, pageSize: 10 });
    ruleApi.subprocessRunTopology('12', '100');
    ruleApi.listApprovalRecords('12', { page: 1, pageSize: 10 });
    ruleApi.listPendingApprovalRecords({
      page: 1,
      pageSize: 10,
      status: 'pending',
      ruleId: '12',
      assigneeRole: 'finance_manager',
      createdByUserId: '1',
      approvedByUserId: '66',
      approvalTitle: 'Finance review',
      createdAtFrom: '2026-06-25T08:00:00Z',
      createdAtTo: '2026-06-25T10:00:00Z'
    });
    ruleApi.listPendingApprovalRecords({ page: 1, pageSize: 10, status: 'approved' });
    ruleApi.listPendingApprovalRecords({ page: 1, pageSize: 10, status: 'rejected' });
    ruleApi.listPendingApprovalRecords({ page: 1, pageSize: 10, status: 'closed' });
    ruleApi.listPendingApprovalRecords({ page: 1, pageSize: 10, status: 'supplement_required' });
    ruleApi.listPendingApprovalRecords({ page: 1, pageSize: 10, status: 'resubmitted' });
    ruleApi.handleApprovalRecord('12', '700', { action: 'approve', comment: 'finance approved' });
    ruleApi.submitApprovalSupplement('12', '700', {
      comment: 'uploaded corrected evidence',
      evidenceUrl: 'minio://report-evidence/finance-rework.pdf'
    });
    ruleApi.remindApprovalRecord('12', '700');
    ruleApi.batchHandleApprovalRecords({
      action: 'approve',
      approvalRecordIds: [700, 701],
      comment: 'approved in batch'
    });
    ruleApi.listActionExecutions('12', { page: 1, pageSize: 10 });
    ruleApi.metrics('12');
    ruleApi.configureSchedule('12', {
      scheduleEnabled: true,
      scheduleIntervalSeconds: 300,
      nextRunAt: '2026-06-24T08:00:00Z',
      maxRetryCount: 3,
      scheduleInput: { sample: { daysOverdue: 45 } }
    });
    ruleApi.retrySchedule('12', {
      nextRunAt: '2026-06-24T08:05:00Z',
      scheduleInput: { sample: { daysOverdue: 45 } }
    });
    ruleApi.retryWebhookActionExecution('12', '99');
    ruleApi.batchHandleWebhookActionExecutions('12', {
      operation: 'ignore',
      actionExecutionIds: [99, 100],
      reason: 'closed manually'
    });

    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/review-submissions', { comment: 'ready for approval' });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/approvals', { comment: 'approved for production' });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/runs', { sample: { daysOverdue: 45 } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/12/runs', { params: { page: 1, pageSize: 10 } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/12/runs/100/subprocess-topology');
    expect(apiClient.get).toHaveBeenCalledWith('/rules/12/approval-records', { params: { page: 1, pageSize: 10 } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-records', {
      params: {
        page: 1,
        pageSize: 10,
        status: 'pending',
        ruleId: '12',
        assigneeRole: 'finance_manager',
        createdByUserId: '1',
        approvedByUserId: '66',
        approvalTitle: 'Finance review',
        createdAtFrom: '2026-06-25T08:00:00Z',
        createdAtTo: '2026-06-25T10:00:00Z'
      }
    });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-records', { params: { page: 1, pageSize: 10, status: 'approved' } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-records', { params: { page: 1, pageSize: 10, status: 'rejected' } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-records', { params: { page: 1, pageSize: 10, status: 'supplement_required' } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/approval-records', { params: { page: 1, pageSize: 10, status: 'resubmitted' } });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/approval-records/700/actions', { action: 'approve', comment: 'finance approved' });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/approval-records/700/supplements', {
      comment: 'uploaded corrected evidence',
      evidenceUrl: 'minio://report-evidence/finance-rework.pdf'
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/approval-records/700/reminders', {});
    expect(apiClient.post).toHaveBeenCalledWith('/rules/approval-records/batch-actions', {
      action: 'approve',
      approvalRecordIds: [700, 701],
      comment: 'approved in batch'
    });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/12/action-executions', { params: { page: 1, pageSize: 10 } });
    expect(apiClient.get).toHaveBeenCalledWith('/rules/12/metrics');
    expect(apiClient.put).toHaveBeenCalledWith('/rules/12/schedule', {
      scheduleEnabled: true,
      scheduleIntervalSeconds: 300,
      nextRunAt: '2026-06-24T08:00:00Z',
      maxRetryCount: 3,
      scheduleInput: { sample: { daysOverdue: 45 } }
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/schedule/retry', {
      nextRunAt: '2026-06-24T08:05:00Z',
      scheduleInput: { sample: { daysOverdue: 45 } }
    });
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/action-executions/99/retry', {});
    expect(apiClient.post).toHaveBeenCalledWith('/rules/12/action-executions/batch', {
      operation: 'ignore',
      actionExecutionIds: [99, 100],
      reason: 'closed manually'
    });
  });

  it('uses public share report endpoint for read-only external report details', () => {
    shareApi.getSharedReport('share-token', { password: 'secret' });

    expect(apiClient.post).toHaveBeenCalledWith('/share-links/share-token/report', { password: 'secret' });
  });

  it('types shared report details with explicit download policy and export summaries', () => {
    const detail: import('./shareApi').SharedReportDetail = {
      accessGranted: true,
      shareToken: 'share-token',
      reportId: '42',
      title: 'Quarterly report',
      status: 'completed',
      allowDownload: true,
      sections: [],
      exports: [
        {
          exportFileId: '9001',
          fileName: 'quarterly-report.md',
          format: 'markdown',
          contentType: 'text/markdown',
          sizeBytes: 128
        }
      ]
    };

    expect(detail.exports[0].exportFileId).toBe('9001');
  });

  it('uses public share export endpoint for explicitly allowed external downloads', () => {
    shareApi.getSharedExportDownloadUrl('share-token', '9001', { password: 'secret' });

    expect(apiClient.post).toHaveBeenCalledWith('/share-links/share-token/exports/9001/download-url', { password: 'secret' });
  });

  it('uses owner share management endpoints for creating and revoking report shares', () => {
    shareApi.createShareLink('88', {
      password: 'ExternalPass#1',
      allowDownload: true,
      allowedDownloadFormats: ['markdown'],
      maxAccessCount: 1,
      allowedVisitors: ['external@example.com'],
      allowedVisitorDomains: ['partner.com'],
      singleUse: true,
      expiresAt: '2026-06-27T08:00:00Z'
    });
    shareApi.revokeShareLink('share-token');

    expect(apiClient.post).toHaveBeenCalledWith('/reports/88/share-links', {
      password: 'ExternalPass#1',
      allowDownload: true,
      allowedDownloadFormats: ['markdown'],
      maxAccessCount: 1,
      allowedVisitors: ['external@example.com'],
      allowedVisitorDomains: ['partner.com'],
      singleUse: true,
      expiresAt: '2026-06-27T08:00:00Z'
    });
    expect(apiClient.post).toHaveBeenCalledWith('/share-links/share-token/revoke', {});
  });

  it('uses dashboard overview endpoint with prototype metric fields', () => {
    auditApi.dashboardOverview('last7days');
    const overview: DashboardOverview = {
      range: 'last7days',
      cards: {
        reportOutputs: 7,
        knowledgeItems: 42,
        activeDataSources: 3,
        citationHitRate: 0.875,
        activeUsers: 5
      },
      reportTrend: [{ date: '2026-06-23', completedReports: 2 }],
      knowledgeRank: [{ knowledgeBaseId: 1, name: 'Finance KB', references: 9 }],
      ruleScheduleHealth: {
        scheduledRules: 4,
        failedScheduledRules: 2,
        blockedScheduledRules: 1,
        recentAlerts: 1
      },
      recentActivities: [{ operationType: 'report_export', result: 'succeeded' }]
    };

    expect(apiClient.get).toHaveBeenCalledWith('/dashboard/overview', { params: { range: 'last7days' } });
    expect(overview.cards.citationHitRate).toBe(0.875);
    expect(overview.ruleScheduleHealth.blockedScheduledRules).toBe(1);
  });

  it('separates personal history from global audit log endpoints', () => {
    auditApi.history({ page: 1, pageSize: 10 });
    auditApi.listAuditLogs({ page: 2, pageSize: 20 });

    expect(apiClient.get).toHaveBeenCalledWith('/history', { params: { page: 1, pageSize: 10 } });
    expect(apiClient.get).toHaveBeenCalledWith('/audit-logs', { params: { page: 2, pageSize: 20 } });
  });

  it('uses data source sync endpoints with explicit sync-run logs', () => {
    knowledgeApi.saveDataSource({
      name: 'ERP PostgreSQL',
      sourceType: 'postgresql',
      endpoint: 'jdbc:postgresql://localhost:5432/erp',
      username: 'erp_reader',
      password: 'secret',
      knowledgeBaseId: 1,
      syncQuery: 'select title, content from reports',
      fieldMapping: {
        rowsPath: 'data.items',
        titleField: 'headline',
        contentField: 'body',
        method: 'POST',
        authType: 'api_key',
        apiKeyHeader: 'X-API-Key',
        headers: { 'X-Tenant': 'finance' },
        bodyTemplate: '{"period":"2026Q1"}',
        pageParam: 'page',
        pageStart: 1,
        pageSizeParam: 'pageSize',
        pageSize: 100,
        maxPages: 3
      },
      cursorColumn: 'id',
      scheduleEnabled: true,
      scheduleIntervalSeconds: 300,
      maxRetryCount: 3
    });
    knowledgeApi.testConnection({ dataSourceId: 12 });
    knowledgeApi.startDataSourceSync('12', { mode: 'manual' });
    knowledgeApi.listDataSourceSyncRuns('12', { page: 1, pageSize: 10 });

    expect(apiClient.post).toHaveBeenCalledWith('/data-sources', {
      name: 'ERP PostgreSQL',
      sourceType: 'postgresql',
      endpoint: 'jdbc:postgresql://localhost:5432/erp',
      username: 'erp_reader',
      password: 'secret',
      knowledgeBaseId: 1,
      syncQuery: 'select title, content from reports',
      fieldMapping: {
        rowsPath: 'data.items',
        titleField: 'headline',
        contentField: 'body',
        method: 'POST',
        authType: 'api_key',
        apiKeyHeader: 'X-API-Key',
        headers: { 'X-Tenant': 'finance' },
        bodyTemplate: '{"period":"2026Q1"}',
        pageParam: 'page',
        pageStart: 1,
        pageSizeParam: 'pageSize',
        pageSize: 100,
        maxPages: 3
      },
      cursorColumn: 'id',
      scheduleEnabled: true,
      scheduleIntervalSeconds: 300,
      maxRetryCount: 3
    });
    expect(apiClient.post).toHaveBeenCalledWith('/data-sources/test-connection', { dataSourceId: 12 });
    expect(apiClient.post).toHaveBeenCalledWith('/data-sources/12/sync-runs', { mode: 'manual' });
    expect(apiClient.get).toHaveBeenCalledWith('/data-sources/12/sync-runs', { params: { page: 1, pageSize: 10 } });
  });

  it('uses system alert endpoint for data source sync failure notifications', () => {
    notificationApi.listSystemAlerts({ page: 1, pageSize: 10, status: 'unread' });

    const alert: import('./notificationApi').SystemAlert = {
      alertId: 1,
      recipientUserId: 37,
      type: 'knowledge_data_source_sync_failed',
      severity: 'warning',
      status: 'unread',
      resourceType: 'knowledge_data_source',
      resourceId: 12,
      payload: {
        dataSourceId: 12,
        failureReason: 'unsupported or unreachable endpoint',
        syncRunId: 99
      },
      createdAt: '2026-06-24T12:00:00Z'
    };

    expect(apiClient.get).toHaveBeenCalledWith('/system-alerts', {
      params: { page: 1, pageSize: 10, status: 'unread' }
    });
    expect(alert.type).toBe('knowledge_data_source_sync_failed');
  });

  it('deletes knowledge items with explicit referenced-item confirmation flag', () => {
    knowledgeApi.deleteItem('42', false);
    knowledgeApi.deleteItem('42', true);

    expect(apiClient.delete).toHaveBeenCalledWith('/knowledge-items/42', { params: { confirmed: false } });
    expect(apiClient.delete).toHaveBeenCalledWith('/knowledge-items/42', { params: { confirmed: true } });
  });

  it('batch imports knowledge items through the REQ-KB-001 endpoint', () => {
    knowledgeApi.batchImportItems({
      knowledgeBaseId: 1,
      items: [
        { title: 'Market insight', content: 'Revenue increased 12%', sourceType: 'manual' },
        { title: 'Risk memo', content: 'Receivables aging requires follow-up' }
      ]
    });

    expect(apiClient.post).toHaveBeenCalledWith('/knowledge-items/batch-import', {
      knowledgeBaseId: 1,
      items: [
        { title: 'Market insight', content: 'Revenue increased 12%', sourceType: 'manual' },
        { title: 'Risk memo', content: 'Receivables aging requires follow-up' }
      ]
    });
  });

  it('lists current user knowledge bases through the REQ-KB-001 endpoint', () => {
    knowledgeApi.listKnowledgeBases({ page: 1, pageSize: 20 });

    expect(apiClient.get).toHaveBeenCalledWith('/knowledge-bases', { params: { page: 1, pageSize: 20 } });
  });

  it('polls uploaded document status through the REQ-KB-002 endpoint', () => {
    knowledgeApi.uploadDocument(new FormData(), 9);
    knowledgeApi.getDocumentStatus('101');

    expect(apiClient.post).toHaveBeenCalledWith('/documents/upload', expect.any(FormData), {
      timeout: 120_000,
      params: { knowledgeBaseId: 9 },
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    expect(apiClient.get).toHaveBeenCalledWith('/documents/101');
  });

  it('creates Word exports with enterprise brand template fields through REQ-REPORT-004 endpoint', () => {
    reportApi.createExport('88', {
      format: 'docx',
      templateId: 'enterprise-board',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79',
        layout: {
          coverTitle: 'Board Strategy Pack',
          tocTitle: 'Report Outline',
          bodyTitlePrefix: 'Section',
          titleFontSize: 30,
          bodyFontSize: 22,
          headerFontSize: 16,
          footerFontSize: 12
        }
      }
    });

    expect(apiClient.post).toHaveBeenCalledWith('/reports/88/exports', {
      format: 'docx',
      templateId: 'enterprise-board',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79',
        layout: {
          coverTitle: 'Board Strategy Pack',
          tocTitle: 'Report Outline',
          bodyTitlePrefix: 'Section',
          titleFontSize: 30,
          bodyFontSize: 22,
          headerFontSize: 16,
          footerFontSize: 12
        }
      }
    });
  });

  it('creates PDF exports with enterprise brand template fields through REQ-REPORT-004 endpoint', () => {
    reportApi.createExport('88', {
      format: 'pdf',
      templateId: 'enterprise-board',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79'
      }
    });

    expect(apiClient.post).toHaveBeenCalledWith('/reports/88/exports', {
      format: 'pdf',
      templateId: 'enterprise-board',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79'
      }
    });
  });

  it('creates PowerPoint exports with enterprise brand template fields through REQ-REPORT-004 endpoint', () => {
    reportApi.createExport('88', {
      format: 'pptx',
      templateId: 'enterprise-board',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79'
      }
    });

    expect(apiClient.post).toHaveBeenCalledWith('/reports/88/exports', {
      format: 'pptx',
      templateId: 'enterprise-board',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79'
      }
    });
  });

  it('uses controlled export download endpoint through REQ-REPORT-004 contract', () => {
    reportApi.getExportDownloadUrl('7001');

    expect(apiClient.get).toHaveBeenCalledWith('/files/report-exports/7001/download-url');
  });

  it('types enterprise export status with persisted brand snapshot through REQ-REPORT-004 contract', () => {
    type ExportStatus = Awaited<ReturnType<typeof reportApi.getExportStatus>>;
    const status: ExportStatus = {
      exportFileId: '7001',
      status: 'completed',
      brandSnapshot: {
        templateId: 'enterprise-board',
        format: 'pptx',
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79',
        layout: {
          coverTitle: 'Board Strategy Pack',
          tocTitle: 'Report Outline',
          bodyTitlePrefix: 'Section',
          titleFontSize: 30,
          bodyFontSize: 22,
          headerFontSize: 14,
          footerFontSize: 12,
          pageWidth: 468,
          closingTitle: 'Closing',
          closingMessage: '',
          closingContact: ''
        }
      }
    };

    expect(status.brandSnapshot?.layout.pageWidth).toBe(468);
  });

  it('uses enterprise export template governance endpoints through REQ-REPORT-004 contract', () => {
    const payload: import('./reportApi').EnterpriseExportTemplateRequest = {
      templateId: 'enterprise-board',
      name: 'Board report template',
      brand: {
        companyName: 'Contoso Analytics',
        logoObjectKey: 'branding/contoso-logo.png',
        header: 'Confidential Board Report',
        footer: 'Generated by Intelligent Report System',
        fontFamily: 'Aptos',
        primaryColor: '#1F4E79',
        layout: {
          coverTitle: 'Board Strategy Pack',
          tocTitle: 'Report Outline',
          bodyTitlePrefix: 'Section',
          titleFontSize: 30,
          bodyFontSize: 22,
          headerFontSize: 14,
          footerFontSize: 12,
          pageWidth: 468,
          closingTitle: 'Closing',
          closingMessage: '',
          closingContact: ''
        }
      }
    };
    const managedTemplate: import('./reportApi').EnterpriseExportTemplate = {
      id: 1,
      templateId: 'enterprise-board',
      name: 'Board report template',
      version: 'v1',
      status: 'active',
      brandSnapshot: {
        ...payload.brand,
        templateId: 'enterprise-board',
        templateVersion: 'v1',
        format: 'docx',
        layout: payload.brand.layout ?? {}
      },
      createdBy: 37,
      createdAt: '2026-06-30T08:00:00Z',
      updatedAt: '2026-06-30T08:00:00Z'
    };

    reportApi.createEnterpriseExportTemplate(payload);
    reportApi.listEnterpriseExportTemplates({ page: 1, pageSize: 20, status: 'active' });
    reportApi.updateEnterpriseExportTemplate('enterprise-board', {
      name: 'Board report template v2',
      brand: {
        ...payload.brand,
        header: 'Confidential Board Report v2'
      }
    });
    reportApi.listEnterpriseExportTemplateVersions('enterprise-board');
    reportApi.disableEnterpriseExportTemplate('enterprise-board');
    reportApi.enableEnterpriseExportTemplate('enterprise-board');

    expect(apiClient.post).toHaveBeenCalledWith('/enterprise-export-templates', payload);
    expect(apiClient.get).toHaveBeenCalledWith('/enterprise-export-templates', {
      params: { page: 1, pageSize: 20, status: 'active' }
    });
    expect(apiClient.put).toHaveBeenCalledWith('/enterprise-export-templates/enterprise-board', {
      name: 'Board report template v2',
      brand: {
        ...payload.brand,
        header: 'Confidential Board Report v2'
      }
    });
    expect(apiClient.get).toHaveBeenCalledWith('/enterprise-export-templates/enterprise-board/versions');
    expect(apiClient.post).toHaveBeenCalledWith('/enterprise-export-templates/enterprise-board/disable', {});
    expect(apiClient.post).toHaveBeenCalledWith('/enterprise-export-templates/enterprise-board/enable', {});
    expect(managedTemplate.brandSnapshot.templateVersion).toBe('v1');
  });

  it('uses report template library endpoints and schema-driven template task payloads', () => {
    reportApi.listTemplates();
    reportApi.createTemplateTask({
      templateId: 'enterprise-quarterly',
      payload: {
        period: '2026Q1',
        scope: '华东区',
        focus: '收入与回款风险',
        style: '管理摘要'
      }
    });

    expect(apiClient.get).toHaveBeenCalledWith('/report-templates');
    expect(apiClient.post).toHaveBeenCalledWith('/reports/template-generation-tasks', {
      templateId: 'enterprise-quarterly',
      payload: {
        period: '2026Q1',
        scope: '华东区',
        focus: '收入与回款风险',
        style: '管理摘要'
      }
    });
  });

  it('types outline confirmation response with the next generation stage', () => {
    reportApi.confirmOutline('template-task-001', { sections: ['经营概览', '风险与建议'] });
    const response: Awaited<ReturnType<typeof reportApi.confirmOutline>> = {
      taskId: 'template-task-001',
      confirmed: true,
      nextStage: 'retrieval'
    };

    expect(apiClient.put).toHaveBeenCalledWith('/reports/generation-tasks/template-task-001/outline', {
      outline: { sections: ['经营概览', '风险与建议'] },
      confirmed: true
    });
    expect(response.nextStage).toBe('retrieval');
  });

  it('uses report version list, diff and rollback endpoints from REQ-REPORT-005', () => {
    reportApi.getDetail('88');
    reportApi.listVersions('88');
    reportApi.compareVersions('88', { baseVersionId: 20, targetVersionId: 21 });
    reportApi.rollbackVersion('88', 20);

    expect(apiClient.get).toHaveBeenCalledWith('/reports/88');
    expect(apiClient.get).toHaveBeenCalledWith('/reports/88/versions');
    expect(apiClient.get).toHaveBeenCalledWith('/reports/88/versions/diff', {
      params: { baseVersionId: 20, targetVersionId: 21 }
    });
    expect(apiClient.post).toHaveBeenCalledWith('/reports/88/versions/20/rollback', {});
  });

  it('types report detail citation marks with source snapshot and quality scores', () => {
    const detail: import('./reportApi').ReportDetail = {
      reportId: '88',
      title: 'Referenced report',
      status: 'completed',
      sections: [
        {
          sectionId: 'summary',
          heading: '经营摘要',
          content: '收入增长 12%。',
          citations: [
            {
              referenceId: 77,
              sourceTitle: '华东销售数据集',
              sourceType: 'knowledge_document',
              snapshot: '收入同比增长 12%。',
              score: { credibility: 0.92, citationQuality: 0.88 },
              anchor: { sectionNo: 1, heading: '经营摘要', text: '收入增长' }
            }
          ]
        }
      ]
    };

    reportApi.getReference('88', '77');

    expect(apiClient.get).toHaveBeenCalledWith('/reports/88/references/77');
    expect(detail.sections[0].citations?.[0].score?.credibility).toBe(0.92);
  });
});
