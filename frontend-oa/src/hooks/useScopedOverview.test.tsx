import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import { useScopedOverview } from './useScopedOverview';
import { StrictMode } from 'react';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['read'], roles: ['USER'], dataScopes: ['SELF'] } }));
const errors = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/antdMessage', () => ({ message: errors }));
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { vi.clearAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1 }; bindAuthResponseIdentity('overview-user', 'read'); });
afterEach(cleanup);

describe('owned query snapshots', () => {
  it('loads after React StrictMode replays effect setup and cleanup', async () => {
    const query = vi.fn().mockResolvedValue('strict-mode-owned');
    const hook = renderHook(() => useScopedOverview(query), { reactStrictMode: true, wrapper: ({ children }) => <StrictMode>{children}</StrictMode> });
    await waitFor(() => expect(hook.result.current.data).toBe('strict-mode-owned'));
    expect(hook.result.current.loading).toBe(false);
    expect(query).toHaveBeenCalledTimes(2);
  });
  it('hides prior query rows immediately and rejects an older result after a query change', async () => {
    const late = deferred<string>();
    const queryA = vi.fn().mockResolvedValueOnce('original-a').mockReturnValueOnce(late.promise);
    const queryB = vi.fn().mockResolvedValue('current-b');
    const hook = renderHook(({ query }) => useScopedOverview<string>(query), { initialProps: { query: queryA } });
    await waitFor(() => expect(hook.result.current.data).toBe('original-a'));
    let pending!: Promise<string | null>;
    act(() => { pending = hook.result.current.load(); });
    hook.rerender({ query: queryB });
    expect(hook.result.current.data).toBeNull();
    await waitFor(() => expect(hook.result.current.data).toBe('current-b'));
    await act(async () => { late.resolve('late-a'); await pending; });
    expect(hook.result.current.data).toBe('current-b');
  });
  it('does not resurrect callbacks after an identity A to B to A change', async () => {
    const query = vi.fn().mockResolvedValue('owned');
    const hook = renderHook(() => useScopedOverview(query));
    await waitFor(() => expect(hook.result.current.data).toBe('owned'));
    const old = hook.result.current;
    auth.user = { ...auth.user, id: 8 }; hook.rerender();
    auth.user = { ...auth.user, id: 7 }; hook.rerender();
    const calls = query.mock.calls.length;
    await act(async () => { expect(await old.load()).toBeNull(); old.setData('stale'); });
    expect(query).toHaveBeenCalledTimes(calls);
    expect(old.isCurrent()).toBe(false);
    expect(hook.result.current.data).not.toBe('stale');
  });
  it('clears rows and exposes a current failure while suppressing errors after unmount', async () => {
    const query = vi.fn().mockResolvedValueOnce('owned').mockRejectedValueOnce(new Error('synthetic'));
    const hook = renderHook(() => useScopedOverview(query));
    await waitFor(() => expect(hook.result.current.data).toBe('owned'));
    await act(async () => { expect(await hook.result.current.load()).toBeNull(); });
    expect(hook.result.current.data).toBeNull(); expect(hook.result.current.error).toBeInstanceOf(Error);
    const old = hook.result.current; hook.unmount();
    expect(await old.load()).toBeNull();
    expect(query).toHaveBeenCalledTimes(2); expect(errors.error).toHaveBeenCalledTimes(1);
  });
});
