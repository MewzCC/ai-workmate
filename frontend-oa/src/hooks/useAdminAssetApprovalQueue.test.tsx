import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, expect, it, vi } from 'vitest';
import { useAdminAssetApprovalQueue } from './useAdminAssetApprovalQueue';
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[] } }));
const messages = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
const mine = vi.fn(), pending = vi.fn();
const permissions = ['route:visitor-booking', 'visitor:read:self', 'visitor:create', 'approval:act'];
const options = { pageId: 'visitor-booking', readPermission: 'visitor:read:self', createPermission: 'visitor:create', statuses: ['PENDING'], mine, pending };
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions };
  mine.mockResolvedValue({ records: [{ id: 1 }], total: 100 }); pending.mockResolvedValue({ records: [], total: 0 });
});

it('uses current page/filter/queue when a write invokes a previously captured refresh', async () => {
  const { result } = renderHook(() => useAdminAssetApprovalQueue(options));
  await waitFor(() => expect(result.current.mine).toHaveLength(1)); const refresh = result.current.refresh;
  await act(async () => { await result.current.loadMine(3, 20, 'PENDING'); });
  // A user-applied status persists in the queue state.
  act(() => { result.current.changeStatus('PENDING'); });
  await waitFor(() => expect(mine.mock.lastCall?.[0]).toEqual({ page: 1, size: 20, status: 'PENDING' }));
  await act(async () => { await result.current.loadMine(3, 20); });
  await act(async () => { await refresh(); });
  expect(mine.mock.lastCall?.[0]).toEqual({ page: 3, size: 20, status: 'PENDING' });
  await act(async () => { await result.current.loadPending(); }); const count = mine.mock.calls.length;
  await act(async () => { await refresh(); });
  expect(result.current.activeTab).toBe('pending'); expect(mine).toHaveBeenCalledTimes(count); expect(pending).toHaveBeenCalledTimes(2);
});

it('rejects old loaders before sending requests after identity changes away and back', async () => {
  const { result, rerender } = renderHook(() => useAdminAssetApprovalQueue(options));
  await waitFor(() => expect(result.current.mine).toHaveLength(1));
  const loadMine = result.current.loadMine, loadPending = result.current.loadPending, refresh = result.current.refresh;
  auth.user = { ...auth.user, id: 8 }; rerender();
  await waitFor(() => expect(mine).toHaveBeenCalledTimes(2));
  auth.user = { ...auth.user, id: 7 }; rerender();
  await waitFor(() => expect(mine).toHaveBeenCalledTimes(3));
  await act(async () => { await loadMine(); await loadPending(); await refresh(); });
  expect(mine).toHaveBeenCalledTimes(3); expect(pending).not.toHaveBeenCalled();
});

it('loads all 205 assigned records and discards a later failed refresh instead of retaining a partial list', async () => {
  pending.mockImplementation(({ page, size }: { page: number; size: number }) => Promise.resolve({
    records: Array.from({ length: Math.min(size, 205 - (page - 1) * size) }, (_, index) => ({ id: (page - 1) * size + index + 1 })), total: 205,
  }));
  const { result } = renderHook(() => useAdminAssetApprovalQueue(options));
  await waitFor(() => expect(result.current.mine).toHaveLength(1));
  await act(async () => { await result.current.loadPending(); });
  expect(result.current.pending).toHaveLength(205); expect(pending).toHaveBeenCalledTimes(3);
  pending.mockImplementation(({ page }: { page: number }) => page === 1 ? Promise.resolve({ records: Array(100).fill({ id: 9 }), total: 205 }) : Promise.reject(new Error('Synthetic page failure')));
  await act(async () => { await result.current.refresh(); });
  expect(result.current.pending).toHaveLength(0); expect(messages.error).toHaveBeenCalled();
});

it('ignores delayed rows after revocation and never re-queries through an old refresh', async () => {
  const late = deferred<{ records: { id: number }[]; total: number }>();
  const { result, rerender } = renderHook(() => useAdminAssetApprovalQueue(options));
  await waitFor(() => expect(result.current.mine).toHaveLength(1));
  mine.mockReturnValueOnce(late.promise); let loading!: Promise<void>;
  await act(async () => { loading = result.current.refresh(); });
  const oldRefresh = result.current.refresh;
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; rerender();
  await act(async () => { late.resolve({ records: [{ id: 99 }], total: 1 }); await loading; await oldRefresh(); });
  expect(result.current.mine).toHaveLength(0); expect(mine).toHaveBeenCalledTimes(2); expect(messages.error).not.toHaveBeenCalled();
});
