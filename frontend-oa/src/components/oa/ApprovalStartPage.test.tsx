import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { ApprovalApplication, ApprovalApplicationTemplate } from '@/lib/approvalEngineApi';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import { PageAgentContextProvider } from './PageAgentContext';

const permissions = ['route:approval-start', 'approval:create', 'route:approval-form', 'approval:mine:read',
  'approval:withdraw', 'approval:reopen', 'approval:remind', 'leave:create', 'route:leave-application', 'route:my-applications'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[], roles: ['USER'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ listApplicationTemplates: vi.fn(), listMyApplications: vi.fn(), getApplication: vi.fn(), ownApplicationFields: vi.fn(),
  withdrawApplication: vi.fn(), reopenApplication: vi.fn(), remindApplication: vi.fn(), success: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: { success: api.success, error: api.error } }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import ApprovalStartPage from './ApprovalStartPage';

const template = (id = 1, key = 'custom-travel'): ApprovalApplicationTemplate => ({ formId: id, formKey: key, formName: `Template ${id}`, version: 1, fields: [],
  process: { id: 3, processKey: 'test-process', processName: 'Configured process', version: 1, nodes: [] } });
const application = (id = 12, status: ApprovalApplication['status'] = 'PENDING', version = 1): ApprovalApplication => ({ id, applicantUserId: 7, applicantName: 'Synthetic owner',
  formKey: 'custom-travel', formName: status === 'DRAFT' ? 'My saved draft' : 'My recent application', title: 'Synthetic application', dataJson: '{}', status, version,
  overdue: false, reminderCount: 0, canRemind: status === 'PENDING', canWithdraw: status === 'PENDING', canEditDraft: status === 'DRAFT', canCancel: status === 'DRAFT',
  taskId: 22, createdAt: '2026-11-04T09:00:00', updatedAt: '2026-11-04T09:00:00', processVersionSnapshot: 4 });
const fields = (id = 12, page = 1) => ({ application: { applicationId: id, formName: 'Actual field projection', formKey: 'custom-travel', status: 'PENDING', version: 1 },
  schemaSource: 'SNAPSHOT', formVersion: 3, fieldPage: page, fieldsTotal: 60,
  fields: [{ name: 'reason', label: 'Reason', type: 'text', redacted: false, values: [`Private field page ${page}`] },
    { name: 'secret', label: 'Protected', type: 'text', redacted: true, values: ['never display this value'] }] });
function Location() { const location = useLocation(); return <output aria-label="start-location">{location.pathname}{location.search}</output>; }
const tree = () => <MemoryRouter initialEntries={['/oa/approval-start']}><ConfigProvider locale={zhCN}><App>
  <PageAgentContextProvider pageId="approval-start"><ApprovalStartPage /><Location /></PageAgentContextProvider></App></ConfigProvider></MemoryRouter>;
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
async function ready() { await screen.findByText('Template 1'); await screen.findByText('My recent application'); }
function recentCard() { return screen.getByText('My recent application').closest('.ant-card') as HTMLElement; }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: [...permissions] }; bindAuthResponseIdentity('approval-entry-owner', 'allowed');
  api.listApplicationTemplates.mockResolvedValue({ records: [template()], total: 1 });
  api.listMyApplications.mockImplementation(({ status }: { status?: string }) => Promise.resolve({ records: [status === 'DRAFT' ? application(11, 'DRAFT') : application()], total: 1 }));
  api.getApplication.mockResolvedValue(application(11, 'DRAFT'));
  api.ownApplicationFields.mockImplementation((id: number, page = 1) => Promise.resolve(fields(id, page)));
  api.withdrawApplication.mockResolvedValue(application(12, 'WITHDRAWN', 2));
  api.reopenApplication.mockResolvedValue(application(12, 'DRAFT', 2)); api.remindApplication.mockResolvedValue(application(12, 'PENDING', 2));
});
afterEach(cleanup);

it('offers the real leave route without querying generic templates or applications the user cannot read', async () => {
  auth.user.permissions = ['route:approval-start', 'leave:create', 'route:leave-application']; render(tree());
  fireEvent.click(await screen.findByRole('button', { name: '去申请' }));
  expect(screen.getByLabelText('start-location').textContent).toBe('/oa/leave-application');
  expect(api.listApplicationTemplates).not.toHaveBeenCalled(); expect(api.listMyApplications).not.toHaveBeenCalled();
});

