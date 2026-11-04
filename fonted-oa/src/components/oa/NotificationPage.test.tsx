import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '@/components/auth/AuthProvider';
import { PageAgentContextProvider } from './PageAgentContext';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import type { NotificationItem, NotificationPage as NotificationResult } from '@/lib/notificationApi';

const auth = vi.hoisted(() => ({ me: vi.fn() }));
const api = vi.hoisted(() => ({ listNotifications: vi.fn(), fetchUnreadCount: vi.fn(), getNotification: vi.fn(), markAllNotificationsRead: vi.fn(), markNotificationRead: vi.fn() }));
const notifications = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
const ui = vi.hoisted(() => { const handlers: PageUiCommandHandlers = {}; return { handlers }; });
vi.mock('@/lib/authApi', async load => {
  const original = await load<typeof import('@/lib/authApi')>();
  return { ...original, authApi: { ...original.authApi, me: auth.me } };
});
vi.mock('@/lib/notificationApi', () => api);
vi.mock('@/lib/antdMessage', () => ({ message: notifications }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import NotificationPage from './NotificationPage';

function user(version = 1, allowed = true) { return { id: 7, tenantId: 1, name: 'Test', email: 'test@example.invalid', role: 'EMPLOYEE', roles: ['EMPLOYEE'],
  permissions: allowed ? ['route:messages', 'notification:read:self'] : ['route:messages'], dataScopes: ['SELF'], permissionVersion: version }; }
function record(id: number, title: string): NotificationItem { return { id, title, content: `${title}正文`, type: 'SYSTEM', bizType: null, bizId: null, read: false, createdAt: '2026-10-28T10:00:00' }; }
function result(item: NotificationItem, page = 1): NotificationResult { return { records: [item], total: 40, page, size: 20 }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function Snapshot() { return <output aria-label="notification-auth-version">{useAuth().user?.permissionVersion}</output>; }
function renderPage() { return render(<AuthProvider><App><PageAgentContextProvider pageId="messages"><NotificationPage /><Snapshot /></PageAgentContextProvider></App></AuthProvider>); }
beforeEach(() => {
  vi.clearAllMocks(); auth.me.mockResolvedValue(user());
  api.listNotifications.mockResolvedValue(result(record(1, '原始通知')));
  api.fetchUnreadCount.mockResolvedValue(1);
  api.getNotification.mockResolvedValue(record(1, '原始通知'));
});
afterEach(cleanup);

it('clears cached rows and the private detail on revocation, ignores a late read, and reloads after restoration', async () => {
  renderPage();
  await screen.findByText('原始通知');
  await act(async () => { await ui.handlers['ui.openDetail']?.({ pageId: 'messages', code: 'ui.openDetail', payload: { recordId: 1 } }); });
  expect(screen.getByRole('dialog')).toBeTruthy();
  const late = deferred<NotificationResult>();
  api.listNotifications.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  await waitFor(() => expect(api.listNotifications).toHaveBeenCalledTimes(2));
  auth.me.mockResolvedValue(user(2, false));
  fireEvent.focus(window);
  await waitFor(() => expect(screen.getByLabelText('notification-auth-version').textContent).toBe('2'));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(screen.queryByText('原始通知')).toBeNull();
  expect(screen.queryByText('原始通知正文')).toBeNull();
  await act(async () => late.resolve(result(record(9, '迟到的旧通知'))));
  expect(screen.queryByText('迟到的旧通知')).toBeNull();
  expect(api.listNotifications).toHaveBeenCalledTimes(2);
  api.listNotifications.mockResolvedValue(result(record(2, '重新授权后通知')));
  auth.me.mockResolvedValue(user(3));
  fireEvent.focus(window);
  await screen.findByText('重新授权后通知');
  expect(screen.queryByText('原始通知')).toBeNull();
});

it('clears previous rows when a refresh fails instead of presenting stale data as current', async () => {
  renderPage(); await screen.findByText('原始通知');
  api.listNotifications.mockRejectedValueOnce(new Error('Synthetic read failure'));
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  await waitFor(() => expect(screen.queryByText('原始通知')).toBeNull());
  expect(notifications.error).toHaveBeenCalled();
});

it('rejects late page results and does not start a captured old-page refresh after navigation', async () => {
  renderPage(); await screen.findByText('原始通知');
  const oldRefresh = ui.handlers['ui.refreshPage'];
  const late = deferred<NotificationResult>();
  api.listNotifications.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByRole('button', { name: '刷新' }));
  await waitFor(() => expect(api.listNotifications).toHaveBeenCalledTimes(2));
  api.listNotifications.mockResolvedValue(result(record(2, '第二页通知'), 2));
  fireEvent.click(screen.getByTitle('2'));
  await screen.findByText('第二页通知');
  await act(async () => late.resolve(result(record(9, '迟到的第一页'))));
  expect(screen.queryByText('迟到的第一页')).toBeNull();
  const calls = api.listNotifications.mock.calls.length;
  await expect(oldRefresh?.({ pageId: 'messages', code: 'ui.refreshPage', payload: {} })).rejects.toThrow();
  expect(api.listNotifications).toHaveBeenCalledTimes(calls);
  expect(screen.getByText('第二页通知')).toBeTruthy();
});

it('opens an owned notification outside the current page without marking it read', async () => {
  renderPage(); await screen.findByText('原始通知');
  api.getNotification.mockResolvedValue(record(42, '其他分页通知'));
  await act(async () => { await ui.handlers['ui.openDetail']?.({ pageId: 'messages', code: 'ui.openDetail', payload: { recordId: 42 } }); });
  expect(screen.getByRole('dialog').textContent).toContain('其他分页通知正文');
  expect(api.getNotification).toHaveBeenCalledWith(42);
  expect(api.markNotificationRead).not.toHaveBeenCalled();
});

it('uses numeric paging filters and rejects unknown fields or invalid sizes before loading', async () => {
  renderPage(); await screen.findByText('原始通知');
  await act(async () => { await ui.handlers['ui.applyFilter']?.({ pageId: 'messages', code: 'ui.applyFilter', payload: { filters: { page: 2, size: 50 } } }); });
  await waitFor(() => expect(api.listNotifications).toHaveBeenLastCalledWith(2, 50));
  const invalid: Record<string, number>[] = [{ page: 0 }, { size: 101 }, { tenantId: 3 }];
  for (const filters of invalid) {
    await expect(async () => ui.handlers['ui.applyFilter']?.({ pageId: 'messages', code: 'ui.applyFilter', payload: { filters } })).rejects.toThrow();
  }
});

it('rejects late or mismatched details and does not report a failed refresh as applied', async () => {
  renderPage(); await screen.findByText('原始通知');
  api.getNotification.mockResolvedValue(record(9, '错误编号通知'));
  await expect(ui.handlers['ui.openDetail']?.({ pageId: 'messages', code: 'ui.openDetail', payload: { recordId: 42 } })).rejects.toThrow();
  expect(screen.queryByRole('dialog')).toBeNull();
  api.listNotifications.mockRejectedValueOnce(new Error('Synthetic refresh failure'));
  await act(async () => { await expect(ui.handlers['ui.refreshPage']?.({ pageId: 'messages', code: 'ui.refreshPage', payload: {} })).rejects.toThrow(); });
  expect(await screen.findByText('加载失败')).toBeTruthy();
});

it('prevents duplicate mark-read clicks and suppresses success callbacks after revocation', async () => {
  const pending = deferred<number>(); api.markAllNotificationsRead.mockReturnValue(pending.promise);
  renderPage(); await screen.findByText('原始通知');
  const readAll = screen.getByRole('button', { name: '全部已读' });
  fireEvent.click(readAll); fireEvent.click(readAll);
  expect(api.markAllNotificationsRead).toHaveBeenCalledTimes(1);
  auth.me.mockResolvedValue(user(2, false)); fireEvent.focus(window);
  await waitFor(() => expect(screen.getByLabelText('notification-auth-version').textContent).toBe('2'));
  await act(async () => { pending.resolve(1); });
  expect(notifications.success).not.toHaveBeenCalled();
});
