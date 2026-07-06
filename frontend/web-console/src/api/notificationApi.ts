import { apiClient } from './client';

export interface SystemAlert {
  alertId: number | string;
  recipientUserId: number | string;
  type: string;
  severity: string;
  status: string;
  resourceType: string;
  resourceId?: number | string;
  payload: Record<string, unknown>;
  createdAt: string;
}

export const notificationApi = {
  /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 */
  listSystemAlerts: (params: { page: number; pageSize: number; status?: string }) =>
    apiClient.get('/system-alerts', { params })
};
