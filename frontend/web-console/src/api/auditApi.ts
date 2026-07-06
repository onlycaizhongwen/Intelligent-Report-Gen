import { apiClient } from './client';

export interface DashboardOverview {
  range: string;
  cards: {
    reportOutputs: number;
    knowledgeItems: number;
    activeDataSources: number;
    citationHitRate: number;
    activeUsers: number;
  };
  reportTrend: Array<{
    date: string;
    completedReports: number;
  }>;
  knowledgeRank: Array<{
    knowledgeBaseId: number;
    name: string;
    references: number;
  }>;
  ruleScheduleHealth: {
    scheduledRules: number;
    failedScheduledRules: number;
    blockedScheduledRules: number;
    recentAlerts: number;
  };
  recentActivities: Array<{
    operationLogId?: number;
    operationType?: string;
    actorUserId?: number;
    result?: string;
    createdAt?: string;
    [key: string]: unknown;
  }>;
}

export interface AuditLogRow {
  operationLogId?: number | string;
  id?: number | string;
  operationType?: string;
  action?: string;
  actorUserId?: number | string;
  operator?: string;
  resourceType?: string;
  resourceId?: number | string;
  result?: string;
  createdAt?: string;
  [key: string]: unknown;
}

export interface AuditLogPage {
  items?: AuditLogRow[];
  page: number;
  pageSize: number;
  total: number;
}

export type AuditPageParams = { page: number; pageSize: number; actionType?: string };

export const auditApi = {
  /** OpenSpec: audit-history-dashboard / REQ-AUDIT-001 */
  history: (params: AuditPageParams) => apiClient.get<AuditLogPage>('/history', { params }),
  /** OpenSpec: audit-history-dashboard / REQ-AUDIT-001 */
  listAuditLogs: (params: AuditPageParams) => apiClient.get<AuditLogPage>('/audit-logs', { params }),
  /** OpenSpec: audit-history-dashboard / REQ-DASH-001 */
  dashboardOverview: (range: string) => apiClient.get<DashboardOverview>('/dashboard/overview', { params: { range } })
};
