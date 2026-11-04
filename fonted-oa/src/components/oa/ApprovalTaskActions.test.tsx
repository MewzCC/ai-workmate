import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ConfigProvider } from 'antd';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import { OaApiError, type LeaveApplication } from '@/lib/oaApi';
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['approval:act'], roles: ['USER'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ detail: vi.fn(), approve: vi.fn(), reject: vi.fn(), copyTo: vi.fn(), transfer: vi.fn(), addSign: vi.fn(), participantOptions: vi.fn(), success: vi.fn(), error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), todoApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error, success: api.success } }));
import ApprovalTaskActions from './ApprovalTaskActions';
const application = (): LeaveApplication => ({ id: 9, applicantUserId: 8, applicantName: 'Synthetic applicant', approverUserId: 7,
  leaveType: 'PERSONAL', startDate: '2026-11-05', startPeriod: 'AM', endDate: '2026-11-05', endPeriod: 'PM',
  durationHalfDays: 2, durationDays: 1, reason: 'Synthetic leave', status: 'PENDING', version: 1,
  taskId: 42, taskVersion: 2, taskStatus: 'PENDING', canApprove: true, canWithdraw: false, canRemind: false, reminderCount: 0, overdue: false,
  currentStage: 'APPROVAL', workflowStages: [], createdAt: '2026-11-04T10:00:00', updatedAt: '2026-11-04T10:00:00', canEdit: false, canSubmit: false });
const props = () => ({ application: application(), scope: 'scope-a', isCurrent: () => true, loading: false,
  reload: vi.fn().mockResolvedValue({}), onMoved: vi.fn(), onUpdated: vi.fn() });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { vi.clearAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: ['approval:act'] }; bindAuthResponseIdentity('approval-actor', 'act');
  api.detail.mockResolvedValue(application()); api.approve.mockResolvedValue({ ...application(), status: 'APPROVED', version: 2 });
  api.reject.mockResolvedValue({ ...application(), status: 'REJECTED', version: 2 });
  for (const operation of [api.copyTo, api.transfer, api.addSign]) operation.mockResolvedValue({ ...application(), version: 2 });
  api.participantOptions.mockImplementation(async (_id: number, params: { page: number; size: number }) => ({ records: [{ id: 11, name: 'Candidate', canApprove: true }], total: 1, ...params })); });
afterEach(cleanup);

