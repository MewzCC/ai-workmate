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
  onNavigatePage?: (pageId: string) => boolean,
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
        onNavigatePage={onNavigatePage}
      />
    </App>,
  );
}

describe('AIOperationDrawer', () => {
  beforeEach(() => {
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
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

  it('automatically executes an allowed L0 read after one user request and starts the cookie event stream', async () => {
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

    await waitFor(() => expect(api.executeAiTask).toHaveBeenCalledWith(basePlan.taskId, {
      planVersion: 1,
      planHash: 'sha256:plan',
    }));
    expect(api.issueAiTaskConfirmation).not.toHaveBeenCalled();
    expect(api.subscribeAiTaskEvents).toHaveBeenCalledWith(basePlan.taskId, expect.any(Object));
    expect(api.executeAiTask).toHaveBeenCalledOnce();
  });

  it.each([
    { status: 'WAITING_CONFIRMATION', riskLevel: 'L1', confirmationRequired: true },
    { status: 'WAITING_CONFIRMATION', riskLevel: 'L2', confirmationRequired: true },
    { status: 'PLAN_READY', riskLevel: 'L1', confirmationRequired: false },
    { status: 'PLAN_READY', riskLevel: 'L0', confirmationRequired: true },
  ])('does not automatically execute a plan with inconsistent or confirmed risk: $riskLevel / $confirmationRequired', async (risk) => {
    api.planAiTask.mockResolvedValue({ ...basePlan, ...risk });
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '处理当前任务' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    expect(api.executeAiTask).not.toHaveBeenCalled();
    expect(api.issueAiTaskConfirmation).not.toHaveBeenCalled();
  });

  it('does not automatically execute a tool absent from live page capabilities', async () => {
    api.planAiTask.mockResolvedValue({ ...basePlan, steps: [{ ...basePlan.steps[0], toolCode: 'unknown.query' }] });
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询数据' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    expect(api.executeAiTask).not.toHaveBeenCalled();
  });

  it.each([
    { sideEffect: 'SINGLE_WRITE' },
    { riskLevel: 'L1' },
    { confirmationPolicy: 'EXPLICIT' },
  ])('does not automatically execute inconsistent live tool metadata: %j', async (metadata) => {
    const capability = await api.getPageCapabilities('todo-list');
    api.getPageCapabilities.mockResolvedValue({ ...capability, tools: [{ ...capability.tools[0], ...metadata }] });
    api.planAiTask.mockResolvedValue(basePlan);
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    expect(api.executeAiTask).not.toHaveBeenCalled();
  });

  it('does not automatically execute when navigation precedes the planning response', async () => {
    let resolve!: (value: typeof basePlan) => void;
    api.planAiTask.mockReturnValue(new Promise<typeof basePlan>((done) => { resolve = done; }));
    const drawer = (pageId: string) => <App><AIOperationDrawer open role="system_admin" pageId={pageId}
      pageTitle={pageId} onClose={vi.fn()} /></App>;
    const view = render(drawer('todo-list'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(api.planAiTask).toHaveBeenCalledOnce());
    view.rerender(drawer('visitor-booking'));
    await act(async () => resolve(basePlan));
    expect(api.executeAiTask).not.toHaveBeenCalled();
  });

  it('keeps an accepted plan and does not invent success or retry when automatic enqueue fails', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockRejectedValue(new Error('queue unavailable'));
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getByText('AI 能力调用失败')).toBeTruthy());
    expect(screen.getAllByText(basePlan.summary)).toHaveLength(2);
    expect(api.executeAiTask).toHaveBeenCalledOnce();
    expect(api.subscribeAiTaskEvents).not.toHaveBeenCalled();
    expect(screen.queryByText('实际执行结果')).toBeNull();
  });

  it('requires manual execution after leaving and returning before the original plan finishes', async () => {
    let resolve!: (value: typeof basePlan) => void;
    api.planAiTask.mockReturnValue(new Promise<typeof basePlan>((done) => { resolve = done; }));
    const drawer = (pageId: string) => <App><AIOperationDrawer open role="system_admin" pageId={pageId}
      pageTitle={pageId} onClose={vi.fn()} /></App>;
    const view = render(drawer('todo-list'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(api.planAiTask).toHaveBeenCalledOnce());
    view.rerender(drawer('visitor-booking'));
    view.rerender(drawer('todo-list'));
    await act(async () => resolve(basePlan));
    expect(api.executeAiTask).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '执行计划' }));
    await waitFor(() => expect(api.executeAiTask).toHaveBeenCalledOnce());
  });

  it('does not execute a late plan after the permission-owned drawer is unmounted', async () => {
    let resolve!: (value: typeof basePlan) => void;
    api.planAiTask.mockReturnValue(new Promise<typeof basePlan>((done) => { resolve = done; }));
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(api.planAiTask).toHaveBeenCalledOnce());
    cleanup();
    await act(async () => resolve(basePlan));
    expect(api.executeAiTask).not.toHaveBeenCalled();
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

  it('answers with real todo text and navigates to an authorized todo page once', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    api.agentTaskApi.detail.mockResolvedValue({ taskId: basePlan.taskId, status: 'SUCCEEDED', steps: [
      { sequence: 1, toolCode: 'todo.query', status: 'SUCCEEDED', arguments: {},
        resultSummary: '查询到 1 条：张三的年假申请。', result: { total: 1, items: [{ id: 1, applicantName: '张三' }] } },
    ], errorCode: null });
    const navigate = vi.fn(() => true);
    renderDrawer(vi.fn(), undefined, navigate);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledTimes(1));
    const subscription = api.subscribeAiTaskEvents.mock.calls[0][1] as {
      onEvent: (event: { id: string; type: string; data: Record<string, unknown> }) => void;
    };
    act(() => subscription.onEvent({ id: 'evt-1', type: 'task-completed', data: { status: 'SUCCEEDED' } }));
    await waitFor(() => expect(navigate).toHaveBeenCalledOnce());
    expect(navigate).toHaveBeenCalledWith('todo');
    expect(screen.getAllByText('查询到 1 条：张三的年假申请。')).toHaveLength(2);
    expect(document.querySelector('.oa-ai-result-json')).toBeNull();
  });

  it('shows a non-todo business result in the conversation and navigates once', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    api.agentTaskApi.detail.mockResolvedValue({ taskId: basePlan.taskId, status: 'SUCCEEDED', steps: [
      { sequence: 1, toolCode: 'dashboard.overview.query', status: 'SUCCEEDED', arguments: { days: 7 },
        resultSummary: '本人驾驶舱：待处理事项 3 条，未读消息 8 条。', result: { metrics: [{ code: 'PENDING_TODOS', value: 3 }] } },
    ], errorCode: null });
    const navigate = vi.fn(() => true);
    renderDrawer(vi.fn(), undefined, navigate);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的驾驶舱' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledTimes(1));
    const subscription = api.subscribeAiTaskEvents.mock.calls[0][1] as {
      onEvent: (event: { id: string; type: string; data: Record<string, unknown> }) => void;
    };
    act(() => {
      subscription.onEvent({ id: 'dashboard-completed', type: 'task-completed', data: { status: 'SUCCEEDED' } });
      subscription.onEvent({ id: 'dashboard-completed', type: 'task-completed', data: { status: 'SUCCEEDED' } });
    });
    await waitFor(() => expect(navigate).toHaveBeenCalledOnce());
    expect(navigate).toHaveBeenCalledWith('dashboard');
    expect(screen.getAllByText('本人驾驶舱：待处理事项 3 条，未读消息 8 条。')).toHaveLength(2);
  });

  it('locates the employee returned by the successful tool instead of opening the default employee', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED' });
    api.agentTaskApi.detail.mockResolvedValue({ taskId: basePlan.taskId, status: 'SUCCEEDED', steps: [
      { sequence: 1, toolCode: 'hr.employee.query', status: 'SUCCEEDED', arguments: { employeeId: 999 },
        resultSummary: '员工 #23：员工乙。', result: { id: 23, name: '员工乙', url: 'https://invalid.example' } },
    ], errorCode: null });
    const navigate = vi.fn(() => true);
    renderDrawer(vi.fn(), undefined, navigate);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询员工乙档案' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledOnce());
    act(() => api.subscribeAiTaskEvents.mock.calls[0][1].onEvent({ id: 'employee-complete', type: 'task-completed', data: { status: 'SUCCEEDED' } }));
    await waitFor(() => expect(navigate).toHaveBeenCalledWith('employee-files', 23));
    expect(navigate).toHaveBeenCalledOnce();
  });

  it('keeps the prompt when planning fails and does not erase a newer draft', async () => {
    let resolvePlan: (value: typeof basePlan) => void = () => undefined;
    api.planAiTask.mockImplementation(() => new Promise((resolve) => { resolvePlan = resolve; }));
    renderDrawer();
    const input = screen.getByRole('textbox') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    fireEvent.change(input, { target: { value: '下一条指令' } });
    await waitFor(() => expect(api.planAiTask).toHaveBeenCalledOnce());
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

  it('preserves the plan across page changes but blocks execution outside its source page', async () => {
    api.planAiTask.mockResolvedValue({ ...basePlan, status: 'WAITING_CONFIRMATION', riskLevel: 'L1', confirmationRequired: true });
    const drawer = (pageId: string) => <App><AIOperationDrawer open role="system_admin" pageId={pageId}
      pageTitle={pageId === 'todo-list' ? '待办中心' : '访客预约'} onClose={vi.fn()} /></App>;
    const view = render(drawer('todo-list'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));

    view.rerender(drawer('visitor-booking'));
    expect(screen.getByText('该计划属于其他页面，请返回原页面后执行')).toBeTruthy();
    expect((screen.getByRole('button', { name: '确认并执行' }) as HTMLButtonElement).disabled).toBe(true);
    expect(api.executeAiTask).not.toHaveBeenCalled();

    view.rerender(drawer('todo-list'));
    expect((screen.getByRole('button', { name: '确认并执行' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('keeps listening for the real result while the drawer is hidden during navigation', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    api.agentTaskApi.detail.mockResolvedValue({ taskId: basePlan.taskId, status: 'SUCCEEDED', steps: [
      { sequence: 1, toolCode: 'todo.query', status: 'SUCCEEDED', resultSummary: null, result: { total: 0, items: [] } },
    ], errorCode: null });
    const drawer = (open: boolean, pageId: string) => <App><AIOperationDrawer open={open} role="system_admin"
      pageId={pageId} pageTitle={pageId === 'todo-list' ? '待办中心' : 'AI 工作空间'} onClose={vi.fn()} /></App>;
    const view = render(drawer(true, 'todo-list'));
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
    await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledTimes(1));

    view.rerender(drawer(false, 'ai-workspace'));
    expect(api.subscribeAiTaskEvents.mock.results[0].value).not.toHaveBeenCalled();
    const subscription = api.subscribeAiTaskEvents.mock.calls[0][1] as {
      onEvent: (event: { id: string; type: string; data: Record<string, unknown> }) => void;
    };
    act(() => subscription.onEvent({ id: 'evt-1', type: 'task-completed', data: { status: 'SUCCEEDED' } }));
    await waitFor(() => expect(api.agentTaskApi.detail).toHaveBeenCalledWith(basePlan.taskId));
    view.rerender(drawer(true, 'todo-list'));
    expect(await screen.findByText('没有匹配的记录')).toBeTruthy();
  });

  it('shows a retryable result error instead of inventing output when detail retrieval fails', async () => {
    api.planAiTask.mockResolvedValue(basePlan);
    api.executeAiTask.mockResolvedValue({ taskId: basePlan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
    api.agentTaskApi.detail.mockRejectedValue(new Error('network down'));
    renderDrawer();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询我的待办' } });
    fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
    await waitFor(() => expect(screen.getAllByText(basePlan.summary)).toHaveLength(2));
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
