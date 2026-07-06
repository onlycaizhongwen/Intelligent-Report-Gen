import { apiClient } from './client';
import type { PageResponse, ReferenceSummary, ReportSummary } from './types';

export interface EnterpriseBrandLayoutTemplate {
  coverTitle?: string;
  tocTitle?: string;
  bodyTitlePrefix?: string;
  titleFontSize?: number;
  bodyFontSize?: number;
  headerFontSize?: number;
  footerFontSize?: number;
  pageWidth?: number;
  closingTitle?: string;
  closingMessage?: string;
  closingContact?: string;
}

export interface EnterpriseBrandTemplate {
  companyName: string;
  logoObjectKey: string;
  header: string;
  footer: string;
  fontFamily: string;
  primaryColor: string;
  layout?: EnterpriseBrandLayoutTemplate;
}

export interface ReportExportRequest {
  format: string;
  templateId: string;
  brand?: EnterpriseBrandTemplate;
}

export interface ReportExportDownload {
  downloadUrl: string;
  expiresAt?: string;
}

export interface EnterpriseBrandSnapshot extends EnterpriseBrandTemplate {
  templateId: string;
  templateVersion?: string;
  format: string;
  layout: EnterpriseBrandLayoutTemplate;
}

export interface EnterpriseExportTemplateRequest {
  templateId?: string;
  name: string;
  status?: string;
  brand: EnterpriseBrandTemplate;
}

export interface EnterpriseExportTemplate {
  id?: string | number | null;
  templateId: string;
  name: string;
  version: string;
  status: string;
  brandSnapshot: EnterpriseBrandSnapshot;
  createdBy?: string | number | null;
  createdAt?: string | null;
  updatedAt?: string | null;
}

export interface ReportExportStatus {
  exportFileId: string;
  status: string;
  downloadUrl?: string;
  expiresAt?: string;
  brandSnapshot?: EnterpriseBrandSnapshot;
}

export interface ReportVersion {
  versionId: string | number;
  versionNo: number;
  changeReason: string;
  current: boolean;
  createdAt?: string;
}

export interface ReportVersionDiff {
  reportId: string | number;
  baseVersionId: string | number;
  targetVersionId: string | number;
  summary: {
    added: number;
    removed: number;
    modified: number;
    unchanged: number;
  };
  changes: Array<{
    changeType: 'added' | 'removed' | 'modified';
    heading: string;
    baseContent?: string | null;
    targetContent?: string | null;
  }>;
}

export interface ReportTemplateField {
  fieldKey: string;
  label: string;
  type: 'text' | 'textarea' | 'select' | 'number' | string;
  required: boolean;
  options?: string[];
  defaultValue?: string | null;
  helpText?: string | null;
}

export interface ReportTemplate {
  templateId: string;
  name: string;
  category: string;
  version: string;
  status: string;
  fields: ReportTemplateField[];
  outlineSchema?: Record<string, unknown>;
}

export interface TemplateReportTaskRequest {
  templateId: string;
  payload: Record<string, string | number | boolean | null>;
}

export interface TemplateReportTaskResponse {
  taskId: string;
  reportId?: string | number;
  status: string;
  currentStage?: string;
  progress?: number;
  templateSnapshot?: {
    templateId?: string;
    name?: string;
    version?: string;
    parameters?: Record<string, string | number | boolean | null>;
    [key: string]: unknown;
  };
}

export interface CitationMark {
  referenceId: string | number;
  sourceTitle?: string;
  sourceType?: string;
  snapshot?: string;
  score?: {
    credibility?: number;
    citationQuality?: number;
    [key: string]: unknown;
  };
  anchor?: {
    sectionNo?: number;
    heading?: string;
    text?: string;
    [key: string]: unknown;
  };
  [key: string]: unknown;
}

export interface ReportDetail {
  reportId: string | number;
  title: string;
  status: string;
  currentVersionId?: string | number;
  sections: Array<{
    sectionId?: string | number;
    heading: string;
    content: string;
    citations?: CitationMark[];
  }>;
}

