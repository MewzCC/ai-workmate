import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import AiUsagePanel from './AiUsagePanel';
const request = vi.hoisted(() => vi.fn());
vi.mock('@/lib/oaApi', () => ({ request }));
vi.mock('./EChartsCard', () => ({ default: ({ title, option }: { title: string; option: { series: { name: string }[] } }) => <div>{title}<span>{option.series.map(item => item.name).join(',')}</span></div> }));
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string, values?: { channel?: string; model?: string }) => key === 'aiConfiguration.usage.series' ? `${values?.channel} · ${values?.model}` : key }) }));
afterEach(cleanup);
it('shows a real empty state when there is no call history', async () => {
 request.mockResolvedValue([]); render(<AiUsagePanel />);
 expect(await screen.findByText('aiConfiguration.usage.empty')).toBeInTheDocument();
});
it('does not replace missing token usage or billing with zero', async () => {
 request.mockResolvedValue([{ day: '2026-11-16', channelId: 2, model: 'example', calls: 2, successes: 1, cancelled: 0, durationMs: 2000, reportedCalls: 0, inputTokens: null, outputTokens: null, totalTokens: null }]);
 render(<AiUsagePanel />);
 await screen.findByText('aiConfiguration.usage.tokenTrend');
 expect(screen.getAllByText('aiConfiguration.usage.unavailable')).toHaveLength(2);
});
it('shows request errors without fabricated chart data', async () => {
 request.mockRejectedValue(new Error('unavailable')); render(<AiUsagePanel />);
 expect(await screen.findByText('aiConfiguration.loadFailed')).toBeInTheDocument();
 expect(screen.queryByText('aiConfiguration.usage.tokenTrend')).not.toBeInTheDocument();
});
it('uses channel names in both filters and chart legends', async () => {
 request.mockResolvedValue([{ day: '2026-11-16', channelId: 1, channelName: 'DeepSeek', model: 'flash', calls: 1, successes: 1, cancelled: 0, durationMs: 1000, reportedCalls: 1, inputTokens: 10, outputTokens: 2, totalTokens: 12 }]);
 render(<AiUsagePanel />);
 await screen.findByText('aiConfiguration.usage.tokenTrend');
 expect(screen.getAllByText('DeepSeek · flash')).toHaveLength(2);
 fireEvent.mouseDown(screen.getByRole('combobox', { name: 'aiConfiguration.channel' }));
 expect(await screen.findByText('DeepSeek')).toBeInTheDocument();
});
