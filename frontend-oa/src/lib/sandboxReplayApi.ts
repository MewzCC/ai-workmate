import { queryString, request } from '@/lib/oaApi';

export type SandboxReplayStatus = 'RUNNING' | 'SUCCESS' | 'FAILED';
export type SandboxReplayComparison = 'MATCHED' | 'CHANGED';

export interface SandboxReplayBaseline {
  invocationId: number;
  endpointId: number;
  endpointCode: string;
  endpointName: string;
  method: string;
  relativePath: string;
  outcome: 'SUCCESS' | 'FAILED';
  httpStatus?: number;
  durationMs: number;
  operatorLabel: string;
  createdAt: string;
}

export interface SandboxReplayRecord {
  id: number;
  sourceInvocationId: number;
  endpointCode: string;
  endpointName: string;
  method: string;
  relativePath: string;
  baselineOutcome: 'SUCCESS' | 'FAILED';
  baselineHttpStatus?: number;
  status: SandboxReplayStatus;
  replayHttpStatus?: number;
  replayDurationMs?: number;
  comparisonResult?: SandboxReplayComparison;
  requestedByLabel: string;
  startedAt: string;
  completedAt?: string;
}

export interface SandboxReplayDetail extends SandboxReplayRecord {
  baselineRequestFingerprint: string;
  baselineResponsePreview?: string;
  replayResponsePreview?: string;
  replayErrorCode?: string;
  traceId: string;
  reason: string;
}

export interface SandboxReplayPage {
  records: SandboxReplayRecord[];
  total: number;
  page: number;
  size: number;
  stats: {
    total: number;
    matched: number;
    changed: number;
    successful: number;
    failed: number;
  };
  canExecute: boolean;
}

export const sandboxReplayApi = {
  list: (params: { keyword?: string; status?: SandboxReplayStatus; page?: number; size?: number }) =>
    request<SandboxReplayPage>(`/integration/replays${queryString(params)}`),
  baselines: (keyword?: string, limit = 50) =>
    request<SandboxReplayBaseline[]>(`/integration/replays/baselines${queryString({ keyword, limit })}`),
  detail: (id: number) => request<SandboxReplayDetail>(`/integration/replays/${id}`),
  execute: (payload: { sourceInvocationId: number; reason: string; idempotencyKey: string }) =>
    request<SandboxReplayDetail>('/integration/replays', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};
