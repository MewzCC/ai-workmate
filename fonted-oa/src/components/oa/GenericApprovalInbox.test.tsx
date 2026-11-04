import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { t } from 'i18next';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import type { GenericApprovalTaskDetail } from '@/lib/genericApprovalTaskApi';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { PageAgentContextProvider } from './PageAgentContext';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, roles: ['USER'], permissions: ['todo:read', 'approval:act'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ mine: vi.fn(), detail: vi.fn(), approve: vi.fn(), reject: vi.fn(), push: vi.fn(), error: vi.fn(), success: vi.fn() }));
const state = vi.hoisted(() => ({ search: 'kind=generic', handlers: {} as PageUiCommandHandlers, views: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/genericApprovalTaskApi', () => ({ genericApprovalTaskApi: api }));
vi.mock('@/lib/nextCompat', () => ({ useRouter: () => ({ push: api.push }), useSearchParams: () => new URLSearchParams(state.search) }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error, success: api.success } }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiViews: state.views, useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { state.handlers = handlers; } }));
import GenericApprovalInbox from './GenericApprovalInbox';
import TodoListPage from './TodoListPage';

const detail = (id = 42): GenericApprovalTaskDetail => ({
  task: { id, applicationId: 9, applicantUserId: 8, applicantName: 'Synthetic applicant', formName: 'Frozen form', title: 'Frozen request', nodeName: 'First review', nodeIndex: 0, status: 'PENDING', version: 2, overdue: false, hasSnapshot: true },
  instanceStatus: 'RUNNING', applicationStatus: 'PENDING', canApprove: true, canReject: true, fieldPage: 1, totalFields: 3,
  fields: [{ name: 'reason', label: 'Frozen reason', type: 'text', values: ['Original payload'], redacted: false },
    { name: 'amount', label: 'Exact amount', type: 'money', values: ['9007199254740993.01'], redacted: false },
    { name: 'bankAccount', label: 'Private account', type: 'text', values: [], redacted: true }], timeline: [],
});
const tree = (tabs = false) => <PageAgentContextProvider pageId="todo">{tabs ? <TodoListPage /> : <GenericApprovalInbox />}</PageAgentContextProvider>;
const confirmButton = () => screen.getByRole('button', { name: new RegExp(t('common.confirm').split('').join('\\s*')) });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => {
  vi.clearAllMocks(); state.search = 'kind=generic';
  auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: ['todo:read', 'approval:act'] }; bindAuthResponseIdentity('generic-actor', 'act');
  api.mine.mockResolvedValue({ records: [detail().task], total: 1, page: 1, size: 20 }); api.detail.mockImplementation(async (id: number, page = 1) => ({ ...detail(id), fieldPage: page }));
  api.approve.mockImplementation(async () => { const receipt = { ...detail(), task: { ...detail().task, status: 'APPROVED', version: 3 }, canApprove: false, canReject: false }; api.detail.mockResolvedValue(receipt); return receipt; });
  api.reject.mockImplementation(async () => { const receipt = { ...detail(), applicationStatus: 'REJECTED', instanceStatus: 'COMPLETED', task: { ...detail().task, status: 'REJECTED', version: 3 }, canApprove: false, canReject: false }; api.detail.mockResolvedValue(receipt); return receipt; });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('uses real URL tabs and numeric filters without mounting the leave query', async () => {
  render(tree(true)); await screen.findByText('Frozen request');
  expect(state.views).toHaveBeenCalledWith('generic', ['leave', 'generic'], expect.any(Function));
  await act(async () => { await state.handlers['ui.applyFilter']!({ pageId: 'todo', code: 'ui.applyFilter', payload: { filters: { status: 'APPROVED', page: 2, size: 50, from: '2026-11-01', to: '2026-11-03' } } }); });
  await waitFor(() => expect(api.mine).toHaveBeenLastCalledWith({ status: 'APPROVED', page: 2, size: 50, from: '2026-11-01T00:00:00', to: '2026-11-03T00:00:00' }));
  const invalid: Record<string, string | number>[] = [{ size: 51 }, { page: 0 }, { status: 'WAITING' }];
  for (const filters of invalid)
    await expect(async () => state.handlers['ui.applyFilter']!({ pageId: 'todo', code: 'ui.applyFilter', payload: { filters } })).rejects.toThrow();
  fireEvent.click(screen.getByRole('tab', { name: t('approval.genericTasks.leaveTab') })); expect(api.push).toHaveBeenCalledWith('/oa/todo');
});

