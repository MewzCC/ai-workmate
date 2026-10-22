import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatMessage } from '@/types/chat';
import type { AgentTaskDetail } from '@/types/oa';

const api = vi.hoisted(() => ({ detail: vi.fn(), navigation: vi.fn(), error: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:employee-files', 'route:asset-ledger', 'route:suppliers'], version: 1 }));
vi.mock('@/lib/oaApi', async (load) => ({
  ...await load<typeof import('@/lib/oaApi')>(), agentTaskApi: { detail: api.detail },
}));
vi.mock('@/lib/navigationApi', () => ({ getNavigation: api.navigation }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({
  user: { id: 7, permissions: auth.permissions, permissionVersion: auth.version }, refresh: vi.fn(),
}) }));
import MessageItem from './MessageItem';

const reply: ChatMessage = { id: 2, role: 'assistant', content: '实际员工查询结果', status: 'success',
  feedback: null, attachments: [], citations: [], createdAt: '', sourceTaskNo: 'task-1', sourceToolCode: 'hr.employee.query' };
function detail(): AgentTaskDetail {
  return { taskId: 'task-1', pageId: 'ai-workspace', input: '查询员工', pageContext: {}, plan: {},
    planHash: 'hash', planVersion: 1, riskLevel: 'L0', status: 'SUCCEEDED', timeoutAt: null,
    createdAt: '', updatedAt: '', finishedAt: '', errorCode: null, steps: [{ sequence: 1,
      toolCode: 'hr.employee.query', status: 'SUCCEEDED', riskLevel: 'L0', arguments: { employeeId: 999 },
      result: { id: 23, url: 'https://invalid.example' }, resultSummary: '实际员工查询结果',
      errorCode: null, startedAt: '', finishedAt: '' }] };
}
function Location() {
  const location = useLocation();
  return <output aria-label="location">{location.pathname}{location.search}</output>;
}
function view(item = reply) {
  return <MemoryRouter initialEntries={['/oa/ai-workspace']}><MessageItem item={item} onRetry={vi.fn()} /><Location /></MemoryRouter>;
}
beforeEach(() => {
  vi.clearAllMocks();
  auth.permissions = ['route:employee-files', 'route:asset-ledger', 'route:suppliers'];
  auth.version = 1;
  api.detail.mockResolvedValue(detail());
  api.navigation.mockResolvedValue([{ routeKey: 'employee-files', name: '员工档案', routeType: 'PAGE',
    path: '/oa/employee-files', componentKey: 'EMPLOYEE_FILES', sortOrder: 1, children: [] }]);
});
afterEach(cleanup);

describe('verified business result navigation', () => {
  it('rechecks the task and live navigation before locating the actual returned record', async () => {
    render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(screen.getByLabelText('location').textContent).toBe('/oa/employee-files?id=23'));
    expect(api.detail).toHaveBeenCalledWith('task-1');
    expect(api.navigation).toHaveBeenCalledOnce();
  });

  it('does not follow a stale permission snapshot when the live menu no longer contains the page', async () => {
    api.navigation.mockResolvedValue([]);
    render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(api.error).toHaveBeenCalledOnce());
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
  });

  it.each(['RUNNING', 'FAILED'] as const)('does not navigate on a %s task', async (status) => {
    api.detail.mockResolvedValue({ ...detail(), status });
    render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(api.error).toHaveBeenCalledOnce());
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
  });

  it('does not offer result navigation for failed assistant messages', () => {
    render(view({ ...reply, status: 'failed' }));
    expect(screen.queryByRole('button', { name: '前往对应业务页面' })).toBeNull();
  });

  it('uses the shared asset receipt location rather than a task argument or result URL', async () => {
    const task = detail();
    task.steps[0] = { ...task.steps[0], toolCode: 'asset.claim', result: { assetId: 45 }, arguments: { assetId: 999 } };
    api.detail.mockResolvedValue(task);
    api.navigation.mockResolvedValue([{ routeKey: 'asset-ledger', name: '资产', routeType: 'PAGE',
      path: '/oa/asset-ledger', componentKey: 'ASSET_LEDGER', sortOrder: 1, children: [] }]);
    render(view({ ...reply, sourceToolCode: 'asset.claim' }));
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(screen.getByLabelText('location').textContent).toBe('/oa/asset-ledger?id=45'));
  });

  it('keeps non-record business tools on their code-owned module destination', async () => {
    const task = detail();
    task.steps[0] = { ...task.steps[0], toolCode: 'supplier.query', result: { items: [], total: 0 }, arguments: {} };
    api.detail.mockResolvedValue(task);
    api.navigation.mockResolvedValue([{ routeKey: 'suppliers', name: '供应商', routeType: 'PAGE',
      path: '/oa/suppliers', componentKey: 'SUPPLIER', sortOrder: 1, children: [] }]);
    render(view({ ...reply, sourceToolCode: 'supplier.query' }));
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(screen.getByLabelText('location').textContent).toBe('/oa/suppliers'));
  });

  it('rejects a mismatched task response', async () => {
    api.detail.mockResolvedValue({ ...detail(), taskId: 'different-task' });
    render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(api.error).toHaveBeenCalledOnce());
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
  });

  it('shows a safe failure and does not fall back to a module URL when retrieval fails', async () => {
    api.detail.mockRejectedValue(new Error('private backend details'));
    render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    await waitFor(() => expect(api.error).toHaveBeenCalledOnce());
    expect(api.error.mock.calls[0][0]).not.toContain('private backend details');
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
  });

  it('does not duplicate the authoritative reads while a click is already pending', async () => {
    let resolve!: (value: AgentTaskDetail) => void;
    api.detail.mockReturnValue(new Promise<AgentTaskDetail>((done) => { resolve = done; }));
    render(view());
    const button = screen.getByRole('button', { name: '前往对应业务页面' });
    fireEvent.click(button);
    fireEvent.click(button);
    await act(async () => resolve(detail()));
    expect(api.detail).toHaveBeenCalledOnce();
    expect(api.navigation).toHaveBeenCalledOnce();
    expect(screen.getByLabelText('location').textContent).toBe('/oa/employee-files?id=23');
  });

  it('does not navigate after the result message has unmounted', async () => {
    let resolve!: (value: AgentTaskDetail) => void;
    api.detail.mockReturnValue(new Promise<AgentTaskDetail>((done) => { resolve = done; }));
    const rendered = render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    rendered.rerender(<MemoryRouter><Location /></MemoryRouter>);
    await act(async () => resolve(detail()));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
  });

  it('ignores a late response after the current message becomes unsuccessful', async () => {
    let resolve!: (value: AgentTaskDetail) => void;
    api.detail.mockReturnValue(new Promise<AgentTaskDetail>((done) => { resolve = done; }));
    const rendered = render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    rendered.rerender(view({ ...reply, status: 'failed' }));
    await act(async () => resolve(detail()));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
  });

  it('ignores a late response after live permission revocation', async () => {
    let resolve!: (value: AgentTaskDetail) => void;
    api.detail.mockReturnValue(new Promise<AgentTaskDetail>((done) => { resolve = done; }));
    const rendered = render(view());
    fireEvent.click(screen.getByRole('button', { name: '前往对应业务页面' }));
    auth.permissions = [];
    auth.version = 2;
    rendered.rerender(view());
    await act(async () => resolve(detail()));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/ai-workspace');
    expect(screen.queryByRole('button', { name: '前往对应业务页面' })).toBeNull();
  });
});
