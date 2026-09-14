import { queryString, request } from '@/lib/oaApi';

export type RuntimeLogSource = 'INTEGRATION' | 'AGENT';
export type RuntimeLogOutcome = 'RUNNING' | 'SUCCEEDED' | 'REJECTED' | 'FAILED' | 'TIMED_OUT' | 'RESULT_INVALID';

export interface RuntimeLogRecord {
  source: RuntimeLogSource;
  id: number;
  referenceCode: string;
  operation: string;
  outcome: RuntimeLogOutcome;
  decision?: string;
  statusCode?: number;
  durationMs?: number;
  operatorLabel: string;
  traceId?: string;
  errorCode?: string;
  startedAt: string;
  completedAt?: string;
}

export interface RuntimeLogDetail extends RuntimeLogRecord {
  decisionCode?: string;
  requestFingerprint?: string;
  detailPreview?: string;
  handlerInvoked?: boolean;
  resultBytes?: number;
  attempt?: number;
}

export interface RuntimeLogPage {
  records: RuntimeLogRecord[];
  total: number;
  page: number;
  size: number;
  from: string;
  to: string;
  stats: {
    total: number;
    succeeded: number;
    failed: number;
    blocked: number;
    averageDurationMs: number;
  };
}

export const runtimeLogApi = {
  list: (params: {
    source?: RuntimeLogSource;
    outcome?: RuntimeLogOutcome;
    keyword?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  }) => request<RuntimeLogPage>(`/admin/runtime-logs${queryString(params)}`),
  detail: (source: RuntimeLogSource, id: number) =>
    request<RuntimeLogDetail>(`/admin/runtime-logs/${source}/${id}`),
};
