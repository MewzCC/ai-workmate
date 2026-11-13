import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ApprovalApplication } from '@/lib/approvalEngineApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), createDraft: vi.fn(), updateDraft: vi.fn(),
  submit: vi.fn(), cancel: vi.fn(), withdraw: vi.fn(), reopen: vi.fn(), remind: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:approval-start'], version: 1 }));
vi.mock('@/lib/expenseApi', async (load) => ({ ...await load<typeof import('@/lib/expenseApi')>(), expenseApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import ExpensePage from './ExpensePage';

const expense: ApprovalApplication = { id: 11, applicantUserId: 7, applicantName: '测试申请人',
  formKey: 'expense-application', formName: '费用报销', title: '测试报销甲', status: 'DRAFT', version: 3,
  dataJson: JSON.stringify({ amount: 100, category: 'OFFICE', expenseDate: '2026-10-22', invoiceNumber: 'FIXTURE-11', reason: '测试报销甲' }),
  overdue: false, reminderCount: 0, canRemind: false, canWithdraw: false, canEditDraft: true, canCancel: true,
  createdAt: '2026-10-22T09:00:00', updatedAt: '2026-10-22T09:00:00', timeline: [] };
const other: ApprovalApplication = { ...expense, id: 12, title: '测试报销乙', version: 8 };
function Probe() {
  const location = useLocation(); const navigate = useNavigate();
  return <><output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
    <output aria-label="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/oa/expense?id=12')}>选择乙</button></>;
}
function view(path = '/oa/expense') {
  return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="expense">
    <ExpensePage /><Probe />
  </PageAgentContextProvider></App></MemoryRouter>;
}
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => {
  vi.clearAllMocks(); auth.permissions = ['route:approval-start']; auth.version = 1;
  api.list.mockResolvedValue({ records: [expense], total: 1, page: 1, size: 20 });
  api.detail.mockImplementation(async (id: number) => id === 11 ? expense : other);
  api.updateDraft.mockResolvedValue({ ...expense, version: 4 });
  api.createDraft.mockResolvedValue({ ...expense, version: 0 });
  api.submit.mockResolvedValue({ ...expense, status: 'PENDING', version: 5 });
});
afterEach(cleanup);

describe('expense page Agent selection', () => {
  it('publishes pagination without guessing a selected application from the list', async () => {
    render(view()); await screen.findByText('FIXTURE-11');
    expect(context()).toEqual({ page: 1, size: 20 });
  });
  it('loads only the requested actual expense and publishes its ID/version, then clears on close', async () => {
    render(view('/oa/expense?id=11'));
    await waitFor(() => expect(context()).toEqual({ applicationId: 11, version: 3, page: 1, size: 20 }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('applicationId'));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/expense');
  });
  it('ignores late details after closing', async () => {
    let resolve!: (value: ApprovalApplication) => void;
    api.detail.mockReturnValue(new Promise<ApprovalApplication>((done) => { resolve = done; }));
    render(view('/oa/expense?id=11'));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Close' }));
    await act(async () => resolve(expense));
    expect(context()).not.toHaveProperty('applicationId');
    expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('does not replace a newer selection with an earlier read', async () => {
    let resolve!: (value: ApprovalApplication) => void;
    api.detail.mockImplementation((id: number) => id === 11 ? new Promise<ApprovalApplication>((done) => { resolve = done; }) : Promise.resolve(other));
    render(view('/oa/expense?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ applicationId: 12, version: 8 }));
    await act(async () => resolve(expense));
    expect(context()).toMatchObject({ applicationId: 12, version: 8 });
  });
  it('requires the current ordinary application permission, not stale action flags', async () => {
    auth.permissions = ['route:expense']; render(view()); await screen.findByText('FIXTURE-11');
    expect(screen.queryByRole('button', { name: '新建报销' })).toBeNull();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
  });
  it('does not keep an old object selected after closing a new draft form', async () => {
    render(view('/oa/expense?id=11'));
    await waitFor(() => expect(context()).toHaveProperty('applicationId', 11));
    fireEvent.click(screen.getByRole('button', { name: '新建报销' }));
    expect(context()).not.toHaveProperty('applicationId');
    fireEvent.click(within(screen.getByLabelText('报销事由').closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('applicationId'));
  });
  it('keeps the newly saved version if subsequent submission fails', async () => {
    api.submit.mockRejectedValue(new Error('APPROVER_UNAVAILABLE'));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    fireEvent.click(screen.getByRole('button', { name: '提交审批' }));
    await waitFor(() => expect(api.submit).toHaveBeenCalledWith(11, 4));
    await waitFor(() => expect(context()).toHaveProperty('version', 4));
    expect(screen.getByLabelText('报销事由')).toHaveValue('测试报销甲');
  });
  it.each(['0', '-1', '1.5', '11tail', '9007199254740992'])('does not read an invalid route ID: %s', async (id) => {
    render(view(`/oa/expense?id=${id}`)); await screen.findByText('FIXTURE-11');
    expect(api.detail).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('applicationId');
  });
  it.each([
    { id: 12 }, { formKey: 'different-form' }, { applicantUserId: 99 },
  ])('rejects a mismatched expense detail: %j', async (fields) => {
    api.detail.mockResolvedValue({ ...expense, ...fields }); render(view('/oa/expense?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(context()).not.toHaveProperty('applicationId');
  });
  it('removes selected data and refetches when the permission version changes', async () => {
    const page = render(view('/oa/expense?id=11'));
    await waitFor(() => expect(context()).toHaveProperty('applicationId', 11));
    api.detail.mockRejectedValue(new Error('FORBIDDEN')); auth.version += 1; auth.permissions = ['route:expense'];
    page.rerender(view('/oa/expense?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(context()).not.toHaveProperty('applicationId'));
    expect(screen.queryByRole('button', { name: '新建报销' })).toBeNull();
  });
  it('does not submit after a pending save finishes in a closed editor', async () => {
    let resolve!: (value: ApprovalApplication) => void;
    api.updateDraft.mockReturnValue(new Promise<ApprovalApplication>((done) => { resolve = done; }));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    fireEvent.click(screen.getByRole('button', { name: '提交审批' }));
    await waitFor(() => expect(api.updateDraft).toHaveBeenCalledWith(11, 3, expect.objectContaining({ reason: '测试报销甲' })));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
    await act(async () => resolve({ ...expense, version: 4 }));
    expect(api.submit).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('applicationId');
  });
  it('preserves the original form and version when saving is rejected', async () => {
    api.updateDraft.mockRejectedValue(new Error('VERSION_CONFLICT'));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    fireEvent.change(screen.getByLabelText('报销事由'), { target: { value: '未保存测试事由' } });
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
    await waitFor(() => expect(api.updateDraft).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole('button', { name: '保存草稿' })).not.toHaveClass('ant-btn-loading'));
    expect(screen.getByLabelText('报销事由')).toHaveValue('未保存测试事由');
    expect(context()).toMatchObject({ applicationId: 11, version: 3 });
  });
  it('does not reopen an older expense after a pending write completes', async () => {
    let resolve!: (value: ApprovalApplication) => void;
    api.updateDraft.mockReturnValue(new Promise<ApprovalApplication>((done) => { resolve = done; }));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
    await waitFor(() => expect(api.updateDraft).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ applicationId: 12, version: 8 }));
    await act(async () => resolve({ ...expense, version: 4 }));
    expect(context()).toMatchObject({ applicationId: 12, version: 8 });
  });
  it('retries a failed detail at the same URL', async () => {
    api.detail.mockRejectedValueOnce(new Error('FORBIDDEN'));
    render(view('/oa/expense?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    fireEvent.click(await screen.findByRole('button', { name: /查\s*看/ }));
    await waitFor(() => expect(context()).toHaveProperty('applicationId', 11));
    expect(api.detail).toHaveBeenCalledTimes(2);
  });
  it('reopens an owned expense at the selected version after real confirmation', async () => {
    api.detail.mockResolvedValue({ ...expense, status: 'WITHDRAWN', canEditDraft: false, canCancel: false });
    api.reopen.mockResolvedValue({ ...expense, version: 4 });
    render(view('/oa/expense?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: '重新编辑' }));
    const [title] = await screen.findAllByText('确认恢复为报销草稿？');
    fireEvent.click(within(title.closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() => expect(api.reopen).toHaveBeenCalledWith(11, 3));
    await waitFor(() => expect(context()).toMatchObject({ applicationId: 11, version: 4 }));
    expect(screen.getByLabelText('报销事由')).toHaveValue('测试报销甲');
  });
  it('does not execute a still-open confirmation after live write permission is revoked', async () => {
    const page = render(view('/oa/expense?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: '取消草稿' }));
    const [title] = await screen.findAllByText('确认取消草稿？');
    const confirm = within(title.closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: /确\s*认/ });
    auth.permissions = ['route:expense']; auth.version += 1; page.rerender(view('/oa/expense?id=11'));
    await waitFor(() => expect(screen.queryByRole('button', { name: '新建报销' })).toBeNull());
    fireEvent.click(confirm);
    await waitFor(() => expect(screen.queryAllByText('确认取消草稿？')).toHaveLength(0));
    expect(api.cancel).not.toHaveBeenCalled();
  });
});
