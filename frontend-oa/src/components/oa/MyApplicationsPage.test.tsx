import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { PageAgentContextProvider } from './PageAgentContext';
import { bindAuthResponseIdentity } from '@/lib/authEvents';

const permissions = ['leave:read:self', 'leave:create', 'leave:withdraw', 'leave:remind', 'route:leave-application',
  'approval:mine:read', 'route:approval-start', 'approval:create', 'approval:submit', 'approval:cancel', 'approval:withdraw', 'approval:reopen', 'approval:remind', 'route:approval-form'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[], roles: ['USER'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ listMyApplications: vi.fn(), ownApplicationFields: vi.fn(), submitDraft: vi.fn(), cancelDraft: vi.fn(),
  withdrawApplication: vi.fn(), reopenApplication: vi.fn(), remindApplication: vi.fn(), mine: vi.fn(), detail: vi.fn(), withdraw: vi.fn(), remind: vi.fn(), success: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), leaveApi: { mine: api.mine, detail: api.detail, withdraw: api.withdraw, remind: api.remind } }));
vi.mock('@/lib/antdMessage', () => ({ message: { success: api.success, error: api.error } }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; }, useRegisterPageUiViews: () => {} }));
import MyApplicationsPage from './MyApplicationsPage';

const application = (version = 1, status = 'DRAFT') => ({ id: 11, applicantUserId: 7, applicantName: 'Synthetic owner', formKey: 'test-form', formName: 'Generic sample',
  title: 'Sample', dataJson: '{}', status, version, overdue: false, reminderCount: 0, canRemind: false, canWithdraw: status === 'PENDING', canEditDraft: status === 'DRAFT', canCancel: status === 'DRAFT', createdAt: '2026-11-03T09:00:00', updatedAt: '2026-11-03T09:00:00' });
const fields = (id = 11, page = 1) => ({ application: { applicationId: id, formName: 'Generic sample', formKey: 'test-form', status: 'APPROVED', version: 2 },
  schemaSource: 'SNAPSHOT', formVersion: 7, fields: [{ name: 'reason', label: 'Reason', type: 'text', values: [`private-page-${page}`], redacted: false }], fieldsTotal: 60, fieldPage: page });
const leave = () => ({ id: 12, applicantUserId: 7, applicantName: 'Synthetic owner', leaveType: 'PERSONAL', status: 'PENDING', version: 1,
  createdAt: '2026-11-03T09:00:00', startDate: '2026-11-04', endDate: '2026-11-04', startPeriod: 'AM', endPeriod: 'PM',
  durationDays: 1, reason: 'private-leave', canEdit: false, canWithdraw: true, canRemind: true, taskId: 22, reminderCount: 0 });
function Location() { const location = useLocation(); return <output aria-label="application-location">{location.pathname}{location.search}</output>; }
const tree = (path = '/oa/my-applications?kind=generic') => <MemoryRouter initialEntries={[path]}><ConfigProvider locale={zhCN}><App>
  <PageAgentContextProvider pageId="my-applications"><MyApplicationsPage /><Location /></PageAgentContextProvider></App></ConfigProvider></MemoryRouter>;
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => {
  vi.clearAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: [...permissions] }; bindAuthResponseIdentity('application-user', 'allowed');
  api.listMyApplications.mockResolvedValue({ records: [application()], total: 1 }); api.ownApplicationFields.mockImplementation((id: number, page = 1) => Promise.resolve(fields(id, page)));
  api.mine.mockResolvedValue({ records: [leave()], total: 1 }); api.detail.mockResolvedValue(leave());
});
afterEach(cleanup);

it('uses real generic and leave views without leaking an unauthorized tab', async () => {
  const view = render(tree()); await screen.findByText('Generic sample #11');
  fireEvent.click(screen.getByRole('tab', { name: '请假申请' }));
  await waitFor(() => expect(api.mine).toHaveBeenCalledWith({ status: undefined, page: 1, size: 20 }));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: ['leave:read:self'] }; view.rerender(tree());
  expect(screen.queryByRole('tab', { name: '通用申请' })).toBeNull();
  expect(screen.queryByText('Generic sample #11')).toBeNull();
});

