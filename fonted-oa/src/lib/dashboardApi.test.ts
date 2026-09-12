import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getDashboardOverview } from '@/lib/dashboardApi';

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
});
