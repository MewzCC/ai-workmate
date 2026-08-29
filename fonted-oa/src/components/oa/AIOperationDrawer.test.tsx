import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  planAiTask: vi.fn(),
  issueAiTaskConfirmation: vi.fn(),
  executeAiTask: vi.fn(),
  subscribeAiTaskEvents: vi.fn(() => vi.fn()),
}));

vi.mock('@/lib/oaApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/lib/oaApi')>();
  return { ...original, ...api };
});

import AIOperationDrawer from './AIOperationDrawer';

const basePlan = {
  taskId: 'agt_4YpV7zqR2mK8nT1x',
  status: 'PLAN_READY' as const,
  planVersion: 1,
  planHash: 'sha256:plan',
  riskLevel: 'L0' as const,
  confirmationRequired: false,
  expiresAt: null,
  summary: '查询本人待办并返回受控结果',
  steps: [{ sequence: 1, toolCode: 'todo.query', title: '查询本人待办', arguments: { limit: 10 } }],
};

function renderDrawer(onExecuted = vi.fn()) {
  render(
    <App>
      <AIOperationDrawer
        open
        role="system_admin"
        pageId="todo-list"
        pageTitle="待办中心"
        onClose={vi.fn()}
        onExecuted={onExecuted}
      />
    </App>,
  );
}

describe('AIOperationDrawer', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('keeps the bounded task composer in the fixed drawer footer', () => {
    renderDrawer();
    const input = screen.getByPlaceholderText('例如：查询我的待办，并按截止时间排序');
    const footer = input.closest('.ant-drawer-footer');
    expect((input as HTMLTextAreaElement).maxLength).toBe(4096);
    expect(footer?.contains(screen.getByRole('button', { name: /发送 \/ 生成计划/ }))).toBe(true);
    expect(footer?.contains(screen.getByRole('button', { name: /取消计划/ }))).toBe(true);
    expect(document.body.contains(screen.getByText('查询本人待办'))).toBe(true);
  });

  it('executes an L0 plan only after the user clicks and starts the cookie event stream', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({
      taskId: basePlan.taskId,
      status: 'QUEUED',
      statusUrl: `/api/ai/tasks/${basePlan.taskId}`,
      eventsUrl: `/api/ai/tasks/${basePlan.taskId}/events`,
    });
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));

    expect(api.executeAiTask).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '执行计划' }));
    await waitFor(() => expect(api.executeAiTask).toHaveBeenCalledWith(basePlan.taskId, {
      planVersion: 1,
      planHash: 'sha256:plan',
    }));
    expect(api.issueAiTaskConfirmation).not.toHaveBeenCalled();
    expect(api.subscribeAiTaskEvents).toHaveBeenCalledWith(basePlan.taskId, expect.any(Object));
  });

  it('issues a memory-only confirmation credential immediately before L1 execution', async () => {
    api.planAiTask.mockResolvedValue({ ...basePlan, status: 'WAITING_CONFIRMATION', riskLevel: 'L1', confirmationRequired: true });
    api.issueAiTaskConfirmation.mockResolvedValue({ token: 'one-time-secret', expiresAt: '2026-08-25T12:10:00+08:00' });
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem');
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    storageSpy.mockClear();

    fireEvent.click(screen.getByRole('button', { name: '确认并执行' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认执行' }));
    await waitFor(() => expect(api.issueAiTaskConfirmation).toHaveBeenCalledWith(basePlan.taskId, {
      planVersion: 1,
      planHash: 'sha256:plan',
    }));
    expect(api.executeAiTask).toHaveBeenCalledWith(basePlan.taskId, {
      planVersion: 1,
      planHash: 'sha256:plan',
      confirmationToken: 'one-time-secret',
    });
    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });

  it('renders L2 confirmation as destructive and forwards only the one-time credential', async () => {
    api.planAiTask.mockResolvedValue({
      ...basePlan,
      status: 'WAITING_CONFIRMATION',
      riskLevel: 'L2',
      confirmationRequired: true,
      steps: [{
        sequence: 1,
        toolCode: 'leave.submit',
        title: '提交本人请假草稿',
        arguments: { applicationId: 42, version: 3 },
      }],
    });
    api.issueAiTaskConfirmation.mockResolvedValue({ token: 'secondary-one-time-secret', expiresAt: '2026-08-29T22:00:00+08:00' });
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '提交刚才选择的请假草稿' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getByText('提交本人请假草稿')).toBeTruthy());

    fireEvent.click(screen.getByRole('button', { name: '确认并执行' }));
    const confirmButton = await screen.findByRole('button', { name: '确认执行' });
    expect(confirmButton.classList.contains('ant-btn-dangerous')).toBe(true);
    fireEvent.click(confirmButton);

    await waitFor(() => expect(api.executeAiTask).toHaveBeenCalledWith(basePlan.taskId, {
      planVersion: 1,
      planHash: 'sha256:plan',
      confirmationToken: 'secondary-one-time-secret',
    }));
  });
});
