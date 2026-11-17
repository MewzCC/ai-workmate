import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { t } from 'i18next';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import type { GenericApprovalTaskDetail } from '@/lib/genericApprovalTaskApi';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, roles: ['USER'], permissions: ['todo:read', 'approval:act'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ mine: vi.fn(), detail: vi.fn(), approve: vi.fn(), reject: vi.fn(), participants: vi.fn(), transfer: vi.fn(), copyTo: vi.fn(), addSign: vi.fn(), push: vi.fn(), error: vi.fn(), success: vi.fn() }));
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
function ContextProbe() { return <output data-testid="generic-task-context">{JSON.stringify(usePageAgentContextSnapshot())}</output>; }
const contextTree = () => <PageAgentContextProvider pageId="todo"><GenericApprovalInbox /><ContextProbe /></PageAgentContextProvider>;
const publishedContext = () => JSON.parse(screen.getByTestId('generic-task-context').textContent || '{}') as Record<string, unknown>;
beforeEach(() => {
  vi.clearAllMocks(); state.search = 'kind=generic';
  api.participants.mockResolvedValue({ records: [{ id: 9, name: 'Qualified recipient', canApprove: true }], total: 1 });
  auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: ['todo:read', 'approval:act'] }; bindAuthResponseIdentity('generic-actor', 'act');
  api.mine.mockResolvedValue({ records: [detail().task], total: 1, page: 1, size: 20 }); api.detail.mockImplementation(async (id: number, page = 1) => ({ ...detail(id), fieldPage: page }));
  api.approve.mockImplementation(async () => { const receipt = { ...detail(), task: { ...detail().task, status: 'APPROVED', version: 3 }, canApprove: false, canReject: false }; api.detail.mockResolvedValue(receipt); return receipt; });
  api.reject.mockImplementation(async () => { const receipt = { ...detail(), applicationStatus: 'REJECTED', instanceStatus: 'COMPLETED', task: { ...detail().task, status: 'REJECTED', version: 3 }, canApprove: false, canReject: false }; api.detail.mockResolvedValue(receipt); return receipt; });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('exposes real collaboration controls only when the server grants the current task capabilities', async () => {
  state.search += '&taskId=42';
  api.detail.mockResolvedValue({ ...detail(), canTransfer: true, canCopy: true });
  render(tree());
  const transfer = await screen.findByRole('button', { name: t('approval.genericTasks.transferButton') });
  fireEvent.click(transfer);
  expect(await screen.findByLabelText(t('approval.genericTasks.recipient'))).toBeInTheDocument();
  expect(api.transfer).not.toHaveBeenCalled();
});

it.each(['PRE', 'POST'] as const)('confirms one %s add-sign command without making an approval decision', async mode => {
  state.search += '&taskId=42';
  api.detail.mockResolvedValue({ ...detail(), canAddSign: true, canTransfer: true, canCopy: true });
  api.addSign.mockResolvedValue({ applicationId: 9, taskId: 42, version: 3, status: mode === 'PRE' ? 'WAITING' : 'PENDING', applicationStatus: 'PENDING',
    action: 'ADD_SIGN', mode, targetUserId: 9, addedTaskId: 77, addedTaskStatus: mode === 'PRE' ? 'PENDING' : 'WAITING', addedTaskVersion: 0, activeTaskId: mode === 'PRE' ? 77 : 42 });
  render(tree()); fireEvent.click(await screen.findByRole('button', { name: t(mode === 'PRE' ? 'approval.genericTasks.preSignButton' : 'approval.genericTasks.postSignButton') }));
  fireEvent.mouseDown(await screen.findByLabelText(t('approval.genericTasks.recipient'))); fireEvent.click(await screen.findByText('Qualified recipient'));
  fireEvent.change(screen.getByLabelText(t('approval.genericTasks.collaborationReason')), { target: { value: 'Explicit extra review' } });
  fireEvent.click(confirmButton());
  await waitFor(() => expect(api.addSign).toHaveBeenCalledWith(42, { targetUserId: 9, version: 2, mode, reason: 'Explicit extra review' }));
  await waitFor(() => expect(api.success).toHaveBeenCalledWith(t(mode === 'PRE' ? 'approval.genericTasks.preSignScheduled' : 'approval.genericTasks.postSignScheduled')));
  expect(api.approve).not.toHaveBeenCalled(); expect(api.reject).not.toHaveBeenCalled(); expect(api.transfer).not.toHaveBeenCalled();
});
it('shows a suspended root and its real add-sign queue without exposing approval controls', async () => {
  state.search += '&taskId=42'; api.detail.mockResolvedValue({ ...detail(), task: { ...detail().task, status: 'WAITING', version: 3 },
    canApprove: false, canReject: false, canTransfer: false, canCopy: false, canAddSign: false,
    addSignTasks: [{ taskId: 77, parentTaskId: 42, mode: 'PRE', assigneeUserId: 9, assigneeName: 'Additional reviewer', status: 'PENDING', version: 0 }] });
  render(tree()); await screen.findByText(t('approval.genericTasks.addSignQueueTitle'));
  expect(await screen.findByText('Additional reviewer')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: t('approval.approvalDetail.approveButton') })).toBeNull();
  expect(screen.queryByRole('button', { name: t('approval.genericTasks.preSignButton') })).toBeNull();
});