it('keeps applicant templates usable when the user lacks own-list permission', async () => {
  auth.user.permissions = ['route:approval-start', 'approval:create', 'route:approval-form']; render(tree()); await screen.findByText('Template 1');
  expect(api.listMyApplications).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole('button', { name: '查看模板' }));
  await waitFor(() => expect(screen.getByLabelText('start-location').textContent).toBe('/oa/approval-form?formKey=custom-travel'));
  expect(api.listApplicationTemplates).toHaveBeenLastCalledWith({ formKey: 'custom-travel', page: 1, size: 1 });
});

it('keeps real own records and leave entry when template loading fails and reports refresh failure', async () => {
  api.listApplicationTemplates.mockRejectedValue(new Error('Synthetic catalogue failure')); render(tree()); await screen.findByText('My recent application');
  expect(screen.getByRole('button', { name: '去申请' })).toBeInTheDocument();
  await act(async () => { await expect(ui.handlers['ui.refreshPage']!({ pageId: 'approval-start', code: 'ui.refreshPage', payload: {} })).rejects.toThrow(); });
  expect(screen.queryByText('Template 1')).toBeNull();
});

it('loads every catalogue page and applies exact form-key filters without replacing them with a substring search', async () => {
  api.listApplicationTemplates.mockImplementation(({ page, formKey }: { page: number; formKey?: string }) => Promise.resolve(formKey
    ? { records: [template(101, formKey)], total: 1 }
    : { records: page === 1 ? Array.from({ length: 100 }, (_, i) => template(i + 1, `item-${i + 1}`)) : [template(101, 'last-template')], total: 101 }));
  render(tree()); await screen.findByText('Template 101');
  expect(api.listApplicationTemplates).toHaveBeenCalledWith({ page: 2, size: 100 });
  await act(async () => { await ui.handlers['ui.applyFilter']!({ pageId: 'approval-start', code: 'ui.applyFilter', payload: { filters: { formKey: 'last-template' } } }); });
  expect(screen.queryByText('Template 1')).toBeNull(); expect(screen.getByText('Template 101')).toBeInTheDocument();
  await act(async () => { await ui.handlers['ui.openDetail']!({ pageId: 'approval-start', code: 'ui.openDetail', payload: { recordId: 101 } }); });
  expect(screen.getByLabelText('start-location').textContent).toContain('formKey=last-template');
});

it('stops further catalogue pages after unmount even when the first response arrives late', async () => {
  const pending = deferred<{ records: ApprovalApplicationTemplate[]; total: number }>(); api.listApplicationTemplates.mockReturnValueOnce(pending.promise);
  const view = render(tree()); await waitFor(() => expect(api.listApplicationTemplates).toHaveBeenCalledTimes(1)); view.unmount();
  await act(async () => { pending.resolve({ records: Array.from({ length: 100 }, (_, i) => template(i + 1)), total: 101 }); });
  expect(api.listApplicationTemplates).toHaveBeenCalledTimes(1);
});

it('refuses a removed template or string resource ID instead of silently navigating', async () => {
  render(tree()); await ready(); api.listApplicationTemplates.mockResolvedValueOnce({ records: [], total: 0 });
  await act(async () => { await expect(ui.handlers['ui.openDetail']!({ pageId: 'approval-start', code: 'ui.openDetail', payload: { recordId: 1 } })).rejects.toThrow(); });
  expect(() => ui.handlers['ui.openDetail']!({ pageId: 'approval-start', code: 'ui.openDetail', payload: { recordId: '1' } })).toThrow();
  expect(screen.getByLabelText('start-location').textContent).toBe('/oa/approval-start');
});

it('ignores a template preflight after the user changes the active search', async () => {
  const pending = deferred<{ records: ApprovalApplicationTemplate[]; total: number }>(); render(tree()); await ready(); api.listApplicationTemplates.mockReturnValueOnce(pending.promise);
  const rejected = expect(ui.handlers['ui.openDetail']!({ pageId: 'approval-start', code: 'ui.openDetail', payload: { recordId: 1 } })).rejects.toThrow();
  fireEvent.change(screen.getByPlaceholderText('搜索审批事项'), { target: { value: 'different' } });
  await act(async () => { pending.resolve({ records: [template()], total: 1 }); await rejected; });
  expect(screen.getByLabelText('start-location').textContent).toBe('/oa/approval-start');
});

it('does not allow a card click to bypass missing target-route permission', async () => {
  auth.user.permissions = permissions.filter(code => code !== 'route:approval-form'); render(tree()); await ready();
  expect(screen.getByRole('button', { name: '查看模板' })).toBeDisabled();
  fireEvent.click(screen.getByText('Template 1').closest('.ant-card')!);
  await act(async () => { await expect(ui.handlers['ui.openDetail']!({ pageId: 'approval-start', code: 'ui.openDetail', payload: { recordId: 1 } })).rejects.toThrow(); });
  expect(screen.getByLabelText('start-location').textContent).toBe('/oa/approval-start');
});