it('applies generic filters and validates an actual self-owned detail before routing', async () => {
  render(tree()); await screen.findByText('Generic sample #11');
  await act(async () => { await ui.handlers['ui.applyFilter']!({ code: 'ui.applyFilter', pageId: 'my-applications', payload: { filters: { status: 'PENDING', formKey: 'travel', page: 2, size: 50 } } }); });
  await waitFor(() => expect(api.listMyApplications).toHaveBeenLastCalledWith({ status: 'PENDING', formKey: 'travel', page: 2, size: 50 }));
  await act(async () => { await ui.handlers['ui.openDetail']!({ code: 'ui.openDetail', pageId: 'my-applications', payload: { recordId: 42 } }); });
  expect(screen.getByLabelText('application-location').textContent).toBe('/oa/my-applications?kind=generic&id=42');
  expect(api.ownApplicationFields).toHaveBeenCalledWith(42, 1);
});

it('pages frozen fields and hides private detail after permission revocation', async () => {
  const view = render(tree('/oa/my-applications?kind=generic&id=11'));
  await screen.findByText('private-page-1');
  fireEvent.click(within(screen.getByRole('dialog')).getByTitle('2'));
  await screen.findByText('private-page-2');
  auth.user = { ...auth.user, permissionVersion: 2, permissions: ['leave:read:self'] }; view.rerender(tree());
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(screen.queryByText('private-page-2')).toBeNull();
});

it('does not submit twice and ignores the old write completion after losing authority', async () => {
  const pending = deferred<ReturnType<typeof application>>(); api.submitDraft.mockReturnValue(pending.promise);
  const view = render(tree()); await screen.findByText('Generic sample #11');
  fireEvent.click(screen.getByRole('button', { name: '提交审批' }));
  const dialog = await screen.findByRole('dialog'); const confirm = within(dialog).getByRole('button', { name: /确\s*定/ });
  fireEvent.click(confirm); fireEvent.click(confirm);
  await waitFor(() => expect(api.submitDraft).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: ['leave:read:self'] }; view.rerender(tree());
  await act(async () => { pending.resolve(application(2, 'PENDING')); });
  expect(api.success).not.toHaveBeenCalled();
});

it('checks version and status receipts before showing a business success', async () => {
  api.submitDraft.mockResolvedValue(application(1, 'DRAFT'));
  render(tree()); await screen.findByText('Generic sample #11');
  fireEvent.click(screen.getByRole('button', { name: '提交审批' }));
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /确\s*定/ }));
  await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(api.success).not.toHaveBeenCalled();
});

it('rejects another applicant in list and detail responses without presenting their data', async () => {
  api.listMyApplications.mockResolvedValue({ records: [{ ...application(), applicantUserId: 99, formName: 'foreign private row' }], total: 1 });
  api.ownApplicationFields.mockResolvedValue(fields(99));
  render(tree('/oa/my-applications?kind=generic&id=11'));
  await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(screen.queryByText('foreign private row #11')).toBeNull(); expect(screen.queryByText('private-page-1')).toBeNull();
});

it('supports numeric leave filters and refuses a late cross-scope detail navigation', async () => {
  const view = render(tree('/oa/my-applications')); await waitFor(() => expect(api.mine).toHaveBeenCalledTimes(1));
  await act(async () => { await ui.handlers['ui.applyFilter']!({ code: 'ui.applyFilter', pageId: 'my-applications', payload: { filters: { page: 2, size: 50, status: 'PENDING' } } }); });
  await waitFor(() => expect(api.mine).toHaveBeenLastCalledWith({ status: 'PENDING', page: 2, size: 50 }));
  const pending = deferred<ReturnType<typeof leave>>(); api.detail.mockReturnValueOnce(pending.promise);
  const rejected = expect(ui.handlers['ui.openDetail']!({ code: 'ui.openDetail', pageId: 'my-applications', payload: { recordId: 12 } })).rejects.toThrow();
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree('/oa/my-applications'));
  await act(async () => { pending.resolve(leave()); await rejected; });
  expect(screen.getByLabelText('application-location').textContent).toBe('/oa/my-applications');
});
