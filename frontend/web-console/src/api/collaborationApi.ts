import { apiClient } from './client';

export interface CollaborationCreateResult {
  annotationId: string | number;
  taskId: string | number;
  notificationId?: string | number;
  status: string;
}

export const collaborationApi = {
  /** OpenSpec: permission-collaboration / REQ-COLLAB-002 */
  createComment: (reportId: string, payload: {
    content: string;
    assigneeUserId?: string | number;
    anchor: {
      sectionId?: string;
      startOffset: number;
      endOffset: number;
      selectedText: string;
    };
  }) =>
    apiClient.post<unknown, CollaborationCreateResult>(`/reports/${reportId}/annotations`, payload),

  /** OpenSpec: permission-collaboration / REQ-COLLAB-002 */
  updateTaskStatus: (taskId: string, payload: { status: string }) => apiClient.put(`/tasks/${taskId}/status`, payload)
};
