import type { DashboardOverview } from './dashboardApi';
import { approvalTaskTarget } from './approvalTaskTarget';

/** Fixed routes only; a missing frozen detail must never fall back to another business. */
export function dashboardTodoTarget(task: DashboardOverview['todos'][number]): string | null {
  return approvalTaskTarget(task, 'dashboard');
}
