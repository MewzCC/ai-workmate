import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  list: vi.fn(),
  detail: vi.fn(),
}));

vi.mock('@/lib/runtimeLogApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/runtimeLogApi')>();
  return { ...original, runtimeLogApi: api };
});

import RuntimeLogsPage from './RuntimeLogsPage';

const humanRecord = {
  source: 'HUMAN' as const,
  id: 11,
  referenceCode: 'req-human',
  operation: 'PASSWORD /api/auth/login',
  outcome: 'SUCCEEDED' as const,
  statusCode: 200,
  durationMs: 18,
  operatorLabel: '张三',
  traceId: 'trace-human',
  actorType: 'HUMAN' as const,
  eventType: 'LOGIN' as const,
  startedAt: '2026-09-22T09:00:00',
  completedAt: '2026-09-22T09:00:00',
};

const agentRecord = {
  source: 'AGENT' as const,
  id: 12,
  referenceCode: 'AGT-12',
  operation: 'leave.apply',
  outcome: 'SUCCEEDED' as const,
  decision: 'ALLOW',
  durationMs: 44,
  operatorLabel: '张三',
  traceId: 'trace-agent',
  actorType: 'AI_AGENT' as const,
  eventType: 'TOOL_CALL' as const,
  startedAt: '2026-09-22T09:01:00',
  completedAt: '2026-09-22T09:01:01',
};

describe('RuntimeLogsPage', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('清晰区分人为操作和 AI 自助工具调用', async () => {
    api.list.mockResolvedValue({
      records: [humanRecord, agentRecord],
      total: 2,
      page: 1,
      size: 20,
      from: '2026-09-15T00:00:00',
      to: '2026-09-22T23:59:59',
      stats: { total: 2, succeeded: 2, failed: 0, blocked: 0, averageDurationMs: 31 },
    });
    api.detail.mockResolvedValue({
      ...humanRecord,
      clientIp: '10.0.0.7',
      userAgent: 'Desktop Browser',
    });

    render(<App><RuntimeLogsPage /></App>);

    expect(await screen.findByText('平台操作日志')).toBeTruthy();
    expect(screen.getAllByText('人为操作').length).toBeGreaterThan(0);
    expect(screen.getAllByText('AI 自助调用').length).toBeGreaterThan(0);
    expect(screen.getAllByText('平台用户').length).toBeGreaterThan(0);
    expect(screen.getAllByText('AI Agent').length).toBeGreaterThan(0);
    expect(screen.getByText('用户登录')).toBeTruthy();
    expect(screen.getByText('AI 工具调用')).toBeTruthy();

    fireEvent.click(screen.getAllByRole('button', { name: '查看' })[0]);
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith('HUMAN', 11));
    expect(await screen.findByText('10.0.0.7')).toBeTruthy();
    expect(screen.getByText('Desktop Browser')).toBeTruthy();
  });
});