it.each([
  ['审批通过', '确认通过', 'approve'], ['退回申请', '确认退回', 'reject'], ['抄送', '确认抄送', 'copyTo'],
  ['转交', '确认转交', 'transfer'], ['前加签', '确认前加签', 'PRE'], ['后加签', '确认后加签', 'POST'],
])('executes the real %s action once after confirmation and a version preflight', async (button, confirm, operation) => {
  const p = props(); render(<ApprovalTaskActions {...p} />); fireEvent.click(screen.getByRole('button', { name: new RegExp(button.split('').join('\\s*')) }));
  expect(screen.getByText(/申请 #9.*任务 #42.*节点版本 2/)).toBeTruthy();
  if (!['approve', 'reject'].includes(operation)) {
    fireEvent.mouseDown(screen.getByRole('combobox')); fireEvent.click(await screen.findByText('Candidate'));
    fireEvent.change(screen.getByLabelText('操作理由'), { target: { value: 'Synthetic reason' } });
  } else if (operation === 'reject') fireEvent.change(screen.getByLabelText('审批意见'), { target: { value: 'Synthetic rejection' } });
  const confirmation = screen.getByRole('button', { name: confirm }); fireEvent.click(confirmation); fireEvent.click(confirmation);
  await waitFor(() => expect(api.success).toHaveBeenCalledTimes(1)); expect(api.detail).toHaveBeenCalledWith(42);
  const called = operation === 'PRE' || operation === 'POST' ? api.addSign : api[operation as 'approve' | 'reject' | 'copyTo' | 'transfer'];
  expect(called).toHaveBeenCalledTimes(1);
  if (operation === 'approve') expect(api.approve).toHaveBeenCalledWith(42, 2, undefined);
  else if (operation === 'reject') expect(api.reject).toHaveBeenCalledWith(42, 2, 'Synthetic rejection');
  else if (operation === 'copyTo') expect(api.copyTo).toHaveBeenCalledWith(42, 11, 2, 'Synthetic reason');
  else if (operation === 'transfer') expect(api.transfer).toHaveBeenCalledWith(42, 11, 2, 'Synthetic reason');
  else expect(api.addSign).toHaveBeenCalledWith(42, 11, 2, operation, 'Synthetic reason');
  if (['transfer', 'PRE'].includes(operation)) expect(p.onMoved).toHaveBeenCalledTimes(1);
  else expect(p.reload).toHaveBeenCalledTimes(1);
});
it('shows a next-node result in preview approval instead of claiming the workflow is finished', async () => {
  api.approve.mockResolvedValueOnce({ ...application(), version: 2, status: 'PENDING', taskId: 43 });
  render(<ApprovalTaskActions {...props()} compact />); fireEvent.click(screen.getByRole('button', { name: '审批通过' }));
  fireEvent.click(screen.getByRole('button', { name: '确认通过' }));
  await waitFor(() => expect(api.success).toHaveBeenCalledWith('当前节点已通过，流程已流转至下一审批人'));
});
it('handles form validation without an unhandled rejection or a business write', async () => {
  render(<ApprovalTaskActions {...props()} />); fireEvent.click(screen.getByRole('button', { name: '退回申请' }));
  fireEvent.click(screen.getByRole('button', { name: '确认退回' }));
  await screen.findByText('退回时必须填写审批意见'); expect(api.reject).not.toHaveBeenCalled(); expect(api.error).not.toHaveBeenCalled();
});
it('refuses a changed server node before writing and refreshes the actual state', async () => {
  const p = props(); api.detail.mockResolvedValueOnce({ ...application(), taskVersion: 3 });
  render(<ApprovalTaskActions {...p} />); fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(screen.getByRole('button', { name: '确认通过' }));
  await waitFor(() => expect(p.reload).toHaveBeenCalledTimes(1)); expect(api.approve).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});
it('does not write from an old dialog after scope A to B to A', async () => {
  const late = deferred<LeaveApplication>(); api.detail.mockReturnValueOnce(late.promise); const p = props();
  const view = render(<ApprovalTaskActions {...p} />); fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(screen.getByRole('button', { name: '确认通过' }));
  await waitFor(() => expect(api.detail).toHaveBeenCalled()); view.rerender(<ApprovalTaskActions {...p} scope="scope-b" />); view.rerender(<ApprovalTaskActions {...p} />);
  await act(async () => { late.resolve(application()); }); expect(api.approve).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});
it('suppresses a late successful write after revocation without claiming cancellation', async () => {
  const late = deferred<LeaveApplication>(); api.approve.mockReturnValueOnce(late.promise); const p = props();
  const view = render(<ApprovalTaskActions {...p} />); fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(screen.getByRole('button', { name: '确认通过' }));
  await waitFor(() => expect(api.approve).toHaveBeenCalled()); auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(<ApprovalTaskActions {...p} />);
  await act(async () => { late.resolve({ ...application(), status: 'APPROVED', version: 2 }); });
  expect(api.success).not.toHaveBeenCalled(); expect(p.reload).not.toHaveBeenCalled(); expect(p.onUpdated).not.toHaveBeenCalled();
});
it('does not invent success or retry a failed write, and rejects a mismatched receipt', async () => {
  const p = props(); api.approve.mockResolvedValueOnce({ ...application(), id: 99, status: 'APPROVED', version: 2 });
  render(<ApprovalTaskActions {...p} />); fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(screen.getByRole('button', { name: '确认通过' }));
  await waitFor(() => expect(api.error).toHaveBeenCalled()); expect(api.success).not.toHaveBeenCalled(); expect(api.approve).toHaveBeenCalledTimes(1); expect(p.reload).toHaveBeenCalledTimes(1);
});
it('reports an uncertain write result and refreshes without retrying the write', async () => {
  const p = props(); api.approve.mockRejectedValueOnce(new TypeError('Synthetic connection loss'));
  render(<ApprovalTaskActions {...p} />); fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(screen.getByRole('button', { name: '确认通过' }));
  await waitFor(() => expect(api.error).toHaveBeenCalledWith('操作结果暂时无法确认，请先核对业务状态和审批记录，避免重复操作。'));
  expect(api.approve).toHaveBeenCalledTimes(1); expect(api.success).not.toHaveBeenCalled(); expect(p.reload).toHaveBeenCalledTimes(1);
});
it.each(['closed', 'owner', 'not-assigned', 'read-only'] as const)('hides write operations for %s while preserving read components', variant => {
  const p = props(); if (variant === 'closed') p.application.taskStatus = 'CANCELLED';
  if (variant === 'owner') p.application.applicantUserId = 7; if (variant === 'not-assigned') p.application.approverUserId = 99;
  if (variant === 'read-only') auth.user.permissions = [];
  render(<ApprovalTaskActions {...p} />); expect(screen.queryByRole('button', { name: '审批通过' })).toBeNull(); expect(api.participantOptions).not.toHaveBeenCalled();
});
it('uses server-side eligibility and search and exposes candidate read failures', async () => {
  render(<ApprovalTaskActions {...props()} />); fireEvent.click(screen.getByRole('button', { name: /转\s*交/ }));
  await waitFor(() => expect(api.participantOptions).toHaveBeenCalledWith(42, { keyword: undefined, page: 1, size: 20, approversOnly: true }));
  fireEvent.mouseDown(screen.getByRole('combobox')); await screen.findByText('Candidate');
  api.participantOptions.mockRejectedValueOnce(new OaApiError('Candidate service unavailable', 503, 'SERVICE_UNAVAILABLE'));
  fireEvent.change(screen.getByRole('combobox'), { target: { value: 'Other' } });
  await waitFor(() => expect(api.participantOptions).toHaveBeenLastCalledWith(42, { keyword: 'Other', page: 1, size: 20, approversOnly: true }));
  await screen.findByText('Candidate service unavailable'); expect(api.transfer).not.toHaveBeenCalled();
});
it('refuses an ineligible approval target returned by an inconsistent server page', async () => {
  api.participantOptions.mockResolvedValueOnce({ records: [{ id: 12, name: 'Read-only user', canApprove: false }], total: 1, page: 1, size: 20 });
  render(<ApprovalTaskActions {...props()} />); fireEvent.click(screen.getByRole('button', { name: /转\s*交/ }));
  await waitFor(() => expect(api.error).toHaveBeenCalled()); fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(screen.queryByText('Read-only user')).toBeNull(); expect(api.transfer).not.toHaveBeenCalled();
});
it('loads four real pages and can select an approver beyond the former fifty-person window', async () => {
  api.participantOptions.mockImplementation(async (_id: number, params: { page: number; size: number }) => ({
    records: Array.from({ length: Math.min(20, 65 - (params.page - 1) * 20) }, (_, index) => ({
      id: 101 + (params.page - 1) * 20 + index, name: `Candidate ${1 + (params.page - 1) * 20 + index}`, canApprove: true,
    })), total: 65, ...params,
  }));
  render(<ConfigProvider virtual={false}><ApprovalTaskActions {...props()} /></ConfigProvider>);
  fireEvent.click(screen.getByRole('button', { name: '前加签' }));
  await waitFor(() => expect(api.participantOptions).toHaveBeenCalledTimes(1)); fireEvent.mouseDown(screen.getByRole('combobox'));
  for (let page = 2; page <= 4; page++) {
    fireEvent.scroll(document.querySelector('.rc-virtual-list-holder')!);
    await waitFor(() => expect(api.participantOptions).toHaveBeenCalledWith(42, { keyword: undefined, page, size: 20, approversOnly: true }));
    await screen.findByText(`Candidate ${page === 4 ? 65 : page * 20}`);
  }
  fireEvent.click(screen.getByText('Candidate 65')); fireEvent.change(screen.getByLabelText('操作理由'), { target: { value: 'Synthetic late-page selection' } });
  fireEvent.click(screen.getByRole('button', { name: '确认前加签' }));
  await waitFor(() => expect(api.addSign).toHaveBeenCalledWith(42, 165, 2, 'PRE', 'Synthetic late-page selection'));
});
it('does not apply late candidates from a closed dialog to a new action', async () => {
  const late = deferred<{ records: { id: number; name: string; canApprove: boolean }[]; total: number; page: number; size: number }>();
  api.participantOptions.mockReturnValueOnce(late.promise); render(<ApprovalTaskActions {...props()} />);
  fireEvent.click(screen.getByRole('button', { name: /抄\s*送/ })); await waitFor(() => expect(api.participantOptions).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: /取\s*消/ })); fireEvent.click(screen.getByRole('button', { name: /转\s*交/ }));
  await waitFor(() => expect(api.participantOptions).toHaveBeenCalledTimes(2));
  await act(async () => { late.resolve({ records: [{ id: 99, name: 'Stale candidate', canApprove: true }], total: 1, page: 1, size: 20 }); });
  fireEvent.mouseDown(screen.getByRole('combobox')); await screen.findByText('Candidate'); expect(screen.queryByText('Stale candidate')).toBeNull();
});
