import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import { MemoryRouter, useLocation, useNavigate, type NavigateFunction } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { LeaveApplication, LeaveApplicationPayload, LeaveApprovalContext } from '@/lib/oaApi';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import { PageAgentContextProvider } from './PageAgentContext';

const permissions = ['route:leave-application', 'leave:create', 'route:my-applications', 'leave:read:self'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[], roles: ['EMPLOYEE'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ approvalContext: vi.fn(), approverCandidates: vi.fn(), detail: vi.fn(), create: vi.fn(), update: vi.fn(), submit: vi.fn(), success: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), leaveApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: { success: api.success, error: api.error } }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import LeaveFormPage from './LeaveFormPage';

const future = dayjs().add(2, 'day').format('YYYY-MM-DD');
const context: LeaveApprovalContext = { applicantName: 'Synthetic employee', departmentName: 'Test group', approverUserId: 9, approverName: 'Valid reviewer', approverConfigured: true, approverSource: 'DIRECT_OR_DEPARTMENT_DEFAULT', approvalDueHours: 48 };
function application(version = 1): LeaveApplication { return { id: 11, applicantUserId: 7, applicantName: 'Synthetic employee', approverUserId: 9, approverName: 'Valid reviewer',
  leaveType: 'PERSONAL', startDate: future, startPeriod: 'AM', endDate: future, endPeriod: 'PM', durationHalfDays: 2, durationDays: 1,
  reason: 'Saved draft reason', status: 'DRAFT', version, canEdit: true, canSubmit: true, canApprove: false, canRemind: false, canWithdraw: false,
  overdue: false, reminderCount: 0, currentStage: 'APPLICATION', workflowStages: [], createdAt: '2026-11-03T09:00:00', updatedAt: '2026-11-03T09:00:00' }; }
let goto: NavigateFunction;
function Location() { const location = useLocation(); goto = useNavigate(); return <output aria-label="leave-location">{location.pathname}{location.search}</output>; }
const tree = (path = '/oa/leave-application') => <MemoryRouter initialEntries={[path]}><ConfigProvider locale={zhCN} virtual={false}><App>
  <PageAgentContextProvider pageId="leave-application"><LeaveFormPage /><Location /></PageAgentContextProvider></App></ConfigProvider></MemoryRouter>;
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
async function fill(reason = 'Confirmed reason', approverUserId = 9) { await act(async () => {
  await ui.handlers['ui.fillForm']!({ pageId: 'leave-application', code: 'ui.fillForm', payload: { values: {
    leaveType: 'PERSONAL', approverUserId, startDate: future, endDate: future, startPeriod: 'AM', endPeriod: 'PM', reason,
  }, formContext: { sessionId: 'fixture' } } });
}); }
async function ready() { await waitFor(() => expect(screen.getByLabelText('请假事由')).not.toBeDisabled()); }
async function submitDialog() { fireEvent.click(screen.getByRole('button', { name: '提交审批' })); return screen.findByRole('dialog'); }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: [...permissions] }; bindAuthResponseIdentity('leave-editor-owner', 'allowed');
  api.approvalContext.mockResolvedValue(context); api.detail.mockResolvedValue(application());
  api.approverCandidates.mockResolvedValue({ records: [{ id: 9, name: 'Valid reviewer', departmentName: 'Test group', recommended: true }], total: 1 });
  let last: LeaveApplicationPayload | undefined;
  api.create.mockImplementation((payload: LeaveApplicationPayload) => { last = payload; return Promise.resolve({ ...application(), ...payload, version: 0 }); });
  api.update.mockImplementation((_id: number, payload: LeaveApplicationPayload) => { last = payload; return Promise.resolve({ ...application(), ...payload, version: payload.version! + 1 }); });
  api.submit.mockImplementation((id: number, version: number) => Promise.resolve({ ...application(), ...last, id, version: version + 1, status: 'PENDING', canEdit: false, canSubmit: false, taskId: 22 }));
});
afterEach(cleanup);

