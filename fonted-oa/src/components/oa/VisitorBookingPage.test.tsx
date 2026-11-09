import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const permissions = ['route:visitor-booking', 'visitor:read:self', 'visitor:create', 'approval:act'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[] } }));
const api = vi.hoisted(() => ({ listMyVisitorBookings: vi.fn(), listPendingVisitorBookings: vi.fn(),
  visitorHostOptions: vi.fn(), submitVisitorBooking: vi.fn(), approveVisitorBooking: vi.fn(), getVisitorBooking: vi.fn() }));
const publishedContext = vi.hoisted(() => vi.fn());
const messages = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/adminAssetsApi', () => ({ adminAssetsApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('./PageAgentContext', async () => ({
  ...await vi.importActual<typeof import('./PageAgentContext')>('./PageAgentContext'), usePublishPageAgentContext: publishedContext,
}));
import VisitorBookingPage from './VisitorBookingPage';

function view(path = '/oa/visitor-booking') { return <MemoryRouter initialEntries={[path]}><ConfigProvider locale={zhCN}><App><VisitorBookingPage /></App></ConfigProvider></MemoryRouter>; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
async function open() {
  fireEvent.click(screen.getByRole('button', { name: '新增预约' }));
  return screen.findByRole('dialog');
}
async function fill(dialog: HTMLElement) {
  fireEvent.change(within(dialog).getByLabelText('访客姓名'), { target: { value: 'Synthetic visitor' } });
  fireEvent.change(within(dialog).getByLabelText('来访事由'), { target: { value: 'Synthetic purpose' } });
  fireEvent.mouseDown(within(dialog).getByLabelText('接待人'));
  fireEvent.click(await screen.findByText('Synthetic host'));
  fireEvent.mouseDown(within(dialog).getByLabelText('预计到访时间'));
  fireEvent.click(within(dialog).getByLabelText('预计到访时间'));
  fireEvent.click(await screen.findByText('此刻'));
}
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions };
  api.listMyVisitorBookings.mockResolvedValue({ records: [], total: 0 });
  api.listPendingVisitorBookings.mockResolvedValue({ records: [], total: 0 });
  api.visitorHostOptions.mockResolvedValue({ records: [{ id: 9, name: 'Synthetic host' }], total: 1 });
});
afterEach(cleanup);

it('loads real host choices only after opening a permitted creation form', async () => {
  render(view());
  await waitFor(() => expect(api.listMyVisitorBookings).toHaveBeenCalledTimes(1));
  expect(api.visitorHostOptions).not.toHaveBeenCalled();
  const dialog = await open();
  expect(within(dialog).getByLabelText('接待人')).toHaveAttribute('role', 'combobox');
  fireEvent.mouseDown(within(dialog).getByLabelText('接待人'));
  await screen.findByText('Synthetic host');
  expect(api.visitorHostOptions).toHaveBeenCalledWith({ keyword: undefined, page: 1, size: 20 });
});

it('keeps invalid fields inline, releases the submission lock and sends no write', async () => {
  render(view()); const dialog = await open();
  fireEvent.click(within(dialog).getByRole('button', { name: '提 交' }));
  await waitFor(() => expect(dialog.querySelectorAll('.ant-form-item-explain-error').length).toBeGreaterThan(0));
  await waitFor(() => expect(within(dialog).getByRole('button', { name: '提 交' })).not.toHaveClass('ant-btn-loading'));
  expect(api.submitVisitorBooking).not.toHaveBeenCalled();
  expect(messages.error).not.toHaveBeenCalled();
});

it('submits the selected host once and keeps a replacement draft when the old request completes', async () => {
  const late = deferred<object>(); api.submitVisitorBooking.mockReturnValueOnce(late.promise);
  render(view()); const dialog = await open(); await fill(dialog);
  const submit = within(dialog).getByRole('button', { name: '提 交' });
  act(() => { fireEvent.click(submit); fireEvent.click(submit); });
  await waitFor(() => expect(api.submitVisitorBooking).toHaveBeenCalledTimes(1));
  expect(api.submitVisitorBooking).toHaveBeenCalledWith(expect.objectContaining({
    visitorName: 'Synthetic visitor', purpose: 'Synthetic purpose', hostUserId: 9, partySize: 1,
    expectedVisitAt: expect.stringMatching(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/),
  }));
  expect(within(dialog).getByLabelText('访客姓名')).toBeDisabled();
  expect(within(dialog).getByLabelText('接待人')).toBeDisabled();
  fireEvent.click(within(dialog).getByRole('button', { name: '关 闭' }));
  const replacement = await open();
  fireEvent.change(within(replacement).getByLabelText('访客姓名'), { target: { value: 'Replacement draft' } });
  await act(async () => { late.resolve({}); });
  expect(within(await screen.findByRole('dialog')).getByLabelText('访客姓名')).toHaveValue('Replacement draft');
  expect(messages.success).not.toHaveBeenCalled();
  await waitFor(() => expect(api.listMyVisitorBookings).toHaveBeenCalledTimes(2));
});

