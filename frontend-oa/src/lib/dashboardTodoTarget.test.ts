import { describe, expect, it } from 'vitest';
import type { DashboardOverview } from './dashboardApi';
import { dashboardTodoTarget } from './dashboardTodoTarget';

const task: DashboardOverview['todos'][number] = { taskId: 42, businessId: 7, businessType: 'GENERIC_APPROVAL',
  title: 'Synthetic', applicantName: 'Synthetic', submittedAt: '2026-11-09T09:00:00', overdue: false, detailAvailable: true };

describe('dashboard typed todo navigation', () => {
  it('routes generic tasks by task ID, never by application ID', () => {
    expect(dashboardTodoTarget(task)).toBe('/oa/todo?kind=generic&taskId=42');
  });
  it('keeps the existing leave detail route', () => {
    expect(dashboardTodoTarget({ ...task, businessType: 'LEAVE_APPLICATION' })).toBe('/oa/approval-tasks/42?from=dashboard');
  });
  it.each([0, -1, 1.5, NaN, Number.MAX_SAFE_INTEGER + 1])('refuses invalid task ID %s', taskId => {
    expect(dashboardTodoTarget({ ...task, taskId })).toBeNull();
  });
  it('refuses unavailable details and unknown business namespaces', () => {
    expect(dashboardTodoTarget({ ...task, detailAvailable: false })).toBeNull();
    expect(dashboardTodoTarget({ ...task, businessType: 'UNKNOWN' })).toBeNull();
    expect(dashboardTodoTarget({ ...task, businessType: 'https://untrusted.invalid' })).toBeNull();
  });
});
