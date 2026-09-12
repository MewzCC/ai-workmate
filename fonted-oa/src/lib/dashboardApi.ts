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

export function getDashboardOverview(days = 7): Promise<DashboardOverview> {
  return request(`/dashboard/overview?days=${days}`);
}
