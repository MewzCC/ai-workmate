import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PlatformObservabilityPage from './PlatformObservabilityPage';

const overview = vi.fn();
vi.mock('@/lib/platformObservabilityApi', () => ({ platformObservabilityApi: { overview: (...args: unknown[]) => overview(...args) } }));
vi.mock('@/hooks/usePermission', () => ({ usePermission: () => ({ allowed: true }) }));

const emptyOverview = {
  from: '2026-09-16T00:00:00', to: '2026-09-23T00:00:00', interval: 'day',
  stats: { total: 0, succeeded: 0, failed: 0, blocked: 0, averageDurationMs: 0 },
  p95DurationMs: 0, timeline: [], sources: [], errorCodes: [],
};

describe('PlatformObservabilityPage', () => {
  beforeEach(() => { overview.mockReset(); });
  afterEach(cleanup);

  it('shows truthful empty states and changes the bounded range', async () => {
    overview.mockResolvedValue(emptyOverview);
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await waitFor(() => expect(overview).toHaveBeenCalledWith('7d'));
    expect(await screen.findAllByText('当前时段暂无可展示的数据')).toHaveLength(4);
    fireEvent.click(screen.getByText('近 24 小时'));
    await waitFor(() => expect(overview).toHaveBeenCalledWith('24h'));
  });

  it('shows a retryable error without mock fallback', async () => {
    overview.mockRejectedValue(new Error('offline'));
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    expect(await screen.findByText('请求失败，请稍后重试')).toBeTruthy();
    expect(screen.queryByText('调用总量')).toBeNull();
  });
});
