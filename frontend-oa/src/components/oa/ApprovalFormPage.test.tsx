import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MemoryRouter, useLocation, useNavigate, type NavigateFunction } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import type { ApprovalApplication, ApprovalApplicationTemplate } from '@/lib/approvalEngineApi';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import { PageAgentContextProvider } from './PageAgentContext';

const permissions = ['route:approval-form', 'route:approval-start', 'approval:create', 'approval:submit', 'approval:cancel', 'approval:mine:read', 'route:my-applications'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[], roles: ['USER'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ listApplicationTemplates: vi.fn(), getApplication: vi.fn(), createDraft: vi.fn(), updateDraft: vi.fn(), submitApplication: vi.fn(), submitDraft: vi.fn(), cancelDraft: vi.fn(), success: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: { success: api.success, error: api.error } }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import ApprovalFormPage from './ApprovalFormPage';

function template(key = 'travel'): ApprovalApplicationTemplate { return { formId: 1, formKey: key, formName: `${key} form`, version: 2,
  fields: [{ name: 'reason', label: 'Reason', type: 'text', required: true, width: 'full', options: [] },
    { name: 'amount', label: 'Amount', type: 'money', required: false, width: 'full', options: [], min: 0 },
    { name: 'kind', label: 'Kind', type: 'select', required: false, width: 'full', options: ['TRAVEL', 'OTHER'] },
    { name: 'time', label: 'Time', type: 'time', required: false, width: 'full', options: [] }],
  process: { id: 2, processKey: 'travel-process', processName: 'Configured process', version: 1, nodes: [{ nodeName: 'Manager approval', approveType: 'USER' }] } }; }
function application(version = 1, status: ApprovalApplication['status'] = 'DRAFT'): ApprovalApplication { return { id: 11, applicantUserId: 7, applicantName: 'Synthetic owner',
  formKey: 'travel', formName: 'travel form', title: 'Synthetic request', dataJson: '{"reason":"saved reason","time":"08:30"}', status, version,
  canEditDraft: status === 'DRAFT', canCancel: status === 'DRAFT', canWithdraw: false, canRemind: false, overdue: false, reminderCount: 0,
  createdAt: '2026-11-03T09:00:00', updatedAt: '2026-11-03T09:00:00' }; }
let goto: NavigateFunction;
function Location() { const location = useLocation(); goto = useNavigate(); return <output aria-label="form-location">{location.pathname}{location.search}</output>; }
const tree = (path = '/oa/approval-form?formKey=travel') => <MemoryRouter initialEntries={[path]}><ConfigProvider locale={zhCN}><App>
  <PageAgentContextProvider pageId="approval-form"><ApprovalFormPage /><Location /></PageAgentContextProvider></App></ConfigProvider></MemoryRouter>;
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
async function fill(values: Record<string, string | number> = { reason: 'Actual typed reason' }) {
  await act(async () => { await ui.handlers['ui.fillForm']!({ pageId: 'approval-form', code: 'ui.fillForm', payload: { values, formContext: { sessionId: 'fixture' } } }); });
}
async function submitDialog() { fireEvent.click(screen.getByRole('button', { name: '提交审批' })); return screen.findByRole('dialog'); }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: [...permissions] }; bindAuthResponseIdentity('generic-form-owner', 'allowed');
  api.listApplicationTemplates.mockImplementation(({ formKey }: { formKey: string }) => Promise.resolve({ records: [template(formKey)], total: 1 }));
  api.getApplication.mockResolvedValue(application()); api.createDraft.mockResolvedValue(application());
  api.updateDraft.mockImplementation((_id: number, payload: { version: number }) => Promise.resolve(application(payload.version + 1)));
  api.submitDraft.mockImplementation((_id: number, version: number) => Promise.resolve(application(version + 1, 'PENDING')));
  api.submitApplication.mockResolvedValue(application(1, 'PENDING')); api.cancelDraft.mockResolvedValue(application(2, 'CANCELLED'));
});
afterEach(cleanup);

