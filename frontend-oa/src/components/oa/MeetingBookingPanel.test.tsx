import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import dayjs from 'dayjs';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { MeetingBooking, MeetingRoom } from '@/lib/adminAssetsApi';

const api = vi.hoisted(() => ({ listMyMeetingBookings: vi.fn(), listAdminMeetingBookings: vi.fn(),
  createMeetingBooking: vi.fn(), cancelMeetingBooking: vi.fn() }));
const auth = vi.hoisted(() => ({ version: 1, permissions: ['meeting:read:self'] }));
const messages = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissionVersion: auth.version, permissions: auth.permissions,
} }) }));
vi.mock('@/lib/adminAssetsApi', () => ({ adminAssetsApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
import MeetingBookingPanel from './MeetingBookingPanel';

function booking(id: number, start = dayjs().hour(9), end = start.add(1, 'hour')): MeetingBooking {
  return { id, roomId: 1, roomName: 'Room', organizerUserId: 7, title: `预约-${id}`,
    startAt: start.format('YYYY-MM-DDTHH:mm:ss'), endAt: end.format('YYYY-MM-DDTHH:mm:ss'),
    attendeeCount: 1, status: 'BOOKED', version: 2, createdAt: '', updatedAt: '', canCancel: true };
}
function result(records: MeetingBooking[], total = records.length) { return { records, total, page: 1, size: 100 }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
const view = (canManage = false) => <App><MeetingBookingPanel rooms={[]} canManage={canManage} /></App>;
beforeEach(() => {
  vi.resetAllMocks(); auth.version = 1; auth.permissions = ['meeting:read:self'];
  api.listMyMeetingBookings.mockResolvedValue(result([])); api.listAdminMeetingBookings.mockResolvedValue(result([]));
});
afterEach(cleanup);

it('loads every page within the displayed calendar range rather than truncating the first 100 bookings', async () => {
  const records = Array.from({ length: 205 }, (_, index) => booking(index + 1));
  api.listMyMeetingBookings.mockImplementation(async ({ page, size }: { page: number; size: number }) =>
    result(records.slice((page - 1) * size, page * size), records.length));
  render(view());
  await screen.findByText('预约-205');
  expect(api.listMyMeetingBookings).toHaveBeenCalledTimes(3);
  expect(api.listMyMeetingBookings).toHaveBeenLastCalledWith(expect.objectContaining({
    page: 3, size: 100, from: dayjs().startOf('month').subtract(7, 'day').format('YYYY-MM-DDTHH:mm:ss'),
    to: dayjs().endOf('month').add(7, 'day').format('YYYY-MM-DDTHH:mm:ss'),
  }));
  expect(api.listAdminMeetingBookings).not.toHaveBeenCalled();
});

it('projects overnight bookings onto each covered day but excludes the midnight end boundary', async () => {
  const day = dayjs().startOf('month').add(12, 'day');
  api.listMyMeetingBookings.mockResolvedValue(result([booking(9, day.hour(23), day.add(2, 'day'))]));
  render(view());
  await waitFor(() => expect(document.querySelectorAll('.oa-meeting-calendar-event')).toHaveLength(2));
  const dates = [...document.querySelectorAll('.oa-meeting-calendar-event')].map(element => element.closest('td')?.getAttribute('title'));
  expect(dates).toEqual([day.format('YYYY-MM-DD'), day.add(1, 'day').format('YYYY-MM-DD')]);
});

it('loads an administrator calendar without requesting ungranted self-read authority', async () => {
  auth.permissions = ['meeting:write'];
  api.listAdminMeetingBookings.mockResolvedValue(result([booking(11)]));
  render(view(true));
  await screen.findByText('预约-11');
  expect(api.listMyMeetingBookings).not.toHaveBeenCalled();
  expect(messages.error).not.toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: '预约会议室' })).toBeNull();
});

it('hides booking and cancellation actions without their current permissions despite stale row flags', async () => {
  api.listMyMeetingBookings.mockResolvedValue(result([booking(12)]));
  render(view()); await screen.findByText('预约-12');
  expect(screen.queryByRole('button', { name: '预约会议室' })).toBeNull();
  expect(screen.queryByRole('button', { name: '预约当天会议室' })).toBeNull();
  fireEvent.click(screen.getByText('列表视图'));
  expect(screen.queryByRole('button', { name: '取消预约' })).toBeNull();
});

it('discards a late previous-month response after switching the displayed calendar month', async () => {
  const late = deferred<ReturnType<typeof result>>();
  api.listMyMeetingBookings.mockReturnValueOnce(late.promise);
  render(view());
  await waitFor(() => expect(api.listMyMeetingBookings).toHaveBeenCalledTimes(1));
  api.listMyMeetingBookings.mockResolvedValue(result([booking(22, dayjs().add(1, 'month').startOf('month').hour(9))]));
  fireEvent.click(screen.getByRole('button', { name: '下个月' }));
  await waitFor(() => expect(document.querySelectorAll('.oa-meeting-calendar-event').length).toBeGreaterThan(0));
  await act(async () => late.resolve(result([booking(23)])));
  fireEvent.click(screen.getByText('列表视图'));
  expect(screen.queryByText('预约-23')).toBeNull();
  expect(screen.getByText('预约-22')).toBeTruthy();
});

it('does not show partial data when a subsequent page fails', async () => {
  api.listMyMeetingBookings.mockResolvedValueOnce(result(Array.from({ length: 100 }, (_, index) => booking(index + 1)), 101))
    .mockRejectedValueOnce(new Error('Synthetic page failure'));
  render(view());
  await waitFor(() => expect(messages.error).toHaveBeenCalled());
  expect(document.querySelectorAll('.oa-meeting-calendar-event')).toHaveLength(0);
  expect(screen.queryByText('预约-1')).toBeNull();
});

it('ignores an old cancellation completion after permissions change', async () => {
  auth.permissions = ['meeting:read:self', 'meeting:cancel'];
  api.listMyMeetingBookings.mockResolvedValue(result([booking(31)]));
  const pending = deferred<MeetingBooking>(); api.cancelMeetingBooking.mockReturnValue(pending.promise);
  const page = render(view()); await screen.findByText('预约-31');
  fireEvent.click(screen.getByText('列表视图'));
  fireEvent.click(screen.getByRole('button', { name: '取消预约' }));
  fireEvent.click(await screen.findByRole('button', { name: /^(OK|确\s*定)$/ }));
  await waitFor(() => expect(api.cancelMeetingBooking).toHaveBeenCalledWith(31, { version: 2 }));
  auth.version++; auth.permissions = ['meeting:read:self']; page.rerender(view());
  await waitFor(() => expect(api.listMyMeetingBookings).toHaveBeenCalledTimes(2));
  await act(async () => pending.resolve({ ...booking(31), status: 'CANCELLED' }));
  expect(api.listMyMeetingBookings).toHaveBeenCalledTimes(2);
  expect(messages.success).not.toHaveBeenCalled();
});

it('locks a submitted booking draft but allows closing and keeps the replacement draft on late completion', async () => {
  auth.permissions = ['meeting:read:self', 'meeting:book'];
  const room: MeetingRoom = { id: 1, code: 'R1', name: 'Synthetic room', capacity: 8, status: 'OPEN', version: 1, createdAt: '', updatedAt: '', canEdit: false, canDelete: false };
  const pending = deferred<MeetingBooking>(); api.createMeetingBooking.mockReturnValueOnce(pending.promise);
  render(<App><MeetingBookingPanel rooms={[room]} canManage={false} /></App>);
  await waitFor(() => expect(api.listMyMeetingBookings).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: '预约当天会议室' }));
  let form = await screen.findByRole('dialog');
  fireEvent.mouseDown(within(form).getByLabelText('会议室'));
  fireEvent.click(await screen.findByText('Synthetic room · - · 8人'));
  fireEvent.change(within(form).getByLabelText('会议主题'), { target: { value: 'Synthetic meeting' } });
  const submit = within(form).getByRole('button', { name: /^(OK|确\s*定)$/ });
  act(() => { fireEvent.click(submit); fireEvent.click(submit); });
  await waitFor(() => expect(api.createMeetingBooking).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(within(form).getByLabelText('会议主题')).toBeDisabled());
  fireEvent.click(await within(form).findByRole('button', { name: /^关\s*闭$/ }));
  fireEvent.click(screen.getByRole('button', { name: '预约当天会议室' })); form = await screen.findByRole('dialog');
  fireEvent.change(within(form).getByLabelText('会议主题'), { target: { value: 'Replacement meeting draft' } });
  await act(async () => pending.resolve(booking(99)));
  expect((within(await screen.findByRole('dialog')).getByLabelText('会议主题') as HTMLInputElement).value).toBe('Replacement meeting draft');
  expect(messages.success).not.toHaveBeenCalled();
});

it('keeps validation errors inline and releases the booking lock without submitting', async () => {
  auth.permissions = ['meeting:read:self', 'meeting:book'];
  render(view());
  await waitFor(() => expect(api.listMyMeetingBookings).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: '预约当天会议室' }));
  const form = await screen.findByRole('dialog');
  const submit = within(form).getByRole('button', { name: /^(OK|确\s*定)$/ });
  fireEvent.click(submit);
  await waitFor(() => expect(form.querySelectorAll('.ant-form-item-explain-error').length).toBeGreaterThan(0));
  await waitFor(() => expect(submit.classList.contains('ant-btn-loading')).toBe(false));
  expect(api.createMeetingBooking).not.toHaveBeenCalled();
  expect(messages.error).not.toHaveBeenCalled();
});