it('closes private forms and suppresses old write callbacks after authority is revoked', async () => {
  const late = deferred<object>(); api.submitVisitorBooking.mockReturnValueOnce(late.promise);
  const page = render(view()); const dialog = await open(); await fill(dialog);
  fireEvent.click(within(dialog).getByRole('button', { name: '提 交' }));
  await waitFor(() => expect(api.submitVisitorBooking).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; page.rerender(view());
  expect(screen.queryByRole('button', { name: '新增预约' })).toBeNull();
  expect(screen.queryByRole('tab', { name: '待我审批' })).toBeNull();
  await act(async () => { late.resolve({}); });
  expect(messages.success).not.toHaveBeenCalled();
  expect(api.listMyVisitorBookings).toHaveBeenCalledTimes(1);
});

const assignedVisitor = { id: 9, visitorName: 'Assigned visitor', applicantName: 'Applicant', purpose: 'Review',
  expectedVisitAt: '2026-11-10T10:00:00', status: 'PENDING', version: 5, taskId: 10001, taskVersion: 2, canDecide: true };

it('publishes and submits the actual task version separately from the booking version', async () => {
  api.listPendingVisitorBookings.mockResolvedValue({ records: [assignedVisitor], total: 1 });
  api.approveVisitorBooking.mockResolvedValue({});
  render(view());
  fireEvent.click(screen.getByRole('tab', { name: '待我审批' }));
  fireEvent.click(await screen.findByRole('button', { name: '审批' }));
  const dialog = await screen.findByRole('dialog');
  expect(publishedContext).toHaveBeenLastCalledWith(expect.objectContaining({ bookingId: 9, taskId: 10001, taskVersion: 2 }));
  fireEvent.click(within(dialog).getByRole('button', { name: '通 过' }));
  await waitFor(() => expect(api.approveVisitorBooking).toHaveBeenCalledWith(10001, { version: 2, comment: undefined }));
});

it('does not replace a missing task version with zero', async () => {
  api.listPendingVisitorBookings.mockResolvedValue({ records: [{ ...assignedVisitor, taskVersion: null }], total: 1 });
  render(view());
  fireEvent.click(screen.getByRole('tab', { name: '待我审批' }));
  expect(await screen.findByRole('button', { name: '审批' })).toBeDisabled();
  expect(api.approveVisitorBooking).not.toHaveBeenCalled();
});

it('opens the actual receipt booking through the authenticated detail endpoint', async () => {
  api.getVisitorBooking.mockResolvedValue({ ...assignedVisitor, status: 'APPROVED', canDecide: false, version: 6 });
  render(view('/oa/visitor-booking?id=9'));
  await waitFor(() => expect(api.getVisitorBooking).toHaveBeenCalledWith(9));
  await waitFor(() => expect(publishedContext).toHaveBeenLastCalledWith(expect.objectContaining({ bookingId: 9 })));
  const dialog = await screen.findByRole('dialog');
  // rc-component/util deliberately reuses "test-id" in test mode, colliding with Select's label ID.
  expect(within(dialog).getByText('访客预约详情')).toBeVisible();
  expect(await within(dialog).findByText('Assigned visitor')).toBeInTheDocument();
  expect(within(dialog).queryByRole('button', { name: '审批' })).toBeNull();
  expect(api.approveVisitorBooking).not.toHaveBeenCalled();
});

it('does not show a delayed detail response after changing accounts', async () => {
  const late = deferred<typeof assignedVisitor>(); api.getVisitorBooking.mockReturnValueOnce(late.promise);
  const page = render(view('/oa/visitor-booking?id=9'));
  await waitFor(() => expect(api.getVisitorBooking).toHaveBeenCalledTimes(1));
  auth.user = { id: 8, tenantId: 2, permissionVersion: 2, permissions: [] };
  page.rerender(view('/oa/visitor-booking?id=9'));
  await act(async () => late.resolve(assignedVisitor));
  expect(screen.queryByText('Assigned visitor')).toBeNull();
  expect(screen.queryByRole('dialog')).toBeNull();
});
