import { apiClient } from './client';

export interface UploadedDocumentResult {
  documentId?: number | string;
  filename?: string;
  parseStatus?: string;
  parseFailureReason?: string;
  status?: string;
}

export interface KnowledgeBaseSummary {
  knowledgeBaseId: number | string;
  name: string;
  ownerUserId?: number | string;
  status?: string;
}

export interface DataSourceSyncRun {
  syncRunId: number | string;
  dataSourceId: number | string;
  mode: string;
  status: string;
  processedRows: number;
  failureReason?: string;
  message?: string;
  startedAt?: string;
  finishedAt?: string;
}

export interface DataSourceFieldMapping {
  rowsPath?: string;
  titleField?: string;
  contentField?: string;
  cursorField?: string;
  method?: 'GET' | 'POST';
  authType?: 'bearer' | 'api_key' | 'basic' | 'none';
  apiKeyHeader?: string;
  headers?: Record<string, string>;
  bodyTemplate?: string;
  pageParam?: string;
  pageStart?: number;
  pageSizeParam?: string;
  pageSize?: number;
  maxPages?: number;
}

export interface SaveDataSourceRequest {
  name: string;
  sourceType: string;
  endpoint: string;
  username?: string;
  password?: string;
  knowledgeBaseId?: number | string;
  syncQuery?: string;
  fieldMapping?: DataSourceFieldMapping;
  cursorColumn?: string;
  scheduleEnabled?: boolean;
  scheduleIntervalSeconds?: number;
  maxRetryCount?: number;
}

export interface DataSourcePreset {
  presetId: string;
  displayName: string;
  category: string;
  sourceType: string;
  endpoint: string;
  username?: string;
  syncQuery?: string;
  fieldMapping?: DataSourceFieldMapping | null;
  cursorColumn?: string;
  scheduleEnabled?: boolean;
  scheduleIntervalSeconds?: number;
  maxRetryCount?: number;
}

export interface KnowledgeItemDeleteResult {
  itemId: number | string;
  deleted: boolean;
  requiresConfirmation: boolean;
  referenceCount?: number;
}

export interface BatchImportKnowledgeItemsRequest {
  knowledgeBaseId: number | string;
  items: Array<{
    title: string;
    content: string;
    sourceType?: string;
  }>;
}

export interface BatchImportKnowledgeItemsResult {
  knowledgeBaseId: number | string;
  total: number;
  imported: number;
  failed: number;
  items: Array<{
    title: string;
    status: 'imported' | 'failed';
    reason?: string;
    itemId?: number | string;
  }>;
}

export const knowledgeApi = {
  /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 */
  listKnowledgeBases: (params: { page: number; pageSize: number }) =>
    apiClient.get<unknown, { items: KnowledgeBaseSummary[]; total: number; page: number; pageSize: number }>('/knowledge-bases', { params }),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 */
  listItems: (params: { page: number; pageSize: number; keyword?: string }) =>
    apiClient.get('/knowledge-items', { params }),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 */
  deleteItem: (itemId: string | number, confirmed = false) =>
    apiClient.delete<unknown, KnowledgeItemDeleteResult>(`/knowledge-items/${itemId}`, { params: { confirmed } }),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-001 */
  batchImportItems: (payload: BatchImportKnowledgeItemsRequest) =>
    apiClient.post<BatchImportKnowledgeItemsRequest, BatchImportKnowledgeItemsResult>('/knowledge-items/batch-import', payload),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-002 */
  uploadDocument: (formData: FormData, knowledgeBaseId: string | number) =>
    apiClient.post<FormData, UploadedDocumentResult>('/documents/upload', formData, {
      timeout: 120_000,
      params: { knowledgeBaseId },
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-002 */
  getDocumentStatus: (documentId: string | number) =>
    apiClient.get<unknown, UploadedDocumentResult>(`/documents/${documentId}`),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 */
  listDataSourcePresets: () => apiClient.get<unknown, DataSourcePreset[]>('/data-sources/presets'),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 */
  saveDataSource: (payload: SaveDataSourceRequest) => apiClient.post('/data-sources', payload),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 */
  testConnection: (payload: unknown) => apiClient.post('/data-sources/test-connection', payload),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 */
  startDataSourceSync: (dataSourceId: string, payload: unknown) =>
    apiClient.post(`/data-sources/${dataSourceId}/sync-runs`, payload),

  /** OpenSpec: knowledge-base-ingestion / REQ-KB-003 */
  listDataSourceSyncRuns: (dataSourceId: string, params: { page: number; pageSize: number }) =>
    apiClient.get(`/data-sources/${dataSourceId}/sync-runs`, { params })
};
