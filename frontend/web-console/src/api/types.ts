export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
}

export interface ReportSummary {
  reportId: number;
  title: string;
  status: string;
}

export interface ReferenceSummary {
  referenceId: string | number;
  title?: string;
  sourceTitle?: string;
  sourceType?: string;
  snippet?: string;
  snapshot?: string;
  score?: number | {
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
}

export type SseEventType = 'stage' | 'delta' | 'references' | 'error' | 'done';
export type SseStage = 'retrieval' | 'analysis' | 'writing' | 'export';

export interface UnifiedSseEvent {
  type: SseEventType;
  taskId: string;
  content: string;
  stage?: SseStage;
  references: unknown[];
  progress?: number;
  errorCode?: string | null;
  traceId?: string | null;
}
