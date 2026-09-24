import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import PlatformObservabilityPage from './PlatformObservabilityPage';

const overview = vi.fn();
const comparison = vi.fn();
const thresholds = vi.fn();
const updateThresholds = vi.fn();
const timeline = vi.fn();
const preferences = vi.fn();
const updatePreferences = vi.fn();
const exportChart = vi.fn();
const defaultCharts = [
  { id: 'volume', mode: 'line', content: ['HUMAN', 'AGENT', 'INTEGRATION'], size: 'normal' },
  { id: 'risk', mode: 'mixed', content: ['failed', 'blocked'], size: 'normal' },
  { id: 'source', mode: 'donut', content: ['HUMAN', 'AGENT', 'INTEGRATION'], size: 'normal' },
  { id: 'error', mode: 'bar', content: [], size: 'normal' },
];
const chartSetOption = vi.hoisted(() => vi.fn());
const chartHandlers = vi.hoisted(() => [] as Array<(hit: { dataIndex: number; seriesName: string }) => void>);
vi.mock('echarts/core', () => ({ use: vi.fn(), init: () => ({ setOption: chartSetOption,
  on: (_event: string, handler: (hit: { dataIndex: number; seriesName: string }) => void) => chartHandlers.push(handler),
  resize: vi.fn(), dispose: vi.fn() }) }));
vi.mock('@/lib/platformObservabilityApi', () => ({ platformObservabilityApi: {
  overview: (...args: unknown[]) => overview(...args),
  comparison: (...args: unknown[]) => comparison(...args),
  thresholds: (...args: unknown[]) => thresholds(...args),
  updateThresholds: (...args: unknown[]) => updateThresholds(...args),
  timeline: (...args: unknown[]) => timeline(...args),
  preferences: (...args: unknown[]) => preferences(...args),
  updatePreferences: (...args: unknown[]) => updatePreferences(...args),
  exportChart: (...args: unknown[]) => exportChart(...args),
} }));
vi.mock('@/hooks/usePermission', () => ({ usePermission: () => ({ allowed: true }) }));

const emptyOverview = {
  from: '2026-09-16T00:00:00', to: '2026-09-23T00:00:00', interval: 'day',
  stats: { total: 0, succeeded: 0, failed: 0, blocked: 0, averageDurationMs: 0 },
  p95DurationMs: 0, timeline: [], sources: [], errorCodes: [],
};

