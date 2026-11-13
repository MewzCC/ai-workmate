import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { MeetingRoom } from '@/lib/adminAssetsApi';
import { AuthProvider, useAuth } from '@/components/auth/AuthProvider';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ listMeetingRooms: vi.fn(), updateMeetingRoom: vi.fn() }));
const auth = vi.hoisted(() => ({ me: vi.fn() }));
vi.mock('@/lib/authApi', async (load) => {
  const original = await load<typeof import('@/lib/authApi')>();
  return { ...original, authApi: { ...original.authApi, me: auth.me } };
});
vi.mock('@/lib/adminAssetsApi', async (load) => ({
  ...await load<typeof import('@/lib/adminAssetsApi')>(), adminAssetsApi: api,
}));
// Reservation scheduling is independent of the room metadata editing contract.
vi.mock('./MeetingBookingPanel', () => ({ default: () => null }));
import MeetingRoomPage from './MeetingRoomPage';

const room: MeetingRoom = {
  id: 19, code: 'MR-TEST', name: 'Test room', capacity: 8, status: 'OPEN', version: 7,
  createdAt: '2026-10-20T09:00:00', updatedAt: '2026-10-20T09:00:00', canEdit: true, canDelete: false,
};
beforeEach(() => {
  vi.clearAllMocks();
  auth.me.mockResolvedValue(authUser(['meeting:write'], 1));
  api.listMeetingRooms.mockResolvedValue({ records: [room], total: 1, page: 1, size: 10 });
  api.updateMeetingRoom.mockResolvedValue({ ...room, version: 8 });
});
afterEach(cleanup);

function authUser(permissions: string[], permissionVersion: number) {
  return { id: 7, tenantId: 1, name: 'Test user', email: 'test@example.invalid', role: 'EMPLOYEE',
    roles: ['EMPLOYEE'], permissions, dataScopes: ['SELF'], permissionVersion };
}
function AuthVersion() {
  return <output aria-label="auth-version">{useAuth().user?.permissionVersion ?? 0}</output>;
}
function AuthenticatedApp({ children }: { children: React.ReactNode }) {
  return <AuthProvider><App>{children}<AuthVersion /></App></AuthProvider>;
}

function ContextSnapshot() {
  return <output aria-label="agent-context">{JSON.stringify(usePageAgentContextSnapshot())}</output>;
}

it('publishes only the currently edited room and removes the selection when editing closes', async () => {
  render(<AuthenticatedApp><PageAgentContextProvider pageId="meeting-room"><MeetingRoomPage /><ContextSnapshot /></PageAgentContextProvider></AuthenticatedApp>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('agent-context').textContent || '{}'))
    .toMatchObject({ roomId: 19, version: 7 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Cancel|取\s*消/ }));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('agent-context').textContent || '{}'))
    .not.toHaveProperty('roomId'));
});

it('submits the version of the room that was opened for editing', async () => {
  render(<AuthenticatedApp><MeetingRoomPage /></AuthenticatedApp>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('会议室名称'), { target: { value: 'Updated room' } });
  fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateMeetingRoom).toHaveBeenCalledWith(19,
    expect.objectContaining({ name: 'Updated room', version: 7 })));
});

it('keeps the edit form and unsaved text when the server rejects a stale version', async () => {
  api.updateMeetingRoom.mockRejectedValue(new Error('VERSION_CONFLICT'));
  render(<AuthenticatedApp><MeetingRoomPage /></AuthenticatedApp>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('会议室名称'), { target: { value: 'Unsaved edit' } });
  fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateMeetingRoom).toHaveBeenCalledTimes(1));
  expect(within(screen.getByRole('dialog')).getByLabelText<HTMLInputElement>('会议室名称').value).toBe('Unsaved edit');
});

it('lets a real writer create the first room even when the list is empty', async () => {
  api.listMeetingRooms.mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });
  render(<AuthenticatedApp><MeetingRoomPage /></AuthenticatedApp>);
  fireEvent.click(await screen.findByRole('button', { name: '新增会议室' }));
  expect(within(screen.getByRole('dialog')).getByLabelText('会议室名称')).toBeTruthy();
});

it('does not infer management permission from stale row flags', async () => {
  auth.me.mockResolvedValue(authUser([], 2));
  render(<AuthenticatedApp><MeetingRoomPage /></AuthenticatedApp>);
  await screen.findByText('Test room');
  await waitFor(() => expect(screen.getByLabelText('auth-version').textContent).toBe('2'));
  expect(screen.queryByRole('button', { name: '新增会议室' })).toBeNull();
  expect(screen.queryByRole('button', { name: '编辑' })).toBeNull();
});

it('blocks saving an open editor after write permission is revoked', async () => {
  render(<AuthenticatedApp><MeetingRoomPage /></AuthenticatedApp>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  auth.me.mockResolvedValue(authUser([], 2));
  fireEvent.focus(window);
  await waitFor(() => expect(screen.getByLabelText('auth-version').textContent).toBe('2'));
  const save = within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ });
  expect(save.hasAttribute('disabled')).toBe(true);
  fireEvent.click(save);
  expect(api.updateMeetingRoom).not.toHaveBeenCalled();
});

it('reloads resource action flags when a new permission snapshot grants editing', async () => {
  auth.me.mockResolvedValue(authUser([], 1));
  api.listMeetingRooms.mockResolvedValue({ records: [{ ...room, canEdit: false }], total: 1, page: 1, size: 10 });
  render(<AuthenticatedApp><MeetingRoomPage /></AuthenticatedApp>);
  await screen.findByText('Test room');
  expect(screen.queryByRole('button', { name: '编辑' })).toBeNull();
  api.listMeetingRooms.mockResolvedValue({ records: [room], total: 1, page: 1, size: 10 });
  auth.me.mockResolvedValue(authUser(['meeting:write'], 2));
  fireEvent.focus(window);
  expect(await screen.findByRole('button', { name: '编辑' })).toBeTruthy();
});
