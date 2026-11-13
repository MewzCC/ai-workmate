import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Dashboard from './Dashboard';

const mocks = vi.hoisted(() => ({
  getOverview: vi.fn(),
  updatePreferences: vi.fn(),
  push: vi.fn(),
}));

vi.mock('@/lib/dashboardApi', async () => {
  const actual = await vi.importActual<typeof import('@/lib/dashboardApi')>('@/lib/dashboardApi');
  return { ...actual, getDashboardOverview: mocks.getOverview, updateDashboardPreferences: mocks.updatePreferences };
});

vi.mock('@/lib/nextCompat', () => ({
  useRouter: () => ({ push: mocks.push }),
}));

vi.mock('@/hooks/usePermission', () => ({
  usePermission: () => ({ allowed: true }),
}));

vi.mock('./EChartsCard', () => ({ default: ({ title }: { title: string }) => <div>{title}</div> }));

const overview = {
  generatedAt: '2026-09-12T02:00:00Z',
  days: 7,
  availableMetricCodes: ['PENDING_TODOS', 'OVERDUE_TODOS', 'MY_APPLICATIONS', 'UNREAD_MESSAGES'],
  metrics: [
    { code: 'PENDING_TODOS', value: 1 },
    { code: 'OVERDUE_TODOS', value: 0 },
    { code: 'MY_APPLICATIONS', value: 3 },
    { code: 'UNREAD_MESSAGES', value: 2 },
  ],
  todos: [{
    taskId: 91,
    businessType: 'LEAVE_APPLICATION',
    businessId: 18,
    title: 'LV-000018',
    applicantName: '不应发送给 Agent 的申请人',
    submittedAt: '2026-09-12T01:00:00Z',
    dueAt: null,
    overdue: false,
  }],
  trends: [{ date: '2026-09-12', submitted: 1, completed: 0 }],
  businessDistribution: [{ businessType: 'LEAVE_APPLICATION', value: 1 }],
  recentActivities: [],
  healthSummary: null,
} as const;

describe('Dashboard approval entry workflow', () => {
  afterEach(cleanup);

  beforeEach(() => {
    mocks.getOverview.mockReset().mockResolvedValue(overview);
    mocks.push.mockReset();
    mocks.updatePreferences.mockReset().mockResolvedValue({
      metricCodes: ['UNREAD_MESSAGES', 'PENDING_TODOS'],
      availableMetricCodes: overview.availableMetricCodes,
    });
  });

  it('opens the existing approval detail without executing a decision on the dashboard', async () => {
    render(<Dashboard primaryColor="#1677ff" onOpenAi={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '处理' }));
    expect(mocks.push).toHaveBeenCalledWith('/oa/approval-tasks/91?from=dashboard');
  });

  it('sends only the task identifier as business data to the controlled Agent entry', async () => {
    const onOpenAi = vi.fn();
    render(<Dashboard primaryColor="#1677ff" onOpenAi={onOpenAi} />);
    fireEvent.click(await screen.findByRole('button', { name: '预审' }));
    await waitFor(() => expect(onOpenAi).toHaveBeenCalledTimes(1));
    const prompt = String(onOpenAi.mock.calls[0][0]);
    expect(prompt).toContain('91');
    expect(prompt).not.toContain('LV-000018');
    expect(prompt).not.toContain('不应发送给 Agent 的申请人');
  });

  it('persists selected metrics in the configured order and reloads the dashboard', async () => {
    render(<Dashboard primaryColor="#1677ff" onOpenAi={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: '配置指标' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '逾期待办' }));
    fireEvent.click(screen.getByRole('checkbox', { name: '本人申请' }));
    fireEvent.click(screen.getAllByRole('button', { name: '下移' })[0]);
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    await waitFor(() => expect(mocks.updatePreferences).toHaveBeenCalledWith([
      'UNREAD_MESSAGES', 'PENDING_TODOS',
    ]));
    await waitFor(() => expect(mocks.getOverview).toHaveBeenCalledTimes(2));
  });
});