it('loads applicant metadata and uses real option, money and time controls for UI filling and preview', async () => {
  const view = render(tree()); await screen.findByLabelText('Reason');
  await fill({ reason: 'Private preview', kind: 'TRAVEL', amount: '9007199254740993.123456789', time: '08:30' });
  expect(screen.getByLabelText(/^Time/)).toHaveValue('08:30');
  await act(async () => { await ui.handlers['ui.previewSubmission']!({ pageId: 'approval-form', code: 'ui.previewSubmission', payload: { formContext: { sessionId: 'fixture' } } }); });
  const dialog = await screen.findByRole('dialog'); expect(within(dialog).getByText('9007199254740993.123456789')).toBeInTheDocument();
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(screen.queryByLabelText('Reason')).toBeNull(); expect(api.createDraft).not.toHaveBeenCalled();
});

it('does not replace a new template with an older response after A to B to A navigation', async () => {
  const old = deferred<{ records: ApprovalApplicationTemplate[]; total: number }>();
  api.listApplicationTemplates.mockReturnValueOnce(old.promise);
  render(tree()); await waitFor(() => expect(api.listApplicationTemplates).toHaveBeenCalledTimes(1));
  await act(async () => { goto('/oa/approval-form?formKey=other'); }); await screen.findByText('other form');
  await act(async () => { goto('/oa/approval-form?formKey=travel'); }); await screen.findByText('travel form');
  await act(async () => { old.resolve({ records: [{ ...template(), formName: 'Old private title' }], total: 1 }); });
  expect(screen.queryByText('Old private title')).toBeNull();
});

it.each(['-1', '1.5', 'invalid', '9007199254740993'])('rejects invalid draft identifiers before requesting or creating data: %s', async id => {
  render(tree(`/oa/approval-form?formKey=travel&draftId=${id}`));
  await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(api.getApplication).not.toHaveBeenCalled(); expect(api.listApplicationTemplates).not.toHaveBeenCalled();
  expect(screen.queryByRole('button', { name: '保存草稿' })).toBeNull();
});

it('refuses another applicant draft even if the general detail endpoint permits an auditor', async () => {
  api.getApplication.mockResolvedValue({ ...application(), applicantUserId: 99 });
  render(tree('/oa/approval-form?formKey=travel&draftId=11')); await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(screen.queryByDisplayValue('saved reason')).toBeNull(); expect(screen.queryByLabelText('Reason')).toBeNull();
});

it('locks a save before dispatch and ignores its late receipt after losing authority', async () => {
  const pending = deferred<ApprovalApplication>(); api.createDraft.mockReturnValue(pending.promise);
  const view = render(tree()); await screen.findByLabelText('Reason'); await fill();
  const save = screen.getByRole('button', { name: '保存草稿' }); fireEvent.click(save); fireEvent.click(save);
  await waitFor(() => expect(api.createDraft).toHaveBeenCalledTimes(1)); expect(screen.getByLabelText('Reason')).toBeDisabled();
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await act(async () => { pending.resolve(application()); });
  expect(api.success).not.toHaveBeenCalled(); expect(screen.getByLabelText('form-location').textContent).not.toContain('draftId=');
});

it('keeps the acknowledged draft version after submission fails and retries only on a new human confirmation', async () => {
  api.submitDraft.mockRejectedValueOnce(new Error('Synthetic submit failure'));
  render(tree('/oa/approval-form?formKey=travel&draftId=11')); await screen.findByDisplayValue('saved reason');
  let dialog = await submitDialog(); expect(within(dialog).getByText('saved reason')).toBeInTheDocument();
  fireEvent.click(within(dialog).getByRole('button', { name: '确认提交' }));
  await waitFor(() => expect(api.submitDraft).toHaveBeenCalledWith(11, 2));
  await waitFor(() => expect(api.error).toHaveBeenCalled()); expect(api.success).not.toHaveBeenCalled();
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  dialog = await submitDialog(); fireEvent.click(within(dialog).getByRole('button', { name: '确认提交' }));
  await waitFor(() => expect(api.updateDraft).toHaveBeenLastCalledWith(11, expect.objectContaining({ version: 2 })));
  await waitFor(() => expect(api.submitDraft).toHaveBeenLastCalledWith(11, 3));
  await waitFor(() => expect(screen.getByLabelText('form-location').textContent).toBe('/oa/my-applications?kind=generic&id=11'));
});