it('preflights the protected task before navigating and never guesses a denied detail', async () => {
  render(tree()); await screen.findByText('Frozen request'); fireEvent.click(screen.getByRole('button', { name: /查\s*看/ }));
  await waitFor(() => expect(api.push).toHaveBeenCalledWith('/oa/todo?kind=generic&taskId=42'));
  api.push.mockClear(); api.detail.mockRejectedValueOnce(new Error('denied'));
  await expect(state.handlers['ui.openDetail']!({ pageId: 'todo', code: 'ui.openDetail', payload: { recordId: 99 } })).rejects.toThrow('denied');
  expect(api.push).not.toHaveBeenCalled();
});

it('restores protected detail from the URL and displays exact amounts and redaction', async () => {
  state.search += '&taskId=42'; render(tree()); await screen.findByText('Original payload');
  expect(screen.getByText('9007199254740993.01')).toBeTruthy(); expect(screen.getByText(t('approval.genericApplications.redacted'))).toBeTruthy();
  expect(api.detail).toHaveBeenCalledWith(42, 1);
});

it('rejects malformed URL identifiers before any protected detail request', async () => {
  state.search += '&taskId=9007199254740993'; render(tree()); await screen.findByText(t('approval.genericTasks.invalidId'));
  expect(api.detail).not.toHaveBeenCalled();
});

it('approves exactly once after a version preflight and reports a handoff instead of final completion', async () => {
  state.search += '&taskId=42'; render(tree()); await screen.findByText('Original payload');
  fireEvent.click(screen.getByRole('button', { name: '审批通过' })); const confirm = confirmButton(); fireEvent.click(confirm); fireEvent.click(confirm);
  await waitFor(() => expect(api.success).toHaveBeenCalledWith(t('approval.approvalDetail.approveNextSuccess')));
  expect(api.approve).toHaveBeenCalledTimes(1); expect(api.approve).toHaveBeenCalledWith(42, 2, undefined); expect(api.detail).toHaveBeenCalledWith(42);
});

it('requires a rejection reason and sends a single real decision after correction', async () => {
  state.search += '&taskId=42'; render(tree()); await screen.findByText('Original payload');
  fireEvent.click(screen.getByRole('button', { name: '退回申请' })); fireEvent.click(confirmButton());
  await screen.findByText(t('approval.approvalDetail.commentRejectRequired')); expect(api.reject).not.toHaveBeenCalled();
  fireEvent.change(screen.getByLabelText('审批意见'), { target: { value: ' Needs correction ' } }); fireEvent.click(confirmButton());
  await waitFor(() => expect(api.success).toHaveBeenCalled()); expect(api.reject).toHaveBeenCalledTimes(1); expect(api.reject).toHaveBeenCalledWith(42, 2, 'Needs correction');
});

it('refuses changed server versions before dispatching a write', async () => {
  state.search += '&taskId=42'; render(tree()); await screen.findByText('Original payload');
  api.detail.mockResolvedValueOnce({ ...detail(), task: { ...detail().task, version: 3 } });
  fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(confirmButton());
  await waitFor(() => expect(api.error).toHaveBeenCalledWith(t('approval.approvalDetail.changedBeforeAction'))); expect(api.approve).not.toHaveBeenCalled();
});

