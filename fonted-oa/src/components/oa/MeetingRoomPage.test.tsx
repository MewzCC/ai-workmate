import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { MeetingRoom } from '@/lib/adminAssetsApi';

const api = vi.hoisted(() => ({ listMeetingRooms: vi.fn(), updateMeetingRoom: vi.fn() }));
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
  api.listMeetingRooms.mockResolvedValue({ records: [room], total: 1, page: 1, size: 10 });
  api.updateMeetingRoom.mockResolvedValue({ ...room, version: 8 });
});
afterEach(cleanup);

it('submits the version of the room that was opened for editing', async () => {
  render(<App><MeetingRoomPage /></App>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('会议室名称'), { target: { value: 'Updated room' } });
  fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateMeetingRoom).toHaveBeenCalledWith(19,
    expect.objectContaining({ name: 'Updated room', version: 7 })));
});

it('keeps the edit form and unsaved text when the server rejects a stale version', async () => {
  api.updateMeetingRoom.mockRejectedValue(new Error('VERSION_CONFLICT'));
  render(<App><MeetingRoomPage /></App>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('会议室名称'), { target: { value: 'Unsaved edit' } });
  fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateMeetingRoom).toHaveBeenCalledTimes(1));
  expect(within(screen.getByRole('dialog')).getByLabelText<HTMLInputElement>('会议室名称').value).toBe('Unsaved edit');
});
