import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['todo:read'], roles: ['USER'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ detail: vi.fn(), timeline: vi.fn(), error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), todoApi: { detail: api.detail, timeline: api.timeline } }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));
import { useApprovalTaskDetail } from './useApprovalTaskDetail';
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { vi.clearAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1 }; bindAuthResponseIdentity('detail-owner', 'read');
  api.detail.mockImplementation(async (taskId: number) => ({ id: taskId + 10, taskId })); api.timeline.mockResolvedValue([{ id: 1 }]); });
afterEach(cleanup);
it('loads detail and history for the same requested task under StrictMode', async () => {
  const hook = renderHook(() => useApprovalTaskDetail(42), { reactStrictMode: true });
  await waitFor(() => expect(hook.result.current.application?.taskId).toBe(42)); expect(hook.result.current.timeline).toEqual([{ id: 1 }]);
});
it('hides a closed drawer immediately and ignores late data after reopen', async () => {
  const late = deferred<{ id: number; taskId: number }>(); api.detail.mockReturnValueOnce(late.promise);
  const hook = renderHook(({ active }) => useApprovalTaskDetail(42, active), { initialProps: { active: true } });
  hook.rerender({ active: false }); expect(hook.result.current.application).toBeUndefined();
  hook.rerender({ active: true }); await waitFor(() => expect(hook.result.current.application?.id).toBe(52));
  await act(async () => { late.resolve({ id: 99, taskId: 42 }); }); expect(hook.result.current.application?.id).toBe(52);
});
it('clears both detail and history after read failure and exposes retry', async () => {
  const hook = renderHook(() => useApprovalTaskDetail(42)); await waitFor(() => expect(hook.result.current.application).toBeTruthy());
  api.timeline.mockRejectedValueOnce(new Error('history denied'));
  await act(async () => { await hook.result.current.load(); });
  expect(hook.result.current.application).toBeUndefined(); expect(hook.result.current.timeline).toEqual([]); expect(hook.result.current.error).toBeTruthy();
  await act(async () => { await hook.result.current.load(); }); expect(hook.result.current.application?.id).toBe(52);
});
it('rejects invalid IDs and a response for a different task', async () => {
  const hook = renderHook(({ id }) => useApprovalTaskDetail(id), { initialProps: { id: 0 } });
  await waitFor(() => expect(hook.result.current.error).toBeTruthy()); expect(api.detail).not.toHaveBeenCalled();
  api.detail.mockResolvedValueOnce({ id: 9, taskId: 3 }); hook.rerender({ id: 42 });
  await waitFor(() => expect(api.detail).toHaveBeenCalled()); await waitFor(() => expect(hook.result.current.loading).toBe(false));
  expect(hook.result.current.application).toBeUndefined(); expect(hook.result.current.error).toBeTruthy();
});
it('suppresses another identity and an unmounted detail callback', async () => {
  const late = deferred<{ id: number; taskId: number }>(); api.detail.mockReturnValueOnce(late.promise);
  const hook = renderHook(() => useApprovalTaskDetail(42)); auth.user = { ...auth.user, id: 8, permissionVersion: 2 }; hook.rerender();
  await waitFor(() => expect(hook.result.current.application?.id).toBe(52));
  await act(async () => { late.resolve({ id: 99, taskId: 42 }); }); expect(hook.result.current.application?.id).toBe(52);
  const old = hook.result.current; hook.unmount(); await expect(old.load()).resolves.toBeNull();
});
