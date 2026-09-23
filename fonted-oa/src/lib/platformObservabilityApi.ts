import { queryString, request } from '@/lib/oaApi';

export type ObservabilityRange = '24h' | '7d' | '30d';

export interface ObservabilityCategory {
  code: string;
  total: number;
}

export interface ObservabilityTimelinePoint {
  bucket: string;
  source: 'HUMAN' | 'AGENT' | 'INTEGRATION';
  total: number;
  failed: number;
  blocked: number;
}

export interface PlatformObservabilityOverview {
  from: string;
  to: string;
  interval: 'hour' | 'day';
  stats: { total: number; succeeded: number; failed: number; blocked: number; averageDurationMs: number };
  p95DurationMs: number | null;
  timeline: ObservabilityTimelinePoint[];
  sources: ObservabilityCategory[];
  errorCodes: ObservabilityCategory[];
}

export const platformObservabilityApi = {
  overview: (range: ObservabilityRange) =>
    request<PlatformObservabilityOverview>(`/admin/platform-observability/overview${queryString({ range })}`),
};
