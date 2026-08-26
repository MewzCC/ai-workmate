import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  list: vi.fn(),
  detail: vi.fn(),
  cancel: vi.fn(),
}));

vi.mock('@/lib/oaApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/oaApi')>();
  return { ...original, agentTaskApi: api };
});

import AiTaskCenterPage from './AiTaskCenterPage';

const summary = {
  taskId: 'agt_4YpV7zqR2mK8nT1x',
  pageId: 'todo-list',
  status: 'WAITING_CONFIRMATION' as const,
  riskLevel: 'L1' as const,
  planVersion: 1,
  createdAt: '2026-08-25T12:00:00',
  updatedAt: '2026-08-25T12:01:00',
  finishedAt: null,
  errorCode: null,
};

const maliciousText = '<img src=x onerror=alert(1)><script>alert(2)</script>';
const detail = {
  ...summary,
  input: maliciousText,
  pageContext: {},
  plan: {},
  planHash: 'sha256:plan',
  steps: [{
    sequence: 1,
    toolCode: 'todo.query',
    riskLevel: 'L0' as const,
    status: 'SUCCEEDED',
    arguments: {},
    result: {},
    resultSummary: maliciousText,
    errorCode: null,
    startedAt: '2026-08-25T12:01:00',
    finishedAt: '2026-08-25T12:01:01',
  }],
  timeoutAt: '2026-08-25T12:02:00',
};

function renderPage() {
  render(<App><AiTaskCenterPage /></App>);
}

describe('AiTaskCenterPage', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders only server-owned tasks and keeps dangerous detail text inert', async () => {
    api.list.mockResolvedValue({ records: [summary], total: 1, page: 1, size: 20 });
    api.detail.mockResolvedValue(detail);
    renderPage();

    await screen.findByText(summary.taskId);
    fireEvent.click(screen.getByRole('button', { name: '查看' }));
    await waitFor(() => expect(screen.getAllByText(maliciousText).length).toBeGreaterThan(0));

    expect(document.querySelector('.agent-task-detail img')).toBeNull();
    expect(document.querySelector('.agent-task-detail script')).toBeNull();
    expect(api.detail).toHaveBeenCalledWith(summary.taskId);
  });

  it('requires a destructive confirmation before cancelling a cancellable task', async () => {
    api.list.mockResolvedValue({ records: [summary], total: 1, page: 1, size: 20 });
    api.cancel.mockResolvedValue({ ...detail, status: 'CANCELLED' });
    renderPage();
    await screen.findByText(summary.taskId);

    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(api.cancel).not.toHaveBeenCalled();
    fireEvent.click(await screen.findByRole('button', { name: '确认取消' }));
    await waitFor(() => expect(api.cancel).toHaveBeenCalledWith(summary.taskId));
  });
});
