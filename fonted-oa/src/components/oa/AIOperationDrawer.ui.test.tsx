import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AIOperationDrawer from './AIOperationDrawer';

const api = vi.hoisted(() => ({ planAiTask: vi.fn(), executeAiTask: vi.fn(), subscribeAiTaskEvents: vi.fn((
  _taskId: string, _subscription: { onEvent: (event: { id: string; type: string; data: { status: string } }) => void },
) => vi.fn()),
  getPageCapabilities: vi.fn(), detail: vi.fn(), apply: vi.fn() }));
const bridge = { applyTask: api.apply };
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), ...api, agentTaskApi: { detail: api.detail } }));
vi.mock('./PageUiCommandBridge', () => ({ usePageUiCommandBridge: () => bridge }));

const plan = { taskId: 'synthetic-ui-task', status: 'PLAN_READY', planVersion: 1, planHash: 'sha256:synthetic', riskLevel: 'L0',
  confirmationRequired: false, summary: 'Prepare a local form', expiresAt: null,
  steps: [{ sequence: 1, toolCode: 'page.ui.prepare', title: 'Prepare', arguments: { pageId: 'todo', code: 'ui.openCreateForm' } }] };
beforeEach(() => {
  vi.clearAllMocks();
  api.planAiTask.mockResolvedValue(plan);
  api.executeAiTask.mockResolvedValue({ taskId: plan.taskId, status: 'QUEUED', statusUrl: '/status', eventsUrl: '/events' });
  api.getPageCapabilities.mockResolvedValue({ pageId: 'todo', componentKey: 'TODO_LIST', version: 1,
    uiCommands: ['ui.openCreateForm'], dataScopePolicy: 'SELF', effectiveDataScopes: ['SELF'],
    contextSchema: { maxBytes: 4096, maxDepth: 2, fields: [] },
    tools: [{ code: 'page.ui.prepare', name: 'Prepare', description: 'Local state only', riskLevel: 'L0', sideEffect: 'NONE', confirmationPolicy: 'NONE', ownershipPolicy: 'SELF' }] });
  api.apply.mockResolvedValue(true);
});
afterEach(cleanup);

it.each([null, 'Prepared a local form'])('applies UI receipts without refreshing away the prepared form, summary=%s', async summary => {
  const onRefresh = vi.fn(), navigate = vi.fn();
  const detail = { taskId: plan.taskId, status: 'SUCCEEDED', steps: [{ sequence: 1, toolCode: 'page.ui.prepare', status: 'SUCCEEDED',
    resultSummary: summary, result: { pageId: 'todo', code: 'ui.openCreateForm', fields: [] } }], errorCode: null };
  api.detail.mockResolvedValue(detail);
  render(<App><AIOperationDrawer open role="employee" pageId="todo" pageTitle="Todo" onClose={() => {}}
    onExecutionCompleted={onRefresh} onNavigatePage={navigate} /></App>);
  fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Prepare a local form' } });
  fireEvent.click(screen.getByRole('button', { name: /发送 \/ 生成计划/ }));
  await waitFor(() => expect(api.subscribeAiTaskEvents).toHaveBeenCalledTimes(1));
  const events = api.subscribeAiTaskEvents.mock.calls[0][1];
  act(() => { events.onEvent({ id: 'completed', type: 'task-completed', data: { status: 'SUCCEEDED' } }); });
  expect(onRefresh).not.toHaveBeenCalled();
  await waitFor(() => expect(api.apply).toHaveBeenCalledWith(detail));
  expect(onRefresh).not.toHaveBeenCalled();
  expect(navigate).not.toHaveBeenCalled();
});
