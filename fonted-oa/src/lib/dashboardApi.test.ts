import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defaultDashboardExportRange, exportDashboard, getDashboardOverview } from '@/lib/dashboardApi';

const requestMock = vi.fn();
vi.mock('@/lib/oaApi', () => ({
  request: (...args: unknown[]) => requestMock(...args),
}));

describe('dashboardApi', () => {
  beforeEach(() => requestMock.mockReset());

  it('loads the authenticated dashboard overview with a bounded day window', async () => {
    const overview = { generatedAt: '2026-09-12T10:00:00Z', days: 7, metrics: [] };
    requestMock.mockResolvedValue(overview);

    await expect(getDashboardOverview(7)).resolves.toBe(overview);
    expect(requestMock).toHaveBeenCalledWith('/dashboard/overview?days=7');
  });

  it('uses seven days by default', async () => {
    requestMock.mockResolvedValue({});
    await getDashboardOverview();
    expect(requestMock).toHaveBeenCalledWith('/dashboard/overview?days=7');
  });

  it('posts the controlled export filter without a filesystem path', async () => {
    requestMock.mockResolvedValue({ filename: 'dashboard.csv', content: 'taskId', rowCount: 0 });
    await exportDashboard({ from: '2026-09-06', to: '2026-09-12', keyword: 'risk' });
    expect(requestMock).toHaveBeenCalledWith('/dashboard/export', {
      method: 'POST',
      body: JSON.stringify({ from: '2026-09-06', to: '2026-09-12', keyword: 'risk' }),
    });
  });

  it('builds an inclusive local-date range', () => {
    const range = defaultDashboardExportRange(7);
    expect(range.from).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(range.to).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });
});