it('suppresses a delayed preflight after permission revocation and clears the old data', async () => {
  state.search += '&taskId=42'; const view = render(tree()); await screen.findByText('Original payload');
  const late = deferred<GenericApprovalTaskDetail>(); api.detail.mockReturnValueOnce(late.promise);
  fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(confirmButton());
  await waitFor(() => expect(api.detail).toHaveBeenCalledWith(42));
  auth.user = { ...auth.user, permissions: [], permissionVersion: 2 }; view.rerender(tree());
  await act(async () => late.resolve(detail())); expect(screen.queryByText('Original payload')).toBeNull(); expect(api.approve).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});

it('does not retry an uncertain write or claim a successful result', async () => {
  state.search += '&taskId=42'; render(tree()); await screen.findByText('Original payload'); api.approve.mockRejectedValueOnce(new TypeError('network lost'));
  fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(confirmButton());
  await waitFor(() => expect(api.error).toHaveBeenCalledWith(t('approval.approvalDetail.resultUnknown'))); expect(api.approve).toHaveBeenCalledTimes(1); expect(api.success).not.toHaveBeenCalled();
});

it('keeps unsupported rule approval disabled while allowing a real rejection', async () => {
  state.search += '&taskId=42'; api.detail.mockResolvedValue({ ...detail(), canApprove: false, blockedReason: 'Additional process required' }); render(tree()); await screen.findByText('Additional process required');
  expect(screen.getByRole('button', { name: '审批通过' })).toBeDisabled(); expect(screen.getByRole('button', { name: '退回申请' })).not.toBeDisabled();
});

it('loads the second frozen field page instead of truncating a large form', async () => {
  state.search += '&taskId=42'; api.detail.mockImplementation(async (id: number, page = 1) => ({ ...detail(id), totalFields: 60, fieldPage: page,
    fields: page === 1 ? detail().fields : [{ name: 'last', label: 'Last frozen field', type: 'text', values: ['Historic final value'], redacted: false }] }));
  render(tree()); await screen.findByText('Original payload'); fireEvent.click(screen.getByTitle('2'));
  await screen.findByText('Historic final value'); expect(api.detail).toHaveBeenCalledWith(42, 2); expect(screen.queryByText('Original payload')).toBeNull();
});

it('does not claim success when a write receipt has the wrong task or stale version', async () => {
  state.search += '&taskId=42'; api.approve.mockResolvedValueOnce({ ...detail(), task: { ...detail().task, status: 'APPROVED' } });
  render(tree()); await screen.findByText('Original payload'); fireEvent.click(screen.getByRole('button', { name: '审批通过' })); fireEvent.click(confirmButton());
  await waitFor(() => expect(api.error).toHaveBeenCalledWith(t('approval.genericApplications.invalidResult'))); expect(api.success).not.toHaveBeenCalled(); expect(api.approve).toHaveBeenCalledTimes(1);
});

it('shows retryable failure and never reuses a previous list as a successful refresh', async () => {
  render(tree()); await screen.findByText('Frozen request'); api.mine.mockRejectedValueOnce(new Error('unavailable'));
  await act(async () => { await expect(state.handlers['ui.refreshPage']!({ pageId: 'todo', code: 'ui.refreshPage', payload: {} })).rejects.toThrow(); });
  expect(screen.queryByText('Frozen request')).toBeNull(); fireEvent.click(screen.getByRole('button', { name: /重\s*试/ })); await screen.findByText('Frozen request');
});

it('uses a full-width drawer and responsive cards at the shared mobile breakpoint', async () => {
  const normal = window.matchMedia(''); vi.spyOn(window, 'matchMedia').mockImplementation(query => ({ ...normal, media: query, matches: query.includes('720px') }));
  state.search += '&taskId=42'; render(tree()); await screen.findByText('Original payload');
  expect(document.querySelector<HTMLElement>('.ant-drawer-content-wrapper')?.style.width).toBe('100%');
  expect(document.querySelector('.ant-table')).toBeNull();
});
