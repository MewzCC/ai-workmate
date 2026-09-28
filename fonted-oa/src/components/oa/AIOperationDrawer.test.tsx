import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  planAiTask: vi.fn(),
  issueAiTaskConfirmation: vi.fn(),
  executeAiTask: vi.fn(),
  subscribeAiTaskEvents: vi.fn((
    _taskId: string,
    _subscription: {
      onEvent: (event: { id: string; type: string; data: Record<string, unknown> }) => void;
      onError?: (error: unknown) => void;
    },
  ) => vi.fn()),
  getPageCapabilities: vi.fn(),
  agentTaskApi: { detail: vi.fn() },
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

function renderDrawer(
  onExecutionCompleted = vi.fn(),
  pageContext?: Readonly<Record<string, string | number | boolean>>,
) {
  render(
    <App>
      <AIOperationDrawer
        open
        role="system_admin"
        pageId="todo-list"
        pageTitle="待办中心"
        pageContext={pageContext}
        onClose={vi.fn()}
        onExecutionCompleted={onExecutionCompleted}
      />
    </App>,
  );
}

describe('AIOperationDrawer', () => {
  beforeEach(() => {
    api.getPageCapabilities.mockResolvedValue({
      pageId: 'todo',
      componentKey: 'TODO_LIST',
      version: 1,
      uiCommands: ['ui.navigate', 'ui.applyFilter', 'ui.openDetail', 'ui.refreshPage'],
      dataScopePolicy: 'ASSIGNED_TO_SELF',
      effectiveDataScopes: ['SELF'],
      contextSchema: {
        maxBytes: 4096,
        maxDepth: 1,
        fields: [
          { name: 'status', valueType: 'STRING', maxLength: 200 },
          { name: 'from', valueType: 'STRING', maxLength: 200 },
          { name: 'to', valueType: 'STRING', maxLength: 200 },
          { name: 'page', valueType: 'NUMBER', maxLength: 32 },
          { name: 'size', valueType: 'NUMBER', maxLength: 32 },
        ],
      },
      tools: [{
        code: 'todo.query',
        name: 'Query my approval tasks',
        description: 'Only my assigned approval tasks',
        riskLevel: 'L0',
        sideEffect: 'NONE',
        confirmationPolicy: 'NONE',
        ownershipPolicy: 'ASSIGNED_TO_SELF',
      }],
    });
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('loads live page capabilities and keeps the bounded task composer in the fixed drawer footer', async () => {
    renderDrawer();
    const input = screen.getByPlaceholderText('例如：查询我的待办，并按截止时间排序');
    const footer = input.closest('.ant-drawer-footer');
    expect((input as HTMLTextAreaElement).maxLength).toBe(4096);
    expect(footer?.contains(screen.getByRole('button', { name: /发送 \/ 生成计划/ }))).toBe(true);
    expect(footer?.contains(screen.getByRole('button', { name: /取消计划/ }))).toBe(true);
    expect(document.body.contains(await screen.findByText('查询本人待办 · 只读 · L0'))).toBe(true);
    expect(api.getPageCapabilities).toHaveBeenCalledWith('todo-list');
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

  it('sends the current bounded page context with the planning request', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    renderDrawer(vi.fn(), { status: 'PENDING', page: 2, size: 20, keyword: 'must-drop' });
    await screen.findByText('查询本人待办 · 只读 · L0');
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询当前筛选下的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));

    await waitFor(() => expect(api.planAiTask).toHaveBeenCalledWith({
      input: '查询当前筛选下的待办',
      pageId: 'todo-list',
      pageContext: { status: 'PENDING', page: 2, size: 20 },
    }));
  });

  it('refreshes the current business page once after a successful terminal event', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({
      taskId: basePlan.taskId,
      status: 'QUEUED',
      statusUrl: `/api/ai/tasks/${basePlan.taskId}`,
      eventsUrl: `/api/ai/tasks/${basePlan.taskId}/events`,
    });
    const onExecutionCompleted = vi.fn();
    api.agentTaskApi.detail.mockResolvedValue({
      taskId: basePlan.taskId,
      status: 'SUCCEEDED',
      steps: [{ sequence: 1, toolCode: 'todo.query', status: 'SUCCEEDED', resultSummary: null, result: { total: 1, items: [{ applicantName: '张三', status: 'PENDING' }] } }],
      errorCode: null,
    });
    renderDrawer(onExecutionCompleted);
    const input = screen.getByRole('textbox') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    expect(input.value).toBe('');
    fireEvent.click(screen.getByRole('button', { name: '执行计划' }));
    await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledTimes(1));

    const subscription = api.subscribeAiTaskEvents.mock.calls[0][1] as {
      onEvent: (event: { id: string; type: string; data: Record<string, unknown> }) => void;
    };
    act(() => {
      subscription.onEvent({ id: 'evt-1', type: 'task-completed', data: { status: 'SUCCEEDED' } });
      subscription.onEvent({ id: 'evt-1', type: 'task-completed', data: { status: 'SUCCEEDED' } });
    });

    expect(onExecutionCompleted).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(api.agentTaskApi.detail).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('实际执行结果')).toBeTruthy();
    expect(await screen.findByText('共 1 条记录')).toBeTruthy();
    expect(await screen.findByText(/张三/)).toBeTruthy();
  });

  it('keeps the prompt when planning fails and does not erase a newer draft', async () => {
    let resolvePlan: (value: typeof basePlan) => void = () => undefined;
    api.planAiTask.mockImplementation(() => new Promise((resolve) => { resolvePlan = resolve; }));
    renderDrawer();
    const input = screen.getByRole('textbox') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    fireEvent.change(input, { target: { value: '下一条指令' } });
    await act(async () => resolvePlan(basePlan));
    expect(input.value).toBe('下一条指令');
  });

  it('retains the submitted prompt when planning fails so it can be edited and retried', async () => {
    api.planAiTask.mockRejectedValue(new Error('planning unavailable'));
    renderDrawer();
    const input = screen.getByRole('textbox') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getByText('AI 能力调用失败')).toBeTruthy());
    expect(input.value).toBe('查询我的待办');
  });

  it('shows a retryable result error instead of inventing output when detail retrieval fails', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    api.agentTaskApi.detail.mockRejectedValue(new Error('network down'));
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    fireEvent.click(screen.getByRole('button', { name: '执行计划' }));
    await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledTimes(1));
    const subscription = api.subscribeAiTaskEvents.mock.calls[0][1] as {
      onEvent: (event: { id: string; type: string; data: Record<string, unknown> }) => void;
    };
    act(() => subscription.onEvent({ id: 'evt-1', type: 'task-completed', data: { status: 'SUCCEEDED' } }));
    expect(await screen.findByText('执行结果读取失败')).toBeTruthy();
    expect(screen.queryByText('此步骤没有返回数据')).toBeNull();
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
    await waitFor(() => expect(screen.getByText('提交已有请假草稿')).toBeTruthy());
    expect(screen.getByText('applicationId')).toBeTruthy();
    expect(screen.getByText('42')).toBeTruthy();

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
