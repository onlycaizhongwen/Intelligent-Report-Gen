import { apiClient } from './client';

export interface SharedReportSection {
  heading?: string;
  content?: string;
  citations?: unknown[];
}

export interface SharedReportDetail {
  accessGranted: boolean;
  shareToken: string;
  reportId: string;
  title: string;
  status: string;
  currentVersionId?: string | number | null;
  allowDownload: boolean;
  sections: SharedReportSection[];
  exports: SharedExportSummary[];
}

export interface SharedExportSummary {
  exportFileId: string | number;
  fileName: string;
  format?: string;
  contentType?: string;
  sizeBytes?: number;
  createdAt?: string;
}

export interface SharedExportDownload {
  exportFileId: string;
  reportId: string;
  fileName: string;
  contentType?: string;
  sizeBytes?: number;
  downloadPolicy: string;
  downloadUrl: string;
  expiresAt: string;
}

export interface ShareLinkSummary {
  shareLinkId: number | string;
  reportId: string | number;
  createdBy?: string | number;
  shareToken: string;
  shareUrl: string;
  status: string;
  expiresAt?: string | null;
  passwordRequired: boolean;
  allowDownload: boolean;
  allowedDownloadFormats?: string[];
  maxAccessCount?: number;
  allowedVisitors?: string[];
  allowedVisitorDomains?: string[];
  singleUse?: boolean;
}

export const shareApi = {
  /** OpenSpec: permission-collaboration / REQ-COLLAB-001 */
  createShareLink: (reportId: string, payload: { expiresAt?: string; password?: string; allowDownload?: boolean; allowedDownloadFormats?: string[]; maxAccessCount?: number; allowedVisitors?: string[]; allowedVisitorDomains?: string[]; singleUse?: boolean }) =>
    apiClient.post<unknown, ShareLinkSummary>(`/reports/${reportId}/share-links`, payload),

  /** OpenSpec: permission-collaboration / REQ-COLLAB-001 */
  revokeShareLink: (shareToken: string) =>
    apiClient.post<unknown, ShareLinkSummary>(`/share-links/${shareToken}/revoke`, {}),

  /** OpenSpec: permission-collaboration / REQ-COLLAB-001 */
  accessShare: (shareToken: string, payload: { password?: string }) =>
    apiClient.post<unknown, { accessGranted: boolean; shareToken: string; reportId: string }>(`/share-links/${shareToken}/access`, payload),

  /** OpenSpec: permission-collaboration / REQ-COLLAB-001 */
  getSharedReport: (shareToken: string, payload: { password?: string; visitor?: string; challengeAnswer?: string }) =>
    apiClient.post<unknown, SharedReportDetail>(`/share-links/${shareToken}/report`, payload),

  /** OpenSpec: permission-collaboration / REQ-COLLAB-001 */
  getSharedExportDownloadUrl: (shareToken: string, exportFileId: string, payload: { password?: string; visitor?: string; challengeAnswer?: string }) =>
    apiClient.post<unknown, SharedExportDownload>(`/share-links/${shareToken}/exports/${exportFileId}/download-url`, payload)
};