describe('PlatformObservabilityPage', () => {
  beforeEach(() => {
    overview.mockReset();
    comparison.mockReset();
    thresholds.mockReset().mockResolvedValue({ failedCount: null, blockedCount: null, p95DurationMs: null });
    updateThresholds.mockReset().mockImplementation(async (value) => value);
    timeline.mockReset();
    preferences.mockReset().mockResolvedValue({ charts: defaultCharts });
    updatePreferences.mockReset().mockImplementation(async (charts) => ({ charts }));
    exportChart.mockReset().mockResolvedValue({ filename: 'observability-volume-7d.csv',
      contentType: 'text/csv;charset=UTF-8', content: 'series,bucket,value', rowCount: 1 });
    chartSetOption.mockReset();
    chartHandlers.length = 0;
  });
  afterEach(cleanup);

  it('shows truthful empty states and changes the bounded range', async () => {
    overview.mockResolvedValue(emptyOverview);
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await waitFor(() => expect(overview).toHaveBeenCalledWith('7d'));
    expect(await screen.findAllByText('当前时段暂无可展示的数据')).toHaveLength(4);
    fireEvent.click(screen.getByText('近 24 小时'));
    await waitFor(() => expect(overview).toHaveBeenCalledWith('24h'));
  });

  it('offers governed chart export only for saved configuration', async () => {
    overview.mockResolvedValue(emptyOverview);
    const createUrl = vi.fn().mockReturnValue('blob:test');
    const revokeUrl = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createUrl });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeUrl });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('调用流量');
    fireEvent.click(screen.getByRole('button', { name: '导出调用流量数据' }));
    await waitFor(() => expect(exportChart).toHaveBeenCalledWith('7d', 'volume'));
    expect(createUrl).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeUrl).toHaveBeenCalledWith('blob:test');
    fireEvent.click(screen.getByText('面积'));
    expect((screen.getByRole('button', { name: '导出调用流量数据' }) as HTMLButtonElement).disabled).toBe(true);
    Reflect.deleteProperty(URL, 'createObjectURL');
    Reflect.deleteProperty(URL, 'revokeObjectURL');
    click.mockRestore();
  });

  it('shows a retryable error without mock fallback', async () => {
    overview.mockRejectedValue(new Error('offline'));
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    expect(await screen.findByText('请求失败，请稍后重试')).toBeTruthy();
    expect(screen.queryByText('调用总量')).toBeNull();
  });

  it('does not offer a writable fallback when chart preferences fail to load', async () => {
    overview.mockResolvedValue(emptyOverview);
    preferences.mockRejectedValue(new Error('offline'));
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    expect(await screen.findByText('图表配置加载或保存失败')).toBeTruthy();
    expect((screen.getByRole('button', { name: '保存配置' }) as HTMLButtonElement).disabled).toBe(true);
    expect(updatePreferences).not.toHaveBeenCalled();
  });

  it('switches chart type and selected content without reloading data', async () => {
    overview.mockResolvedValue({
      ...emptyOverview,
      stats: { ...emptyOverview.stats, total: 2 },
      sources: [{ code: 'HUMAN', total: 2 }],
      errorCodes: [{ code: 'TEST_ERROR', total: 1 }],
      timeline: [{ bucket: '2026-09-16T00:00:00', source: 'HUMAN', total: 2, failed: 1, blocked: 0 }],
    });
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('调用流量');
    await waitFor(() => expect(chartSetOption).toHaveBeenCalled());
    fireEvent.click(screen.getByText('面积'));
    await waitFor(() => expect(chartSetOption.mock.calls.some(([option]) => option.series?.some((item: { areaStyle?: unknown }) => item.areaStyle))).toBe(true));
    fireEvent.click(screen.getByRole('button', { name: '调用流量显示内容' }));
    fireEvent.click(await screen.findByLabelText('AI 自助调用'));
    await waitFor(() => expect(chartSetOption.mock.calls.some(([option]) => option.series?.length === 2 && option.series.some((item: { areaStyle?: unknown }) => item.areaStyle))).toBe(true));
    expect(chartSetOption.mock.lastCall?.[1]).toMatchObject({ replaceMerge: ['series'] });
    expect(overview).toHaveBeenCalledTimes(1);
  });

  it('saves chart type, content, order and width through the authenticated settings API', async () => {
    overview.mockResolvedValue(emptyOverview);
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('调用流量');
    fireEvent.click(screen.getByText('面积'));
    const layoutButton = screen.getByRole('button', { name: '调整布局' });
    expect(layoutButton.hasAttribute('disabled')).toBe(false);
    fireEvent.click(layoutButton);
    expect(screen.queryByRole('dialog')).toBeTruthy();
    fireEvent.click(screen.getAllByRole('button', { name: /下\s*移/ })[0]);
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '调用流量图表宽度' }));
    fireEvent.click((await screen.findAllByText('通栏'))[0]);
    fireEvent.click(screen.getByRole('button', { name: /确\s*认/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存配置' }));
    await waitFor(() => expect(updatePreferences).toHaveBeenCalled());
    const submitted = updatePreferences.mock.calls[0][0];
    expect(submitted[0].id).toBe('risk');
    expect(submitted[1]).toMatchObject({ id: 'volume', mode: 'area', content: ['HUMAN', 'AGENT', 'INTEGRATION'], size: 'wide' });
    cleanup();
    preferences.mockResolvedValue({ charts: submitted });
    const restored = render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('异常脉冲');
    await waitFor(() => expect(restored.container.querySelector('.oa-observability-slot')?.textContent).toContain('异常脉冲'));
    expect(restored.container.querySelectorAll('.oa-observability-slot')[1].className).toContain('oa-observability-slot-wide');
  }, 20_000);

  it('adds, duplicates, renames and removes controlled chart cards before saving', async () => {
    overview.mockResolvedValue(emptyOverview);
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('调用流量');
    fireEvent.click(screen.getByRole('button', { name: '调整布局' }));
    fireEvent.click(screen.getByRole('button', { name: '添加图表' }));
    expect(screen.getByText('5 / 12 张图表')).toBeTruthy();
    fireEvent.change(screen.getAllByRole('textbox', { name: '调用流量自定义标题' })[1],
      { target: { value: '重点流量' } });
    fireEvent.click(screen.getAllByRole('button', { name: /复\s*制/ })[4]);
    expect(screen.getByText('6 / 12 张图表')).toBeTruthy();
    fireEvent.click(screen.getAllByRole('button', { name: /删\s*除/ })[5]);
    fireEvent.click(screen.getByRole('button', { name: /确\s*认/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存配置' }));
    await waitFor(() => expect(updatePreferences).toHaveBeenCalled());
    expect(updatePreferences.mock.calls[0][0]).toHaveLength(5);
    expect(updatePreferences.mock.calls[0][0][4]).toMatchObject({
      id: 'volume-1', kind: 'volume', title: '重点流量', mode: 'line',
    });
  }, 20_000);

  it('loads an alternate hourly timeline for a custom time granularity', async () => {
    overview.mockResolvedValue({ ...emptyOverview, stats: { ...emptyOverview.stats, total: 1 } });
    timeline.mockResolvedValue({ from: emptyOverview.from, to: emptyOverview.to, interval: 'hour', timeline: [] });
    preferences.mockResolvedValue({ charts: [{ ...defaultCharts[0], kind: 'volume', granularity: 'hour' }] });
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await waitFor(() => expect(timeline).toHaveBeenCalledWith(emptyOverview.from, emptyOverview.to, 'hour'));
    expect(await screen.findByRole('img', { name: '调用流量' })).toBeTruthy();
  });

  it('drills into matching tenant logs for a clicked time bucket and source', async () => {
    overview.mockResolvedValue({ ...emptyOverview, stats: { ...emptyOverview.stats, total: 1 },
      timeline: [{ bucket: emptyOverview.from, source: 'HUMAN', total: 1, failed: 0, blocked: 0 }],
    });
    function LogLocation() { return <output>{useLocation().search}</output>; }
    render(<MemoryRouter initialEntries={['/oa/platform-observability']}><Routes>
      <Route path="/oa/platform-observability" element={<PlatformObservabilityPage />} />
      <Route path="/oa/runtime-logs" element={<LogLocation />} />
    </Routes></MemoryRouter>);
    await screen.findByRole('img', { name: '调用流量' });
    await waitFor(() => expect(chartHandlers.length).toBeGreaterThan(0));
    fireEvent.click(screen.getByRole('img', { name: '调用流量' }));
    chartHandlers[0]({ dataIndex: 0, seriesName: '人为操作' });
    const query = new URLSearchParams((await screen.findByRole('status')).textContent ?? '');
    expect(query.get('source')).toBe('HUMAN');
    expect(query.get('from')).toBe(emptyOverview.from);
    expect(query.get('to')).toBe('2026-09-17T00:00:00');
    expect(query.get('toExclusive')).toBe('true');
  });

  it('uses the exact error code instead of a fuzzy keyword when drilling into an error chart', async () => {
    overview.mockResolvedValue({ ...emptyOverview, stats: { ...emptyOverview.stats, total: 1 },
      sources: [{ code: 'AGENT', total: 1 }], errorCodes: [{ code: 'TIMEOUT', total: 1 }],
      timeline: [{ bucket: emptyOverview.from, source: 'AGENT', total: 1, failed: 1, blocked: 0 }],
    });
    function LogLocation() { return <output>{useLocation().search}</output>; }
    render(<MemoryRouter initialEntries={['/oa/platform-observability']}><Routes>
      <Route path="/oa/platform-observability" element={<PlatformObservabilityPage />} />
      <Route path="/oa/runtime-logs" element={<LogLocation />} />
    </Routes></MemoryRouter>);
    await screen.findByRole('img', { name: '高频错误码' });
    await waitFor(() => expect(chartHandlers).toHaveLength(4));
    chartHandlers[3]({ dataIndex: 0, seriesName: '高频错误码' });
    const query = new URLSearchParams((await screen.findByRole('status')).textContent ?? '');
    expect(query.get('errorCode')).toBe('TIMEOUT');
    expect(query.get('from')).toBe(emptyOverview.from);
    expect(query.get('to')).toBe(emptyOverview.to);
    expect(query.has('keyword')).toBe(false);
  });

  it('compares equal adjacent periods using server metrics without inventing a zero-baseline rate', async () => {
    overview.mockResolvedValue(emptyOverview);
    comparison.mockResolvedValue({ range: '7d',
      current: { from: '2026-09-16T00:00:00', to: '2026-09-23T00:00:00', toExclusive: false,
        stats: { total: 8, failed: 2, averageDurationMs: 80 } },
      previous: { from: '2026-09-09T00:00:00', to: '2026-09-16T00:00:00', toExclusive: true,
        stats: { total: 0, failed: 0, averageDurationMs: 0 } },
    });
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('调用流量');
    fireEvent.click(screen.getByRole('switch', { name: '时段对比' }));
    await waitFor(() => expect(comparison).toHaveBeenCalledWith('7d'));
    expect(await screen.findByText('与上一等长时段对比')).toBeTruthy();
    expect(screen.getAllByText('无可比数据')).toHaveLength(2);
    expect(screen.getByText('本期: 8')).toBeTruthy();
    expect(screen.getByText('上期: 0')).toBeTruthy();
  });

  it('highlights only measured values that cross a persisted visual threshold', async () => {
    overview.mockResolvedValue({ ...emptyOverview,
      stats: { ...emptyOverview.stats, total: 3, failed: 2, blocked: 0 }, p95DurationMs: 900 });
    thresholds.mockResolvedValue({ failedCount: 2, blockedCount: 1, p95DurationMs: 1000 });
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await waitFor(() => expect(thresholds).toHaveBeenCalled());
    expect(await screen.findByText('达到阈值')).toBeTruthy();
    expect(screen.getAllByText('达到阈值')).toHaveLength(1);
  });

  it('saves bounded visual thresholds separately from chart configuration', async () => {
    overview.mockResolvedValue(emptyOverview);
    render(<MemoryRouter><PlatformObservabilityPage /></MemoryRouter>);
    await screen.findByText('调用流量');
    fireEvent.click(screen.getByRole('button', { name: '视觉阈值' }));
    const inputs = screen.getAllByRole('spinbutton');
    fireEvent.change(inputs[0], { target: { value: '5' } });
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(updateThresholds).toHaveBeenCalledWith({
      failedCount: 5, blockedCount: null, p95DurationMs: null,
    }));
    expect(updatePreferences).not.toHaveBeenCalled();
  });
});
