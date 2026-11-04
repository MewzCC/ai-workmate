import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { EmployeeChange, OrganizationOverview } from '@/lib/hrApi';

const permissions = ['route:employee-change', 'hr:read', 'hr:manage'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[] } }));
const api = vi.hoisted(() => ({ overview: vi.fn(), listEmployeeChanges: vi.fn(), employeeChangeReviewerOptions: vi.fn(),
  createEmployeeChange: vi.fn(), employeeChangeDetail: vi.fn(), approveEmployeeChange: vi.fn(),
  rejectEmployeeChange: vi.fn(), withdrawEmployeeChange: vi.fn() }));
const messages = vi.hoisted(() => ({ error: vi.fn(), success: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/hrApi', () => ({ hrApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
import EmployeeChangePage from './EmployeeChangePage';

const organization: OrganizationOverview = { departments: [], positions: [], employees: [
  { id: 8, name: 'Synthetic employee', email: 'employee@example.invalid', role: 'EMPLOYEE', status: 1 },
  { id: 11, name: 'Legacy administrator', email: 'legacy@example.invalid', role: 'SYSTEM_ADMIN', status: 1 },
] };
function change(id = 1): EmployeeChange { return { id, employeeUserId: 8, employeeName: `Synthetic employee ${id}`,
  employeeEmail: 'employee@example.invalid', applicantUserId: 7, applicantName: 'Synthetic applicant',
  reviewApproverUserId: 7, reviewApproverName: 'Synthetic reviewer', changeType: 'REGULARIZATION',
  effectiveDate: '2026-11-04', reason: `Synthetic reason ${id}`, status: 'PENDING', version: 3,
  canApprove: true, canWithdraw: true, submittedAt: '2026-11-03T09:00:00', createdAt: '', updatedAt: '' }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function view(path = '/oa/employee-change') { return <MemoryRouter initialEntries={[path]}>
  <ConfigProvider locale={zhCN}><App><EmployeeChangePage /></App></ConfigProvider>
</MemoryRouter>; }
async function open() {
  await waitFor(() => expect(api.overview).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: '发起员工变动' }));
  return screen.findByRole('dialog');
}
async function fill(dialog: HTMLElement) {
  fireEvent.mouseDown(within(dialog).getByLabelText('变动员工'));
  fireEvent.click(await screen.findByText('Synthetic employee · employee@example.invalid'));
  fireEvent.mouseDown(within(dialog).getByLabelText('变动类型'));
  fireEvent.click(await screen.findByText('转正'));
  fireEvent.mouseDown(within(dialog).getByLabelText('流程审批人'));
  fireEvent.click(await screen.findByText('Permission-based reviewer'));
  fireEvent.change(within(dialog).getByLabelText('变动说明'), { target: { value: 'Synthetic change' } });
}
function row(id: number) { return screen.getByText(`Synthetic employee ${id}`).closest('tr')!; }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions };
  api.overview.mockResolvedValue(organization);
  api.listEmployeeChanges.mockResolvedValue({ records: [], total: 0 });
  api.employeeChangeReviewerOptions.mockResolvedValue({ records: [{ id: 9, name: 'Permission-based reviewer' }], total: 1 });
  api.employeeChangeDetail.mockImplementation(async (id: number) => change(id));
});
afterEach(cleanup);

it('selects reviewers from the protected domain API rather than inferring them from administrator roles', async () => {
  render(view());
  expect(api.employeeChangeReviewerOptions).not.toHaveBeenCalled();
  const dialog = await open();
  fireEvent.mouseDown(within(dialog).getByLabelText('流程审批人'));
  await screen.findByText('Permission-based reviewer');
  expect(screen.queryByText('Legacy administrator · legacy@example.invalid')).toBeNull();
  expect(api.employeeChangeReviewerOptions).toHaveBeenCalledWith({ keyword: undefined, page: 1, size: 20 });
});

it('leaves validation failures inline without an unhandled write or error notification', async () => {
  render(view()); const dialog = await open();
  fireEvent.click(within(dialog).getByRole('button', { name: '提交审批' }));
  await waitFor(() => expect(dialog.querySelectorAll('.ant-form-item-explain-error').length).toBeGreaterThan(0));
  await waitFor(() => expect(within(dialog).getByRole('button', { name: '提交审批' })).not.toHaveClass('ant-btn-loading'));
  expect(api.createEmployeeChange).not.toHaveBeenCalled();
  expect(messages.error).not.toHaveBeenCalled();
});

it('submits a selected reviewer once and protects the next draft from the previous write', async () => {
  const late = deferred<object>(); api.createEmployeeChange.mockReturnValueOnce(late.promise);
  render(view()); const dialog = await open(); await fill(dialog);
  const submit = within(dialog).getByRole('button', { name: '提交审批' });
  act(() => { fireEvent.click(submit); fireEvent.click(submit); });
  await waitFor(() => expect(api.createEmployeeChange).toHaveBeenCalledTimes(1));
  expect(api.createEmployeeChange).toHaveBeenCalledWith(expect.objectContaining({
    employeeUserId: 8, reviewApproverUserId: 9, changeType: 'REGULARIZATION', reason: 'Synthetic change',
  }));
  expect(within(dialog).getByLabelText('流程审批人')).toBeDisabled();
  fireEvent.click(within(dialog).getByRole('button', { name: '关 闭' }));
  const replacement = await open();
  fireEvent.change(within(replacement).getByLabelText('变动说明'), { target: { value: 'Replacement draft' } });
  await act(async () => { late.resolve({}); });
  expect(within(await screen.findByRole('dialog')).getByLabelText('变动说明')).toHaveValue('Replacement draft');
  expect(messages.success).not.toHaveBeenCalled();
  await waitFor(() => expect(api.listEmployeeChanges).toHaveBeenCalledTimes(2));
});

