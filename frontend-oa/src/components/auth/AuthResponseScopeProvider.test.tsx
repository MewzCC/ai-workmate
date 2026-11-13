import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthProvider';
import type { AuthUser } from '@/lib/authApi';
import { captureAuthResponseScope, notifyAuthResponseStatus } from '@/lib/authEvents';

const api = vi.hoisted(() => ({ me: vi.fn(), logout: vi.fn() }));
vi.mock('@/lib/authApi', () => ({ authApi: api, AuthApiError: class AuthApiError extends Error { constructor(message: string, readonly status: number) { super(message); } } }));
let controls: ReturnType<typeof useAuth>;
function user(id: number, permissionVersion = 1): AuthUser { return { id, tenantId: 1, name: `Synthetic ${id}`, email: `user${id}@example.invalid`, role: 'EMPLOYEE', roles: ['EMPLOYEE'], permissions: [], dataScopes: ['SELF'], permissionVersion }; }
function Probe() { controls = useAuth(); return <output data-testid="identity">{controls.user ? `${controls.user.id}:${controls.user.permissionVersion}` : 'none'}</output>; }
function Tree() { return <AuthProvider><Probe /></AuthProvider>; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { vi.resetAllMocks(); api.me.mockResolvedValue(user(7)); api.logout.mockResolvedValue(undefined); });
afterEach(cleanup);

it('ignores old authentication signals but accepts a new user permission refresh', async () => {
  render(<Tree />); await waitFor(() => expect(screen.getByTestId('identity').textContent).toBe('7:1'));
  const old = captureAuthResponseScope(); act(() => controls.setUser(user(8)));
  act(() => { notifyAuthResponseStatus(401, old); notifyAuthResponseStatus(403, old); });
  expect(screen.getByTestId('identity').textContent).toBe('8:1'); expect(api.me).toHaveBeenCalledTimes(1);
  api.me.mockResolvedValue(user(8, 2));
  await act(async () => notifyAuthResponseStatus(403, captureAuthResponseScope()));
  expect(screen.getByTestId('identity').textContent).toBe('8:2'); expect(api.me).toHaveBeenCalledTimes(2);
});

it('does not let an old identity refresh clear or replace the new in-flight refresh', async () => {
  render(<Tree />); await waitFor(() => expect(screen.getByTestId('identity').textContent).toBe('7:1'));
  const old = deferred<AuthUser>(), next = deferred<AuthUser>();
  api.me.mockReturnValueOnce(old.promise).mockReturnValueOnce(next.promise);
  let first!: Promise<AuthUser | null>, second!: Promise<AuthUser | null>;
  act(() => { first = controls.refresh(); });
  act(() => controls.setUser(user(8)));
  act(() => { second = controls.refresh(); });
  await act(async () => { old.resolve(user(7, 90)); expect(await first).toBeNull(); });
  expect(screen.getByTestId('identity').textContent).toBe('8:1');
  expect(controls.refresh()).toBe(second); expect(api.me).toHaveBeenCalledTimes(3);
  await act(async () => { next.resolve(user(8, 2)); await second; });
  expect(screen.getByTestId('identity').textContent).toBe('8:2');
});

it('does not rebind a disposed provider when its old me request finally returns', async () => {
  const old = deferred<AuthUser>(); api.me.mockReturnValueOnce(old.promise);
  const first = render(<Tree />); await waitFor(() => expect(api.me).toHaveBeenCalledTimes(1)); first.unmount();
  api.me.mockResolvedValue(user(8)); render(<Tree />);
  await waitFor(() => expect(screen.getByTestId('identity').textContent).toBe('8:1'));
  const scope = captureAuthResponseScope(); await act(async () => old.resolve(user(7)));
  expect(captureAuthResponseScope()).toBe(scope); expect(screen.getByTestId('identity').textContent).toBe('8:1');
});

it('does not clear a replacement user when a previous logout callback completes', async () => {
  render(<Tree />); await waitFor(() => expect(screen.getByTestId('identity').textContent).toBe('7:1'));
  const pending = deferred<void>(); api.logout.mockReturnValueOnce(pending.promise);
  let logout!: Promise<boolean>; act(() => { logout = controls.logout(); });
  act(() => controls.setUser(user(8)));
  await act(async () => { pending.resolve(); await logout; });
  expect(screen.getByTestId('identity').textContent).toBe('8:1');
});

it('keeps a same-identity refresh in the same response scope and still expires the session', async () => {
  render(<Tree />); await waitFor(() => expect(screen.getByTestId('identity').textContent).toBe('7:1'));
  const scope = captureAuthResponseScope(); api.me.mockResolvedValue(user(7, 2));
  await act(async () => { await controls.refresh(); }); expect(captureAuthResponseScope()).toBe(scope);
  act(() => notifyAuthResponseStatus(401, scope)); expect(screen.getByTestId('identity').textContent).toBe('none');
});

it('restores the live provider after Strict Mode effect disposal', async () => {
  render(<StrictMode><Tree /></StrictMode>);
  await waitFor(() => expect(screen.getByTestId('identity').textContent).toBe('7:1'));
  expect(controls.user?.id).toBe(7);
});