it('suppresses a late add-sign preflight after permission revocation', async () => {
  state.search += '&taskId=42'; const current = { ...detail(), canAddSign: true };
  api.detail.mockResolvedValue(current); const view = render(tree());
  fireEvent.click(await screen.findByRole('button', { name: t('approval.genericTasks.preSignButton') }));
  fireEvent.mouseDown(await screen.findByLabelText(t('approval.genericTasks.recipient'))); fireEvent.click(await screen.findByText('Qualified recipient'));
  fireEvent.change(screen.getByLabelText(t('approval.genericTasks.collaborationReason')), { target: { value: 'Explicit extra review' } });
  const late = deferred<GenericApprovalTaskDetail>(); api.detail.mockReturnValueOnce(late.promise);
  const before = api.detail.mock.calls.length; fireEvent.click(confirmButton());
  await waitFor(() => expect(api.detail.mock.calls.length).toBeGreaterThan(before));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await act(async () => late.resolve(current));
  expect(api.addSign).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});

it.each(['wrong-order', 'network-loss'])('does not retry or report add-sign success for %s', async failure => {
  state.search += '&taskId=42'; api.detail.mockResolvedValue({ ...detail(), canAddSign: true });
  if (failure === 'network-loss') api.addSign.mockRejectedValueOnce(new TypeError('connection lost'));
  else api.addSign.mockResolvedValueOnce({ applicationId: 9, taskId: 42, version: 3, status: 'WAITING', applicationStatus: 'PENDING',
    action: 'ADD_SIGN', mode: 'PRE', targetUserId: 9, addedTaskId: 77, addedTaskStatus: 'PENDING', addedTaskVersion: 0, activeTaskId: 42 });
  render(tree()); fireEvent.click(await screen.findByRole('button', { name: t('approval.genericTasks.preSignButton') }));
  fireEvent.mouseDown(await screen.findByLabelText(t('approval.genericTasks.recipient'))); fireEvent.click(await screen.findByText('Qualified recipient'));
  fireEvent.change(screen.getByLabelText(t('approval.genericTasks.collaborationReason')), { target: { value: 'Explicit extra review' } });
  fireEvent.click(confirmButton());
  await waitFor(() => expect(api.error).toHaveBeenCalledWith(t('approval.approvalDetail.resultUnknown')));
  expect(api.addSign).toHaveBeenCalledTimes(1); expect(api.success).not.toHaveBeenCalled(); expect(api.push).not.toHaveBeenCalled();
});

it('confirms the explicit recipient and reason with the current task version before transferring', async () => {
  state.search += '&taskId=42'; api.detail.mockResolvedValue({ ...detail(), canTransfer: true, canCopy: true });
  api.transfer.mockResolvedValue({ applicationId: 9, taskId: 42, version: 3, status: 'PENDING', applicationStatus: 'PENDING', action: 'TRANSFER', originalAssigneeUserId: 7, targetUserId: 9, assigneeUserId: 9 });
  render(tree()); fireEvent.click(await screen.findByRole('button', { name: t('approval.genericTasks.transferButton') }));
  fireEvent.mouseDown(await screen.findByLabelText(t('approval.genericTasks.recipient')));
  fireEvent.click(await screen.findByText('Qualified recipient'));
  fireEvent.change(screen.getByLabelText(t('approval.genericTasks.collaborationReason')), { target: { value: 'Explicit user reason' } });
  fireEvent.click(confirmButton());
  await waitFor(() => expect(api.transfer).toHaveBeenCalledWith(42, { targetUserId: 9, version: 2, reason: 'Explicit user reason' }));
  await waitFor(() => expect(api.push).toHaveBeenCalledWith('/oa/todo?kind=generic'));
  expect(api.approve).not.toHaveBeenCalled(); expect(api.reject).not.toHaveBeenCalled();
});

it('suppresses a collaboration preflight after live permission revocation', async () => {
  state.search += '&taskId=42'; const current = { ...detail(), canTransfer: true, canCopy: true };
  api.detail.mockResolvedValue(current); const view = render(tree());
  fireEvent.click(await screen.findByRole('button', { name: t('approval.genericTasks.copyButton') }));
  fireEvent.mouseDown(await screen.findByLabelText(t('approval.genericTasks.recipient'))); fireEvent.click(await screen.findByText('Qualified recipient'));
  fireEvent.change(screen.getByLabelText(t('approval.genericTasks.collaborationReason')), { target: { value: 'Explicit copy reason' } });
  const late = deferred<GenericApprovalTaskDetail>(); api.detail.mockReturnValueOnce(late.promise);
  const before = api.detail.mock.calls.length; fireEvent.click(confirmButton());
  await waitFor(() => expect(api.detail.mock.calls.length).toBeGreaterThan(before));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await act(async () => late.resolve(current));
  expect(api.copyTo).not.toHaveBeenCalled(); expect(api.transfer).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});

