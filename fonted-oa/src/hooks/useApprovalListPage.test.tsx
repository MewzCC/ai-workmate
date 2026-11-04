import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['approval:read'], roles: ['ADMIN'], dataScopes: ['TENANT'] } }));
const api = vi.hoisted(() => ({ list: vi.fn(), stats: vi.fn(), detail: vi.fn(), push: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers, context: {} as Record<string, unknown> }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), approvalApi: { list: api.list, stats: api.stats }, todoApi: { detail: api.detail } }));
vi.mock('@/lib/nextCompat', () => ({ useRouter: () => ({ push: api.push }) }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));
vi.mock('@/components/oa/PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
vi.mock('@/components/oa/PageAgentContext', () => ({ usePublishPageAgentContext: (context: Record<string, unknown>) => { ui.context = context; } }));
import { useApprovalListPage } from './useApprovalListPage';
const records = (id = 9) => ({ records: [{ id, applicantName: 'Synthetic applicant' }], total: 1 });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
const filter = (filters: Record<string, string | number>) => ui.handlers['ui.applyFilter']!({ pageId: 'approval-list', code: 'ui.applyFilter', payload: { filters } });
const open = (recordId: number | string) => ui.handlers['ui.openDetail']!({ pageId: 'approval-list', code: 'ui.openDetail', payload: { recordId } });
beforeEach(() => { vi.clearAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: ['approval:read'] };
  bindAuthResponseIdentity('approval-admin', 'read'); api.list.mockResolvedValue(records()); api.stats.mockResolvedValue([{ status: 'PENDING', count: 4 }]); api.detail.mockImplementation(async (taskId: number) => ({ id: 9, taskId })); });
afterEach(cleanup);

it('keeps unsubmitted search separate and applies numeric paging, status and local dates', async () => {
  const hook = renderHook(useApprovalListPage); await waitFor(() => expect(hook.result.current.total).toBe(1));
  const calls = api.list.mock.calls.length; act(() => hook.result.current.setSearch('draft search'));
  expect(api.list).toHaveBeenCalledTimes(calls); expect(ui.context.keyword).toBe('');
  await act(async () => { await filter({ keyword: 'applied', status: 'WITHDRAWN', page: 2, size: 50, from: '2026-11-01', to: '2026-11-04' }); });
  await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ keyword: 'applied', status: 'WITHDRAWN', page: 2, size: 50, leaveType: undefined, from: '2026-11-01T00:00:00', to: '2026-11-04T00:00:00' }));
  expect(hook.result.current.search).toBe('applied'); expect(ui.context.size).toBe(50);
  const invalid: Record<string, string | number>[] = [{ size: '20' }, { size: 51 }, { page: 0 }, { leaveType: 'HACK' }, { keyword: 'x'.repeat(121) }, { from: '2026-02-30', to: '2026-03-01' }];
  for (const fields of invalid)
    await expect(async () => filter(fields)).rejects.toThrow();
});
it('preflights a protected detail, rejects string identifiers and mismatched receipts', async () => {
  const hook = renderHook(useApprovalListPage); await waitFor(() => expect(hook.result.current.total).toBe(1));
  await act(async () => { await open(42); }); expect(hook.result.current.drawerTaskId).toBe(42);
  await expect(async () => open('42')).rejects.toThrow();
  api.detail.mockResolvedValueOnce({ id: 9, taskId: 3 }); await expect(open(99)).rejects.toThrow();
  expect(hook.result.current.drawerTaskId).toBe(42);
  api.detail.mockRejectedValueOnce(new Error('denied')); await expect(open(100)).rejects.toThrow('denied'); expect(api.push).not.toHaveBeenCalled();
});
it('does not reopen a closed preview from a late request or an older opening', async () => {
  const hook = renderHook(useApprovalListPage); await waitFor(() => expect(hook.result.current.total).toBe(1));
  const late = deferred<{ id: number; taskId: number }>(); api.detail.mockReturnValueOnce(late.promise);
  let pending!: Promise<void> | void; act(() => { pending = open(42); }); const rejected = expect(pending).rejects.toThrow();
  act(() => hook.result.current.closeDetail()); await act(async () => { late.resolve({ id: 9, taskId: 42 }); await rejected; });
  expect(hook.result.current.drawerTaskId).toBeNull();
});
it('clears preview and records when filters change and ignores the older list', async () => {
  const hook = renderHook(useApprovalListPage); await waitFor(() => expect(hook.result.current.total).toBe(1));
  await act(async () => { await open(42); }); const old = deferred<ReturnType<typeof records>>(); api.list.mockReturnValueOnce(old.promise);
  let refresh!: Promise<unknown>; act(() => { refresh = hook.result.current.load(); });
  api.list.mockResolvedValueOnce(records(10)); act(() => hook.result.current.setPage(2));
  expect(hook.result.current.drawerTaskId).toBeNull(); await waitFor(() => expect(hook.result.current.records[0].id).toBe(10));
  await act(async () => { old.resolve(records(99)); await refresh; }); expect(hook.result.current.records[0].id).toBe(10);
});
it('fails refresh explicitly and clears statistics instead of keeping stale successful rows', async () => {
  const hook = renderHook(useApprovalListPage); await waitFor(() => expect(hook.result.current.total).toBe(1));
  api.stats.mockRejectedValueOnce(new Error('stats unavailable'));
  await act(async () => { await expect(hook.result.current.refresh()).rejects.toThrow(); });
  expect(hook.result.current.records).toEqual([]); expect(hook.result.current.stats).toEqual([]); expect(hook.result.current.error).toBeTruthy();
});
it('revokes list and opening callbacks without another login and cannot revive them after regrant', async () => {
  const hook = renderHook(useApprovalListPage); await waitFor(() => expect(hook.result.current.total).toBe(1));
  const old = ui.handlers, late = deferred<{ id: number; taskId: number }>(); api.detail.mockReturnValueOnce(late.promise);
  const pending = open(42), rejected = expect(pending).rejects.toThrow();
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; hook.rerender();
  expect(hook.result.current.records).toEqual([]); expect(hook.result.current.drawerTaskId).toBeNull();
  await act(async () => { late.resolve({ id: 9, taskId: 42 }); await rejected; });
  auth.user = { ...auth.user, permissionVersion: 3, permissions: ['approval:read'] }; hook.rerender();
  await waitFor(() => expect(hook.result.current.total).toBe(1));
  await expect(async () => old['ui.applyFilter']!({ pageId: 'approval-list', code: 'ui.applyFilter', payload: { filters: { page: 2 } } })).rejects.toThrow();
});