it('uses the domain candidate API with search and further pages instead of truncating at 100 users', async () => {
  api.approverCandidates.mockResolvedValueOnce({ records: Array.from({ length: 20 }, (_, i) => ({ id: i + 20, name: `Reviewer ${i + 20}` })), total: 21 })
    .mockResolvedValueOnce({ records: [{ id: 99, name: 'Last reviewer' }], total: 21 });
  render(tree()); await ready();
  await waitFor(() => expect(api.approverCandidates).toHaveBeenCalledWith({ keyword: undefined, page: 1, size: 20 }));
  const input = screen.getByLabelText('本次审批人'); fireEvent.mouseDown(input); await screen.findByText('Reviewer 20');
  const popup = document.querySelector('.rc-virtual-list-holder')!; fireEvent.scroll(popup); fireEvent.scroll(popup);
  await waitFor(() => expect(api.approverCandidates).toHaveBeenCalledWith({ keyword: undefined, page: 2, size: 20 }));
  await screen.findByText('Last reviewer');
  fireEvent.change(input, { target: { value: 'Valid' } });
  await waitFor(() => expect(api.approverCandidates).toHaveBeenCalledWith({ keyword: 'Valid', page: 1, size: 20 }));
});

it('keeps a replacement draft when an old detail response finishes after navigating A to B to A', async () => {
  const old = deferred<LeaveApplication>(); api.detail.mockReturnValueOnce(old.promise).mockResolvedValueOnce({ ...application(), id: 12, reason: 'New draft B' }).mockResolvedValueOnce({ ...application(), reason: 'Fresh draft A' });
  render(tree('/oa/leave-application?id=11')); await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(1));
  await act(async () => { goto('/oa/leave-application?id=12'); }); await screen.findByDisplayValue('New draft B');
  await act(async () => { goto('/oa/leave-application?id=11'); }); await screen.findByDisplayValue('Fresh draft A');
  await act(async () => { old.resolve({ ...application(), reason: 'Old private draft' }); });
  expect(screen.queryByDisplayValue('Old private draft')).toBeNull();
});

it.each(['-1', '1.5', 'invalid', '9007199254740993'])('refuses invalid edit identifiers without creating a new request: %s', async id => {
  render(tree(`/oa/leave-application?id=${id}`)); await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(api.detail).not.toHaveBeenCalled(); expect(api.approvalContext).not.toHaveBeenCalled(); expect(api.create).not.toHaveBeenCalled();
});

it('does not render an auditor-readable draft owned by another applicant', async () => {
  api.detail.mockResolvedValue({ ...application(), applicantUserId: 99 });
  render(tree('/oa/leave-application?id=11')); await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(screen.queryByDisplayValue('Saved draft reason')).toBeNull(); expect(screen.queryByLabelText('请假事由')).toBeNull();
});

it('locks a save before validation and dispatch and suppresses its late success after revocation', async () => {
  const pending = deferred<LeaveApplication>(); api.create.mockReturnValueOnce(pending.promise);
  const view = render(tree()); await ready(); await fill();
  const save = screen.getByRole('button', { name: '保存草稿' }); fireEvent.click(save); fireEvent.click(save);
  await waitFor(() => expect(api.create).toHaveBeenCalledTimes(1)); expect(screen.getByLabelText('请假事由')).toBeDisabled();
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await act(async () => { pending.resolve({ ...application(), reason: 'Confirmed reason' }); });
  expect(api.success).not.toHaveBeenCalled(); expect(screen.getByLabelText('leave-location').textContent).toBe('/oa/leave-application');
});

