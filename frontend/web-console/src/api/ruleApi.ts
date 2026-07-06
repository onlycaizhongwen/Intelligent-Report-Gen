import { apiClient } from './client';

export interface ApprovalTemplateStepPayload {
  stepId: string;
  approvalTitle: string;
  assigneeRoles: string[];
  approvalMode?: 'all' | 'any';
  slaHours?: number;
  slaEscalations?: Array<{
    afterHours: number;
    role: string;
  }>;
}

export interface ApprovalTemplatePayload {
  name: string;
  description?: string;
  status?: 'enabled' | 'disabled';
  steps: ApprovalTemplateStepPayload[];
}

export const ruleApi = {
  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listRules: (params: { page: number; pageSize: number }) => apiClient.get('/rules', { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  createRule: (payload: unknown) => apiClient.post('/rules', payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  createApprovalDelegateRule: (payload: {
    assigneeRole: string;
    delegateRole: string;
    activeFrom?: string;
    activeTo?: string;
    activeWeekdays?: string[];
    activeDates?: string[];
    reason?: string;
  }) => apiClient.post('/rules/approval-delegate-rules', payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listApprovalDelegateRules: (params: {
    page: number;
    pageSize: number;
    status?: 'enabled' | 'disabled' | string;
    assigneeRole?: string;
  }) => apiClient.get('/rules/approval-delegate-rules', { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  batchImportApprovalDelegateRules: (payload: {
    rules: Array<{
      assigneeRole: string;
      delegateRole: string;
      activeFrom?: string;
      activeTo?: string;
      activeWeekdays?: string[];
      activeDates?: string[];
      reason?: string;
    }>;
  }) => apiClient.post('/rules/approval-delegate-rules/batch-import', payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  createApprovalTemplate: (payload: ApprovalTemplatePayload) =>
    apiClient.post('/rules/approval-templates', payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listApprovalTemplates: (params: {
    page: number;
    pageSize: number;
    status?: 'enabled' | 'disabled' | string;
  }) => apiClient.get('/rules/approval-templates', { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listApprovalTemplateUsage: (templateId: string, params: {
    page: number;
    pageSize: number;
  }) => apiClient.get(`/rules/approval-templates/${templateId}/usage`, { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  updateApprovalTemplate: (templateId: string, payload: ApprovalTemplatePayload) =>
    apiClient.put(`/rules/approval-templates/${templateId}`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listApprovalTemplateVersions: (templateId: string) =>
    apiClient.get(`/rules/approval-templates/${templateId}/versions`),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  compareApprovalTemplateVersions: (templateId: string, params: {
    baseVersion: number;
    targetVersion: number;
  }) => apiClient.get(`/rules/approval-templates/${templateId}/versions/diff`, { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  getApprovalTemplateVersion: (templateId: string, version: number) =>
    apiClient.get(`/rules/approval-templates/${templateId}/versions/${version}`),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  rollbackApprovalTemplateVersion: (templateId: string, version: number) =>
    apiClient.post(`/rules/approval-templates/${templateId}/versions/${version}/rollback`, {}),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  disableApprovalTemplate: (templateId: string) =>
    apiClient.post(`/rules/approval-templates/${templateId}/disable`, {}),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  enableApprovalTemplate: (templateId: string) =>
    apiClient.post(`/rules/approval-templates/${templateId}/enable`, {}),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  updateApprovalDelegateRule: (delegateRuleId: string, payload: {
    assigneeRole: string;
    delegateRole: string;
    activeFrom?: string;
    activeTo?: string;
    activeWeekdays?: string[];
    activeDates?: string[];
    reason?: string;
  }) => apiClient.put(`/rules/approval-delegate-rules/${delegateRuleId}`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  disableApprovalDelegateRule: (delegateRuleId: string, payload: {
    reason?: string;
  }) => apiClient.post(`/rules/approval-delegate-rules/${delegateRuleId}/disable`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  enableApprovalDelegateRule: (delegateRuleId: string, payload: {
    reason?: string;
  }) => apiClient.post(`/rules/approval-delegate-rules/${delegateRuleId}/enable`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  saveRule: (ruleId: string, payload: unknown) => apiClient.put(`/rules/${ruleId}`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  debugRule: (ruleId: string, payload: unknown) => apiClient.post(`/rules/${ruleId}/debug-runs`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  submitForReview: (ruleId: string, payload: unknown) => apiClient.post(`/rules/${ruleId}/review-submissions`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  approveRule: (ruleId: string, payload: unknown) => apiClient.post(`/rules/${ruleId}/approvals`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  executeRule: (ruleId: string, payload: unknown) => apiClient.post(`/rules/${ruleId}/runs`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listRuns: (ruleId: string, params: { page: number; pageSize: number }) => apiClient.get(`/rules/${ruleId}/runs`, { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  subprocessRunTopology: (ruleId: string, runId: string) =>
    apiClient.get(`/rules/${ruleId}/runs/${runId}/subprocess-topology`),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listApprovalRecords: (ruleId: string, params: { page: number; pageSize: number }) =>
    apiClient.get(`/rules/${ruleId}/approval-records`, { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listPendingApprovalRecords: (params: {
    page: number;
    pageSize: number;
    status?: 'pending' | 'approved' | 'rejected' | 'closed' | 'supplement_required' | 'resubmitted';
    ruleId?: string;
    assigneeRole?: string;
    approvalTitle?: string;
    createdByUserId?: string;
    approvedByUserId?: string;
    createdAtFrom?: string;
    createdAtTo?: string;
  }) =>
    apiClient.get('/rules/approval-records', { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  handleApprovalRecord: (ruleId: string, approvalRecordId: string, payload: {
    action: 'approve' | 'reject';
    comment?: string;
  }) => apiClient.post(`/rules/${ruleId}/approval-records/${approvalRecordId}/actions`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  submitApprovalSupplement: (ruleId: string, approvalRecordId: string, payload: {
    comment?: string;
    evidenceUrl?: string;
  }) => apiClient.post(`/rules/${ruleId}/approval-records/${approvalRecordId}/supplements`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  uploadApprovalSupplementAttachment: (ruleId: string, approvalRecordId: string, formData: FormData) =>
    apiClient.post(`/rules/${ruleId}/approval-records/${approvalRecordId}/supplement-attachments`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  remindApprovalRecord: (ruleId: string, approvalRecordId: string) =>
    apiClient.post(`/rules/${ruleId}/approval-records/${approvalRecordId}/reminders`, {}),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  batchHandleApprovalRecords: (payload: {
    action: 'approve' | 'reject';
    approvalRecordIds: Array<number | string>;
    comment?: string;
  }) => apiClient.post('/rules/approval-records/batch-actions', payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  listActionExecutions: (ruleId: string, params: { page: number; pageSize: number }) =>
    apiClient.get(`/rules/${ruleId}/action-executions`, { params }),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  metrics: (ruleId: string) => apiClient.get(`/rules/${ruleId}/metrics`),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  configureSchedule: (ruleId: string, payload: unknown) => apiClient.put(`/rules/${ruleId}/schedule`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  retrySchedule: (ruleId: string, payload: unknown) => apiClient.post(`/rules/${ruleId}/schedule/retry`, payload),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  retryWebhookActionExecution: (ruleId: string, actionExecutionId: string) =>
    apiClient.post(`/rules/${ruleId}/action-executions/${actionExecutionId}/retry`, {}),

  /** OpenSpec: rule-engine / REQ-RULE-001 */
  batchHandleWebhookActionExecutions: (ruleId: string, payload: {
    operation: 'retry' | 'ignore';
    actionExecutionIds: Array<number | string>;
    reason?: string;
  }) => apiClient.post(`/rules/${ruleId}/action-executions/batch`, payload)
};
