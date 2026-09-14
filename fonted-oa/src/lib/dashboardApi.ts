import { request } from '@/lib/oaApi';

export type DashboardMetricCode = 'PENDING_TODOS' | 'OVERDUE_TODOS' | 'MY_APPLICATIONS' | 'UNREAD_MESSAGES';

export interface DashboardOverview {
  generatedAt: string;
  days: number;
  availableMetricCodes: DashboardMetricCode[];
  metrics: Array<{ code: DashboardMetricCode; value: number }>;
  todos: Array<{ taskId: number; businessType: string; businessId: number; title: string; applicantName: string; submittedAt: string; dueAt?: string | null; overdue: boolean }>;
  trends: Array<{ date: string; submitted: number; completed: number }>;
  businessDistribution: Array<{ businessType: string; value: number }>;
  recentActivities: Array<{ id: number; resourceType: string; resourceId: string; action: string; result: string; summary?: string | null; createdAt: string }>;
  healthSummary?: { status: string; checkedAt: string } | null;
}

export interface DashboardExportRequest {
  from: string;
  to: string;
  keyword?: string;
}

export interface DashboardExportResponse {
  filename: string;
  contentType: string;
  content: string;
  rowCount: number;
  generatedAt: string;
}

export interface DashboardPreferences {
  metricCodes: DashboardMetricCode[];
  availableMetricCodes: DashboardMetricCode[];
}

export function getDashboardOverview(days = 7): Promise<DashboardOverview> {
  return request(`/dashboard/overview?days=${days}`);
}

export function exportDashboard(payload: DashboardExportRequest): Promise<DashboardExportResponse> {
  return request('/dashboard/export', { method: 'POST', body: JSON.stringify(payload) });
}

export function getDashboardPreferences(): Promise<DashboardPreferences> {
  return request('/settings/dashboard');
}

export function updateDashboardPreferences(metricCodes: DashboardMetricCode[]): Promise<DashboardPreferences> {
  return request('/settings/dashboard', { method: 'PUT', body: JSON.stringify({ metricCodes }) });
}

export function defaultDashboardExportRange(days = 7): DashboardExportRequest {
  const to = new Date();
  const from = new Date(to);
  from.setDate(from.getDate() - Math.max(1, days) + 1);
  return { from: localDate(from), to: localDate(to) };
}

export function downloadDashboardExport(response: DashboardExportResponse): void {
  const url = URL.createObjectURL(new Blob([response.content], { type: response.contentType }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = response.filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function localDate(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