it('uses the latest selected detail when a slower previous selection completes', async () => {
  const late = deferred<EmployeeChange>();
  api.listEmployeeChanges.mockResolvedValue({ records: [change(1), change(2)], total: 2 });
  api.employeeChangeDetail.mockImplementation((id: number) => id === 1 ? late.promise : Promise.resolve(change(id)));
  render(view()); await screen.findByText('Synthetic employee 1');
  fireEvent.click(within(row(1)).getByRole('button', { name: /^查\s*看$/ }));
  fireEvent.click(within(row(2)).getByRole('button', { name: /^查\s*看$/ }));
  await screen.findByText('Synthetic reason 2');
  await act(async () => { late.resolve(change(1)); });
  expect(screen.queryByText('Synthetic reason 1')).toBeNull();
});

it('does not publish a previous actor list when it finishes after a new actor has loaded', async () => {
  const late = deferred<{ records: EmployeeChange[]; total: number }>();
  api.listEmployeeChanges.mockReturnValueOnce(late.promise)
    .mockResolvedValue({ records: [change(2)], total: 1 });
  const page = render(view());
  await waitFor(() => expect(api.listEmployeeChanges).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, id: 8 }; page.rerender(view());
  await screen.findByText('Synthetic employee 2');
  await act(async () => { late.resolve({ records: [change(1)], total: 1 }); });
  expect(screen.queryByText('Synthetic employee 1')).toBeNull();
});

it('does not send or display a previous actor search after changing identity', async () => {
  const page = render(view());
  await waitFor(() => expect(api.listEmployeeChanges).toHaveBeenCalledTimes(1));
  const input = screen.getByPlaceholderText('搜索员工姓名或邮箱');
  fireEvent.change(input, { target: { value: 'Synthetic private search' } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', charCode: 13 });
  await waitFor(() => expect(api.listEmployeeChanges).toHaveBeenCalledTimes(2));
  expect(api.listEmployeeChanges).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'Synthetic private search' }));
  auth.user = { ...auth.user, id: 8 }; page.rerender(view());
  await waitFor(() => expect(api.listEmployeeChanges).toHaveBeenCalledTimes(3));
  expect(api.listEmployeeChanges).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '', page: 1 }));
  expect(screen.getByPlaceholderText('搜索员工姓名或邮箱')).toHaveValue('');
});

it('authorizes a valid route selection through the detail API and rejects mismatched results', async () => {
  api.employeeChangeDetail.mockResolvedValue(change(99));
  render(view('/oa/employee-change?id=2'));
  await waitFor(() => expect(messages.error).toHaveBeenCalledTimes(1));
  expect(api.employeeChangeDetail).toHaveBeenCalledWith(2);
  expect(screen.queryByText('Synthetic reason 99')).toBeNull();
});

it('destroys pending confirmation dialogs after revocation and hides writes despite old row flags', async () => {
  api.listEmployeeChanges.mockResolvedValue({ records: [change(1)], total: 1 });
  const page = render(view()); await screen.findByText('Synthetic employee 1');
  fireEvent.click(within(row(1)).getByRole('button', { name: /^通\s*过$/ }));
  await screen.findAllByText('确认通过这笔员工变动？');
  auth.user = { ...auth.user, permissionVersion: 2, permissions: ['route:employee-change', 'hr:read'] };
  page.rerender(view());
  await waitFor(() => expect(screen.queryAllByText('确认通过这笔员工变动？')).toHaveLength(0));
  await screen.findByText('Synthetic employee 1');
  expect(screen.queryByRole('button', { name: /^通\s*过$/ })).toBeNull();
  expect(screen.queryByRole('button', { name: /^驳\s*回$/ })).toBeNull();
  expect(screen.queryByRole('button', { name: /^撤\s*回$/ })).toBeNull();
  expect(api.approveEmployeeChange).not.toHaveBeenCalled();
});

it('suppresses old write completion and list reloads after the actor loses access', async () => {
  const late = deferred<object>(); api.createEmployeeChange.mockReturnValueOnce(late.promise);
  const page = render(view()); const dialog = await open(); await fill(dialog);
  fireEvent.click(within(dialog).getByRole('button', { name: '提交审批' }));
  await waitFor(() => expect(api.createEmployeeChange).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; page.rerender(view());
  await act(async () => { late.resolve({}); });
  expect(messages.success).not.toHaveBeenCalled();
  expect(api.listEmployeeChanges).toHaveBeenCalledTimes(1);
  expect(screen.queryByRole('button', { name: '发起员工变动' })).toBeNull();
});

it('sends one versioned approval and ignores its completion after access changes', async () => {
  const late = deferred<object>(); api.approveEmployeeChange.mockReturnValueOnce(late.promise);
  api.listEmployeeChanges.mockResolvedValue({ records: [change(1)], total: 1 });
  const page = render(view()); await screen.findByText('Synthetic employee 1');
  fireEvent.click(within(row(1)).getByRole('button', { name: /^通\s*过$/ }));
  const confirm = await screen.findByRole('dialog');
  const submit = within(confirm).getByRole('button', { name: /^通\s*过$/ });
  act(() => { fireEvent.click(submit); fireEvent.click(submit); });
  await waitFor(() => expect(api.approveEmployeeChange).toHaveBeenCalledTimes(1));
  expect(api.approveEmployeeChange).toHaveBeenCalledWith(1, 3);
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; page.rerender(view());
  await act(async () => { late.resolve({}); });
  expect(messages.success).not.toHaveBeenCalled();
  expect(api.listEmployeeChanges).toHaveBeenCalledTimes(1);
});