it('does not claim collaboration success or retry a receipt for a different recipient', async () => {
  state.search += '&taskId=42'; api.detail.mockResolvedValue({ ...detail(), canTransfer: true, canCopy: true });
  api.copyTo.mockResolvedValue({ applicationId: 9, taskId: 42, version: 3, status: 'PENDING', applicationStatus: 'PENDING', action: 'CC', originalAssigneeUserId: 7, targetUserId: 99, assigneeUserId: 7 });
  render(tree()); fireEvent.click(await screen.findByRole('button', { name: t('approval.genericTasks.copyButton') }));
  fireEvent.mouseDown(await screen.findByLabelText(t('approval.genericTasks.recipient'))); fireEvent.click(await screen.findByText('Qualified recipient'));
  fireEvent.change(screen.getByLabelText(t('approval.genericTasks.collaborationReason')), { target: { value: 'Explicit copy reason' } });
  fireEvent.click(confirmButton()); await waitFor(() => expect(api.error).toHaveBeenCalledWith(t('approval.approvalDetail.resultUnknown')));
  expect(api.copyTo).toHaveBeenCalledTimes(1); expect(api.success).not.toHaveBeenCalled(); expect(api.push).not.toHaveBeenCalled();
});

it('publishes only a loaded task ID and version and removes them when details close', async () => {
  state.search += '&taskId=42'; const late = deferred<GenericApprovalTaskDetail>(); api.detail.mockReturnValueOnce(late.promise);
  const view = render(contextTree()); await waitFor(() => expect(api.detail).toHaveBeenCalledWith(42, 1));
  expect(publishedContext().taskId).toBeUndefined();
  await act(async () => late.resolve(detail())); await screen.findByText('Original payload');
  await waitFor(() => expect(publishedContext()).toMatchObject({ taskId: 42, taskVersion: 2 }));
  expect(JSON.stringify(publishedContext())).not.toContain('Original payload');
  state.search = 'kind=generic'; view.rerender(contextTree());
  await waitFor(() => expect(publishedContext().taskId).toBeUndefined()); expect(publishedContext().taskVersion).toBeUndefined();
});

it('never lets a late previous task overwrite the current task context and clears it on revocation', async () => {
  state.search += '&taskId=42'; const late = deferred<GenericApprovalTaskDetail>(); api.detail.mockReturnValueOnce(late.promise);
  const view = render(contextTree()); await waitFor(() => expect(api.detail).toHaveBeenCalledWith(42, 1));
  state.search = 'kind=generic&taskId=43'; api.detail.mockResolvedValue({ ...detail(43), task: { ...detail(43).task, version: 5 } }); view.rerender(contextTree());
  await waitFor(() => expect(publishedContext()).toMatchObject({ taskId: 43, taskVersion: 5 }));
  await act(async () => late.resolve(detail())); expect(publishedContext()).toMatchObject({ taskId: 43, taskVersion: 5 });
  auth.user = { ...auth.user, permissions: [], permissionVersion: 2 }; view.rerender(contextTree());
  await waitFor(() => expect(publishedContext().taskId).toBeUndefined()); expect(publishedContext().taskVersion).toBeUndefined();
});

it.each([-1, 1.5, Number.NaN])('does not publish an invalid task version %s', async version => {
  state.search += '&taskId=42'; api.detail.mockResolvedValue({ ...detail(), task: { ...detail().task, version } });
  render(contextTree()); await screen.findByText('Original payload');
  expect(publishedContext().taskId).toBeUndefined(); expect(publishedContext().taskVersion).toBeUndefined();
});

it('uses real URL tabs and numeric filters without mounting the leave query', async () => {
  render(tree(true)); await screen.findByText('Frozen request');
  expect(state.views).toHaveBeenCalledWith('generic', ['all', 'leave', 'generic'], expect.any(Function));
  await act(async () => { await state.handlers['ui.applyFilter']!({ pageId: 'todo', code: 'ui.applyFilter', payload: { filters: { status: 'APPROVED', page: 2, size: 50, from: '2026-11-01', to: '2026-11-03' } } }); });
  await waitFor(() => expect(api.mine).toHaveBeenLastCalledWith({ status: 'APPROVED', page: 2, size: 50, from: '2026-11-01T00:00:00', to: '2026-11-03T00:00:00' }));
  const invalid: Record<string, string | number>[] = [{ size: 51 }, { page: 0 }, { status: 'WAITING' }];
  for (const filters of invalid)
    await expect(async () => state.handlers['ui.applyFilter']!({ pageId: 'todo', code: 'ui.applyFilter', payload: { filters } })).rejects.toThrow();
  fireEvent.click(screen.getByRole('tab', { name: t('approval.genericTasks.leaveTab') })); expect(api.push).toHaveBeenCalledWith('/oa/todo?kind=leave');
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
