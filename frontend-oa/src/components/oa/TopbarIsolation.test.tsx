import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { AuthUser } from '@/lib/authApi';
import type { NotificationItem } from '@/lib/notificationApi';
import type { DashboardExportResponse } from '@/lib/dashboardApi';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import i18n from '@/i18n';

const auth = vi.hoisted(() => ({ user: null as AuthUser | null, logout: vi.fn(), refresh: vi.fn() }));
const api = vi.hoisted(() => ({ listNotifications: vi.fn(), fetchUnreadCount: vi.fn(), markNotificationRead: vi.fn(), exportDashboard: vi.fn(), downloadDashboardExport: vi.fn() }));
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => auth }));
vi.mock('@/lib/notificationApi', () => ({ listNotifications: api.listNotifications, fetchUnreadCount: api.fetchUnreadCount, markNotificationRead: api.markNotificationRead }));
vi.mock('@/lib/dashboardApi', async original => {
  const real = await original<typeof import('@/lib/dashboardApi')>();
  return { ...real, exportDashboard: api.exportDashboard, downloadDashboardExport: api.downloadDashboardExport };
});
vi.mock('@/lib/nextCompat', () => ({ useRouter: () => router }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('@/components/profile/ProfileSettingsModal', () => ({ default: () => null }));
vi.mock('./HelpDrawer', () => ({ default: () => null }));
import Topbar from './Topbar';

const permissions = ['notification:read:self', 'route:messages', 'data:export'];
function user(id = 7, granted = permissions, version = 1): AuthUser { return { id, tenantId: 1, name: `Synthetic user ${id}`, email: `user${id}@example.invalid`, role: 'EMPLOYEE', roles: ['EMPLOYEE'], permissions: granted, dataScopes: ['SELF'], permissionVersion: version }; }
function item(id = 1, title = 'Synthetic notice A'): NotificationItem { return { id, title, content: 'Synthetic private content', type: 'SYSTEM', bizId: null, bizType: null, read: false, createdAt: '2026-11-02T09:00:00' }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function Tree({ pageId = 'dashboard' }: { pageId?: string }) { return <ConfigProvider locale={zhCN}><App><Topbar role="employee" pageId={pageId} pageTitle="Synthetic page" onOpenAppearance={vi.fn()} onOpenAi={vi.fn()} onToggleMenu={vi.fn()} /></App></ConfigProvider>; }
function replaceUser(next: AuthUser) { auth.user = next; bindAuthResponseIdentity(`${next.tenantId}:${next.id}`); }
async function openNotifications() { fireEvent.mouseEnter(screen.getByRole('button', { name: i18n.t('oa.topbar.notifications') })); }
async function openLogout() { fireEvent.mouseEnter(document.querySelector('.oa-profile-trigger')!); fireEvent.click(await screen.findByRole('menuitem', { name: /退出登录/ })); }
const exported: DashboardExportResponse = { filename: 'synthetic.csv', contentType: 'text/csv;charset=UTF-8', content: 'header\nsynthetic', rowCount: 1, generatedAt: '2026-11-02T09:00:00' };
beforeEach(() => {
  vi.resetAllMocks(); replaceUser(user()); api.listNotifications.mockResolvedValue({ records: [item()], total: 1, page: 1, size: 5 }); api.fetchUnreadCount.mockResolvedValue(1);
  api.markNotificationRead.mockResolvedValue(undefined);
});
afterEach(() => { cleanup(); vi.useRealTimers(); });

it('does not request or poll notifications without current read authority', async () => {
  vi.useFakeTimers(); replaceUser(user(7, [])); render(<Tree />);
  await act(async () => { await vi.advanceTimersByTimeAsync(31000); });
  expect(api.listNotifications).not.toHaveBeenCalled(); expect(api.fetchUnreadCount).not.toHaveBeenCalled();
  expect(document.querySelector('.oa-notify-trigger')).toBeNull();
});

it('clears old notices on replacement and ignores an earlier pending list', async () => {
  const late = deferred<{ records: NotificationItem[]; total: number; page: number; size: number }>();
  api.listNotifications.mockReturnValueOnce(late.promise).mockResolvedValue({ records: [item(2, 'Synthetic notice B')], total: 1, page: 1, size: 5 });
  const view = render(<Tree />); await waitFor(() => expect(api.listNotifications).toHaveBeenCalledTimes(1));
  replaceUser(user(8, permissions, 2)); view.rerender(<Tree />); await openNotifications(); await screen.findByText('Synthetic notice B');
  await act(async () => late.resolve({ records: [item()], total: 1, page: 1, size: 5 }));
  expect(screen.queryByText('Synthetic notice A')).toBeNull(); expect(screen.getByText('Synthetic notice B')).toBeTruthy();
});

it('removes cached notification content and the entry on live revocation', async () => {
  const view = render(<Tree />); await openNotifications(); await screen.findByText('Synthetic notice A');
  replaceUser(user(7, [], 2)); view.rerender(<Tree />);
  await waitFor(() => expect(screen.queryByText('Synthetic notice A')).toBeNull());
  expect(document.querySelector('.oa-notify-trigger')).toBeNull(); expect(api.listNotifications).toHaveBeenCalledTimes(1);
});

it('does not navigate or update a new account when an older mark-read finishes', async () => {
  const pending = deferred<void>(); api.markNotificationRead.mockReturnValueOnce(pending.promise);
  const view = render(<Tree />); await openNotifications(); fireEvent.click(await screen.findByText('Synthetic notice A'));
  await waitFor(() => expect(api.markNotificationRead).toHaveBeenCalledWith(1));
  api.listNotifications.mockResolvedValue({ records: [], total: 0, page: 1, size: 5 }); replaceUser(user(8, permissions, 2)); view.rerender(<Tree />);
  await act(async () => pending.resolve()); expect(router.push).not.toHaveBeenCalled();
});

it('marks an authorized notice read and follows its real messages route', async () => {
  render(<Tree />); await openNotifications(); fireEvent.click(await screen.findByText('Synthetic notice A'));
  await waitFor(() => expect(router.push).toHaveBeenCalledWith('/oa/messages')); expect(api.markNotificationRead).toHaveBeenCalledWith(1);
});

it('drops an export delivered after revocation without downloading or claiming success', async () => {
  const pending = deferred<DashboardExportResponse>(); api.exportDashboard.mockReturnValueOnce(pending.promise);
  const view = render(<Tree />); fireEvent.click(screen.getByRole('button', { name: '导出看板' }));
  await waitFor(() => expect(api.exportDashboard).toHaveBeenCalledTimes(1));
  replaceUser(user(7, ['route:messages'], 2)); view.rerender(<Tree />); await act(async () => pending.resolve(exported));
  expect(api.downloadDashboardExport).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
});

it('does not download a dashboard export after navigating out of its page', async () => {
  const pending = deferred<DashboardExportResponse>(); api.exportDashboard.mockReturnValueOnce(pending.promise);
  const view = render(<Tree />); fireEvent.click(screen.getByRole('button', { name: '导出看板' }));
  await waitFor(() => expect(api.exportDashboard).toHaveBeenCalledTimes(1));
  view.rerender(<Tree pageId="messages" />); await act(async () => pending.resolve(exported)); expect(api.downloadDashboardExport).not.toHaveBeenCalled();
});

it('downloads a current export once and prevents a duplicate click', async () => {
  const pending = deferred<DashboardExportResponse>(); api.exportDashboard.mockReturnValueOnce(pending.promise); render(<Tree />);
  const button = screen.getByRole('button', { name: '导出看板' }); fireEvent.click(button); fireEvent.click(button);
  await waitFor(() => expect(api.exportDashboard).toHaveBeenCalledTimes(1)); await act(async () => pending.resolve(exported));
  expect(api.downloadDashboardExport).toHaveBeenCalledWith(exported); expect(messages.success).toHaveBeenCalledTimes(1);
});

it('does not redirect a replacement account after an older logout callback', async () => {
  const pending = deferred<boolean>(); auth.logout.mockReturnValueOnce(pending.promise);
  const view = render(<Tree />); await openLogout(); await waitFor(() => expect(auth.logout).toHaveBeenCalledTimes(1));
  replaceUser(user(8, permissions, 2)); view.rerender(<Tree />); await act(async () => pending.resolve(true)); expect(router.replace).not.toHaveBeenCalled();
});

it('redirects only when the current session really remains signed out', async () => {
  auth.logout.mockImplementation(async () => { bindAuthResponseIdentity(null); return true; }); render(<Tree />); await openLogout();
  await waitFor(() => expect(router.replace).toHaveBeenCalledWith('/oa/auth'));
});