it('stops the second write when identity changes while an existing draft update is in flight', async () => {
  const pending = deferred<ApprovalApplication>(); api.updateDraft.mockReturnValueOnce(pending.promise);
  const view = render(tree('/oa/approval-form?formKey=travel&draftId=11')); await screen.findByDisplayValue('saved reason');
  fireEvent.click(within(await submitDialog()).getByRole('button', { name: '确认提交' }));
  await waitFor(() => expect(api.updateDraft).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, id: 8, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await act(async () => { pending.resolve(application(2)); });
  expect(api.submitDraft).not.toHaveBeenCalled(); expect(api.success).not.toHaveBeenCalled();
});

it('submits the values shown in confirmation once and navigates from the actual receipt', async () => {
  const pending = deferred<ApprovalApplication>(); api.submitApplication.mockReturnValueOnce(pending.promise);
  render(tree()); await screen.findByLabelText('Reason'); await fill({ reason: 'Exact confirmation text', amount: '123.45' });
  const dialog = await submitDialog(); expect(within(dialog).getByText('Exact confirmation text')).toBeInTheDocument();
  expect(screen.getByLabelText('Reason')).toBeDisabled(); expect(ui.handlers['ui.fillForm']).toBeUndefined();
  const confirm = within(dialog).getByRole('button', { name: '确认提交' }); fireEvent.click(confirm); fireEvent.click(confirm);
  await waitFor(() => expect(api.submitApplication).toHaveBeenCalledTimes(1));
  expect(api.submitApplication).toHaveBeenCalledWith({ formKey: 'travel', processKey: 'travel-process', formData: { reason: 'Exact confirmation text', amount: '123.45' } });
  await act(async () => { pending.resolve(application(1, 'PENDING')); });
  await waitFor(() => expect(screen.getByLabelText('form-location').textContent).toContain('kind=generic&id=11'));
});

it('does not turn a mismatched save receipt into a success or a detail navigation', async () => {
  api.createDraft.mockResolvedValue({ ...application(), id: 999, formKey: 'wrong-form' });
  render(tree()); await screen.findByLabelText('Reason'); await fill(); fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
  await waitFor(() => expect(api.error).toHaveBeenCalled()); expect(api.success).not.toHaveBeenCalled();
  expect(screen.getByLabelText('form-location').textContent).not.toContain('draftId=');
});

it('destroys an owned cancellation confirmation when authority is revoked before confirmation', async () => {
  const view = render(tree('/oa/approval-form?formKey=travel&draftId=11')); await screen.findByDisplayValue('saved reason');
  fireEvent.click(screen.getByRole('button', { name: '取消草稿' })); await screen.findByRole('dialog');
  auth.user = { ...auth.user, permissionVersion: 2, permissions: permissions.filter(code => code !== 'approval:cancel') }; view.rerender(tree());
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(api.cancelDraft).not.toHaveBeenCalled();
});

it('loads and restores its fields under root StrictMode effect replay', async () => {
  render(tree('/oa/approval-form?formKey=travel&draftId=11'), { reactStrictMode: true });
  await screen.findByDisplayValue('saved reason'); expect(screen.getByLabelText(/^Time/)).toHaveValue('08:30');
  expect(api.listApplicationTemplates).toHaveBeenCalledTimes(2);
});