export const reportApi = {
  /** OpenSpec: report-generation / REQ-REPORT-001 */
  createTask: (payload: { topic: string; focus?: string }) =>
    apiClient.post<unknown, { taskId: string; status: string }>('/reports/generation-tasks', payload),

  /** OpenSpec: report-generation / REQ-REPORT-001 */
  listTemplates: () =>
    apiClient.get<unknown, ReportTemplate[]>('/report-templates'),

  /** OpenSpec: report-generation / REQ-REPORT-001 */
  createTemplateTask: (payload: TemplateReportTaskRequest) =>
    apiClient.post<unknown, TemplateReportTaskResponse>(
      '/reports/template-generation-tasks',
      payload
    ),

  /** OpenSpec: report-generation / REQ-REPORT-002 */
  confirmOutline: (taskId: string, outline: unknown) =>
    apiClient.put<unknown, { taskId: string; confirmed: boolean; nextStage: string }>(`/reports/generation-tasks/${taskId}/outline`, { outline, confirmed: true }),

  /** OpenSpec: report-generation / REQ-REPORT-001 */
  list: (params: { page: number; pageSize: number }) =>
    apiClient.get<unknown, PageResponse<ReportSummary>>('/reports', { params }),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-003 */
  getDetail: (reportId: string) =>
    apiClient.get<unknown, ReportDetail>(`/reports/${reportId}`),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-003 */
  getReference: (reportId: string, referenceId: string) =>
    apiClient.get<unknown, ReferenceSummary>(`/reports/${reportId}/references/${referenceId}`),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  createExport: (reportId: string, payload: ReportExportRequest) =>
    apiClient.post<unknown, { exportFileId: string; status: string; fileName?: string; contentType?: string; downloadUrl?: string; brandSnapshot?: EnterpriseBrandSnapshot }>(`/reports/${reportId}/exports`, payload),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  getExportStatus: (reportId: string, exportFileId: string) =>
    apiClient.get<unknown, ReportExportStatus>(
      `/reports/${reportId}/exports/${exportFileId}`
    ),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  getExportDownloadUrl: (exportFileId: string) =>
    apiClient.get<unknown, ReportExportDownload>(`/files/report-exports/${exportFileId}/download-url`),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  createEnterpriseExportTemplate: (payload: EnterpriseExportTemplateRequest) =>
    apiClient.post<unknown, EnterpriseExportTemplate>('/enterprise-export-templates', payload),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  listEnterpriseExportTemplates: (params: { page: number; pageSize: number; status?: string }) =>
    apiClient.get<unknown, PageResponse<EnterpriseExportTemplate>>('/enterprise-export-templates', { params }),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  updateEnterpriseExportTemplate: (templateId: string, payload: EnterpriseExportTemplateRequest) =>
    apiClient.put<unknown, EnterpriseExportTemplate>(`/enterprise-export-templates/${templateId}`, payload),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  listEnterpriseExportTemplateVersions: (templateId: string) =>
    apiClient.get<unknown, EnterpriseExportTemplate[]>(`/enterprise-export-templates/${templateId}/versions`),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  disableEnterpriseExportTemplate: (templateId: string) =>
    apiClient.post<unknown, EnterpriseExportTemplate>(`/enterprise-export-templates/${templateId}/disable`, {}),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-004 */
  enableEnterpriseExportTemplate: (templateId: string) =>
    apiClient.post<unknown, EnterpriseExportTemplate>(`/enterprise-export-templates/${templateId}/enable`, {}),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-005 */
  listVersions: (reportId: string) =>
    apiClient.get<unknown, ReportVersion[]>(`/reports/${reportId}/versions`),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-005 */
  compareVersions: (reportId: string, params: { baseVersionId: string | number; targetVersionId: string | number }) =>
    apiClient.get<unknown, ReportVersionDiff>(`/reports/${reportId}/versions/diff`, { params }),

  /** OpenSpec: report-citation-export-version / REQ-REPORT-005 */
  rollbackVersion: (reportId: string, versionId: string | number) =>
    apiClient.post<unknown, { reportId: string | number; sourceVersionId: string | number; newVersionId: string | number; currentVersionId: string | number; changeReason: string }>(
      `/reports/${reportId}/versions/${versionId}/rollback`,
      {}
    )
};