it('preflights draft ownership and state before editing', async () => {
  render(tree()); await ready(); api.getApplication.mockResolvedValueOnce({ ...application(11, 'DRAFT'), applicantUserId: 99 });
  fireEvent.click(screen.getByRole('button', { name: /编\s*辑/ })); await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(screen.getByLabelText('start-location').textContent).toBe('/oa/approval-start');
});

it('uses the bounded field projection, respects redaction and never reopens a closed detail from a late page response', async () => {
  render(tree()); await ready(); fireEvent.click(within(recentCard()).getByRole('button', { name: '查看详情' }));
  const dialog = await screen.findByRole('dialog'); await within(dialog).findByText('Private field page 1');
  expect(screen.queryByText('never display this value')).toBeNull(); expect(api.getApplication).not.toHaveBeenCalled();
  const pending = deferred<ReturnType<typeof fields>>(); api.ownApplicationFields.mockReturnValueOnce(pending.promise);
  fireEvent.click(within(dialog).getByTitle('2')); await waitFor(() => expect(api.ownApplicationFields).toHaveBeenCalledWith(12, 2));
  const close = within(dialog).getAllByRole('button', { name: /关\s*闭/ }).at(-1)!; fireEvent.click(close);
  await act(async () => { pending.resolve(fields(12, 2)); }); await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(screen.queryByText('Private field page 2')).toBeNull();
});

it('locks withdrawal once and ignores its completion after live permission revocation', async () => {
  const pending = deferred<ApprovalApplication>(); api.withdrawApplication.mockReturnValueOnce(pending.promise);
  const view = render(tree()); await ready(); fireEvent.click(within(recentCard()).getByRole('button', { name: /撤\s*回/ }));
  const confirm = within(await screen.findByRole('dialog')).getByRole('button', { name: /确\s*定/ }); fireEvent.click(confirm); fireEvent.click(confirm);
  await waitFor(() => expect(api.withdrawApplication).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: ['route:approval-start'] }; view.rerender(tree());
  await act(async () => { pending.resolve(application(12, 'WITHDRAWN', 2)); });
  expect(api.success).not.toHaveBeenCalled(); expect(screen.queryByText('My recent application')).toBeNull();
});

it('checks versioned receipts before reporting a lifecycle success', async () => {
  api.withdrawApplication.mockResolvedValue(application(12, 'PENDING', 1)); render(tree()); await ready();
  fireEvent.click(within(recentCard()).getByRole('button', { name: /撤\s*回/ }));
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /确\s*定/ }));
  await waitFor(() => expect(api.error).toHaveBeenCalled()); expect(api.success).not.toHaveBeenCalled(); expect(api.withdrawApplication).toHaveBeenCalledTimes(1);
});

it('reopens a rejected request with a new version and enters the acknowledged draft', async () => {
  api.listMyApplications.mockImplementation(({ status }: { status?: string }) => Promise.resolve({ records: [status === 'DRAFT' ? application(11, 'DRAFT') : application(12, 'REJECTED')], total: 1 }));
  render(tree()); await ready(); fireEvent.click(screen.getByRole('button', { name: '编辑并重新提交' }));
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: /确\s*定/ }));
  await waitFor(() => expect(api.reopenApplication).toHaveBeenCalledWith(12, 1));
  await waitFor(() => expect(screen.getByLabelText('start-location').textContent).toBe('/oa/approval-form?formKey=custom-travel&draftId=12'));
});

it('loads real entry data under root StrictMode effect replay', async () => {
  render(tree(), { reactStrictMode: true }); await ready(); expect(api.listApplicationTemplates).toHaveBeenCalledTimes(2);
  expect(api.listMyApplications).toHaveBeenCalledTimes(4);
});

it('requires an explicit reminder confirmation before dispatching the versioned request', async () => {
  render(tree()); await ready(); fireEvent.click(within(recentCard()).getByRole('button', { name: /催办/ }));
  const dialog = await screen.findByRole('dialog'); expect(api.remindApplication).not.toHaveBeenCalled();
  fireEvent.click(within(dialog).getByRole('button', { name: /确\s*定/ }));
  await waitFor(() => expect(api.remindApplication).toHaveBeenCalledWith(12, 1));
  await waitFor(() => expect(api.success).toHaveBeenCalled()); expect(api.remindApplication).toHaveBeenCalledTimes(1);
});