it('confirms exact dates, periods, approver and reason and submits once using the acknowledged draft', async () => {
  render(tree()); await ready(); await fill('Exact confirmed reason');
  const dialog = await submitDialog(); expect(within(dialog).getByText('Exact confirmed reason')).toBeInTheDocument();
  expect(within(dialog).getByText(/\(#9\)/)).toBeInTheDocument(); expect(screen.getByLabelText('请假事由')).toBeDisabled();
  expect(ui.handlers['ui.fillForm']).toBeUndefined();
  const confirm = within(dialog).getByRole('button', { name: '确认提交' }); fireEvent.click(confirm); fireEvent.click(confirm);
  await waitFor(() => expect(api.create).toHaveBeenCalledTimes(1)); await waitFor(() => expect(api.submit).toHaveBeenCalledWith(11, 0));
  expect(api.create).toHaveBeenCalledWith({ leaveType: 'PERSONAL', approverUserId: 9, startDate: future, endDate: future, startPeriod: 'AM', endPeriod: 'PM', reason: 'Exact confirmed reason', version: undefined });
  await waitFor(() => expect(screen.getByLabelText('leave-location').textContent).toBe('/oa/my-applications'));
});

it('retains the saved version after submit failure and requires a new confirmation to retry', async () => {
  api.submit.mockRejectedValueOnce(new Error('Synthetic submit failure'));
  render(tree('/oa/leave-application?id=11')); await screen.findByDisplayValue('Saved draft reason');
  fireEvent.click(within(await submitDialog()).getByRole('button', { name: '确认提交' }));
  await waitFor(() => expect(api.submit).toHaveBeenCalledWith(11, 2)); await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(api.success).not.toHaveBeenCalled(); await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  fireEvent.click(within(await submitDialog()).getByRole('button', { name: '确认提交' }));
  await waitFor(() => expect(api.update).toHaveBeenLastCalledWith(11, expect.objectContaining({ version: 2 })));
  await waitFor(() => expect(api.submit).toHaveBeenLastCalledWith(11, 3));
});

it('stops the second write after an in-flight update loses its authenticated form scope', async () => {
  const pending = deferred<LeaveApplication>(); api.update.mockReturnValueOnce(pending.promise);
  const view = render(tree('/oa/leave-application?id=11')); await screen.findByDisplayValue('Saved draft reason');
  fireEvent.click(within(await submitDialog()).getByRole('button', { name: '确认提交' })); await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, id: 8, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await act(async () => { pending.resolve(application(2)); }); expect(api.submit).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});

it('preserves the newly saved draft route after submission fails without creating or submitting again', async () => {
  api.submit.mockRejectedValueOnce(new Error('Synthetic submit failure'));
  api.detail.mockResolvedValue({ ...application(0), reason: 'New saved reason' });
  render(tree()); await ready(); await fill('New saved reason');
  fireEvent.click(within(await submitDialog()).getByRole('button', { name: '确认提交' }));
  await waitFor(() => expect(screen.getByLabelText('leave-location').textContent).toBe('/oa/leave-application?id=11'));
  await screen.findByDisplayValue('New saved reason');
  expect(api.create).toHaveBeenCalledTimes(1); expect(api.submit).toHaveBeenCalledTimes(1); expect(api.success).not.toHaveBeenCalled();
});

it('closes private UI previews and pending confirmations when the page is unmounted', async () => {
  const view = render(tree()); await ready(); await fill();
  await act(async () => { await ui.handlers['ui.previewSubmission']!({ pageId: 'leave-application', code: 'ui.previewSubmission', payload: { formContext: { sessionId: 'fixture' } } }); });
  await screen.findByRole('dialog'); view.unmount(); await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(api.create).not.toHaveBeenCalled();
});

it('loads draft values under root StrictMode effect replay', async () => {
  render(tree('/oa/leave-application?id=11'), { reactStrictMode: true }); await screen.findByDisplayValue('Saved draft reason');
  expect(api.detail).toHaveBeenCalledTimes(2); expect(screen.getByLabelText('开始时间')).toHaveValue(dayjs(future).format('YYYY年MM月DD日'));
});
