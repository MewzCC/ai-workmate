import { queryString, request } from '@/lib/oaApi';
import type { DashboardExportResponse } from '@/lib/dashboardApi';

export type ObservabilityRange = '24h' | '7d' | '30d';
export type ObservabilityChartKind = 'volume' | 'risk' | 'source' | 'error';
export type ObservabilityChartSize = 'normal' | 'wide';
export interface ObservabilityChartPreference {
  id: string;
  kind?: ObservabilityChartKind;
  title?: string;
  mode: string;
  content: string[];
  size: ObservabilityChartSize;
  granularity?: 'auto' | 'hour' | 'day';
}
export interface ObservabilityPreferences { charts: ObservabilityChartPreference[] }

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

export interface PlatformObservabilityTimeline {
  from: string;
  to: string;
  interval: 'hour' | 'day';
  timeline: ObservabilityTimelinePoint[];
}

export interface PlatformObservabilityComparison {
  range: ObservabilityRange;
  current: { from: string; to: string; toExclusive: boolean; stats: PlatformObservabilityOverview['stats'] };
  previous: { from: string; to: string; toExclusive: boolean; stats: PlatformObservabilityOverview['stats'] };
}

export interface ObservabilityThresholds {
  failedCount: number | null;
  blockedCount: number | null;
  p95DurationMs: number | null;
}

export const platformObservabilityApi = {
  overview: (range: ObservabilityRange) =>
    request<PlatformObservabilityOverview>(`/admin/platform-observability/overview${queryString({ range })}`),
  timeline: (from: string, to: string, interval: 'hour' | 'day') =>
    request<PlatformObservabilityTimeline>(`/admin/platform-observability/timeline${queryString({ from, to, interval })}`),
  comparison: (range: ObservabilityRange) =>
    request<PlatformObservabilityComparison>(`/admin/platform-observability/comparison${queryString({ range })}`),
  thresholds: () => request<ObservabilityThresholds>('/admin/platform-observability/thresholds'),
  updateThresholds: (thresholds: ObservabilityThresholds) =>
    request<ObservabilityThresholds>('/admin/platform-observability/thresholds', {
      method: 'PUT', body: JSON.stringify(thresholds),
    }),
  preferences: () => request<ObservabilityPreferences>('/admin/platform-observability/preferences'),
  updatePreferences: (charts: ObservabilityChartPreference[]) =>
    request<ObservabilityPreferences>('/admin/platform-observability/preferences', {
      method: 'PUT', body: JSON.stringify({ charts }),
    }),
  exportChart: (range: ObservabilityRange, chartId: string) =>
    request<DashboardExportResponse>('/admin/platform-observability/export', {
      method: 'POST', body: JSON.stringify({ range, chartId }),
    }),
};
