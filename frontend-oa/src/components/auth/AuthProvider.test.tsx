import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import {
  PERMISSIONS_CHANGED_EVENT,
  PERMISSIONS_STALE_EVENT,
  notifyAuthResponseStatus,
} from '@/lib/authEvents';
import PermissionButton from '@/components/oa/PermissionButton';

const me = vi.fn();

vi.mock('@/lib/authApi', () => ({
  AuthApiError: class AuthApiError extends Error {
    constructor(message: string, readonly status: number) {
      super(message);
    }
  },
  authApi: {
    me: () => me(),
    logout: vi.fn().mockResolvedValue(undefined),
  },
}));

const authUser = (permissionVersion: number, permissions: string[]) => ({
  id: 7,
  name: '权限测试用户',
  email: 'permission@example.com',
  tenantId: 1,
  role: 'EMPLOYEE',
  roles: ['EMPLOYEE'],
  permissions,
  dataScopes: ['SELF'],
  permissionVersion,
});

function PermissionProbe() {
  const { user } = useAuth();
  const any = usePermission(['data:export', 'ai:execute'], 'ANY');
  const all = usePermission(['data:export', 'ai:execute'], 'ALL');
  return (
    <>
      <span data-testid="version">{user?.permissionVersion ?? 0}</span>
      <span data-testid="any">{String(any.allowed)}</span>
      <span data-testid="all">{String(all.allowed)}</span>
      <PermissionButton permission="data:export">导出</PermissionButton>
      <PermissionButton permission="ai:execute">AI</PermissionButton>
    </>
  );
}

describe('real-time permission snapshot', () => {
  beforeEach(() => {
    me.mockReset();
  });

  afterEach(() => cleanup());

  it('uses /api/auth/me permissions for ANY, ALL and permission buttons', async () => {
    me.mockResolvedValue(authUser(1, ['data:export']));
    render(<AuthProvider><PermissionProbe /></AuthProvider>);

    await waitFor(() => expect(screen.getByTestId('version').textContent).toBe('1'));
    expect(screen.getByTestId('any').textContent).toBe('true');
    expect(screen.getByTestId('all').textContent).toBe('false');
    expect(screen.getByRole('button', { name: /导\s*出/ }).hasAttribute('disabled')).toBe(false);
    expect(screen.getByRole('button', { name: 'AI' }).hasAttribute('disabled')).toBe(true);
  });

  it('refreshes the permission snapshot after 403, access changes and window focus', async () => {
    me
      .mockResolvedValueOnce(authUser(1, ['dashboard:read']))
      .mockResolvedValueOnce(authUser(2, ['dashboard:read', 'data:export']))
      .mockResolvedValueOnce(authUser(3, ['dashboard:read']))
      .mockResolvedValueOnce(authUser(4, ['dashboard:read', 'ai:execute']));
    render(<AuthProvider><PermissionProbe /></AuthProvider>);
    await waitFor(() => expect(screen.getByTestId('version').textContent).toBe('1'));

    notifyAuthResponseStatus(403);
    await waitFor(() => expect(screen.getByTestId('version').textContent).toBe('2'));

    window.dispatchEvent(new CustomEvent(PERMISSIONS_CHANGED_EVENT));
    await waitFor(() => expect(screen.getByTestId('version').textContent).toBe('3'));

    fireEvent.focus(window);
    await waitFor(() => expect(screen.getByTestId('version').textContent).toBe('4'));
    expect(me).toHaveBeenCalledTimes(4);
  });

  it('publishes a stale-permission event for forbidden responses', () => {
    const listener = vi.fn();
    window.addEventListener(PERMISSIONS_STALE_EVENT, listener);
    notifyAuthResponseStatus(403);
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener(PERMISSIONS_STALE_EVENT, listener);
  });
});
