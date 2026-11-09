import type { DashboardOverview } from './dashboardApi';

/** Fixed routes only; a missing frozen detail must never fall back to another business. */
export function dashboardTodoTarget(task: DashboardOverview['todos'][number]): string | null {
  if (!Number.isSafeInteger(task.taskId) || task.taskId < 1 || task.detailAvailable !== true) return null;
  if (task.businessType === 'LEAVE_APPLICATION') return `/oa/approval-tasks/${task.taskId}?from=dashboard`;
  if (task.businessType === 'GENERIC_APPROVAL') return `/oa/todo?kind=generic&taskId=${task.taskId}`;
  return null;
}
