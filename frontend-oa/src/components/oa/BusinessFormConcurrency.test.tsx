import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import type { SealUsage, SealUsageDocument } from '@/lib/adminAssetsApi';
import type { EmployeeChange } from '@/lib/hrApi';
import { PageAgentContextProvider } from './PageAgentContext';
import i18n from '@/i18n';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[] } }));
const api = vi.hoisted(() => ({ listMyVisitorBookings: vi.fn(), listPendingVisitorBookings: vi.fn(), submitVisitorBooking: vi.fn(), visitorHostOptions: vi.fn(),
  listMySealUsages: vi.fn(), listPendingSealUsages: vi.fn(), submitSealUsage: vi.fn(), registerSealUse: vi.fn(), returnSeal: vi.fn(),
  listSealUsageDocuments: vi.fn(), uploadSealUsageDocument: vi.fn(), approveSealUsage: vi.fn() }));
const hr = vi.hoisted(() => ({ overview: vi.fn(), listEmployeeChanges: vi.fn(), createEmployeeChange: vi.fn(), employeeChangeReviewerOptions: vi.fn(),
  employeeChangeDetail: vi.fn(), approveEmployeeChange: vi.fn(), rejectEmployeeChange: vi.fn(), withdrawEmployeeChange: vi.fn() }));
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => { const handlers: PageUiCommandHandlers = {}; return { handlers }; });
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/adminAssetsApi', () => ({ adminAssetsApi: api }));
vi.mock('@/lib/hrApi', () => ({ hrApi: hr }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('@/lib/nextCompat', () => ({ useSearchParams: () => new URLSearchParams() }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import VisitorBookingPage from './VisitorBookingPage';
import SealUsagePage from './SealUsagePage';
import EmployeeChangePage from './EmployeeChangePage';

const permissions = ['route:visitor-booking', 'visitor:read:self', 'visitor:create', 'route:seal-usage', 'seal:read:self', 'seal:create', 'approval:act', 'route:employee-change', 'hr:read', 'hr:manage'];
interface Case { pageId: string; Component: typeof VisitorBookingPage; create: string; title: string; field: string; submitLabel: string;
  values: Record<string, string | number>; submit: typeof api.submitSealUsage; list: typeof api.listMySealUsages }
const cases: Case[] = [
  { pageId: 'visitor-booking', Component: VisitorBookingPage, create: '新增预约', title: '新增预约', field: '访客姓名', submitLabel: '提 交',
    values: { visitorName: 'Synthetic visitor', purpose: 'Synthetic visit', hostUserId: 9, expectedVisitAt: '2026-11-01T09:00:00', partySize: 1 }, submit: api.submitVisitorBooking, list: api.listMyVisitorBookings },
  { pageId: 'seal-usage', Component: SealUsagePage, create: '新增用印', title: '新增用印', field: '文件标题', submitLabel: '提 交',
    values: { documentTitle: 'Synthetic document', usageReason: 'Synthetic reason', sealType: 'OFFICIAL', copies: 1 }, submit: api.submitSealUsage, list: api.listMySealUsages },
  { pageId: 'employee-change', Component: EmployeeChangePage, create: '发起员工变动', title: '发起入转调离申请', field: '变动说明', submitLabel: '提交审批',
    values: { employeeUserId: 8, changeType: 'REGULARIZATION', reviewApproverUserId: 9, effectiveDate: '2026-11-01', reason: 'Synthetic change' }, submit: hr.createEmployeeChange, list: hr.listEmployeeChanges },
];
function Tree({ entry }: { entry: Case }) { const Component = entry.Component;
  return <ConfigProvider locale={zhCN}><App><PageAgentContextProvider pageId={entry.pageId}><Component /></PageAgentContextProvider></App></ConfigProvider>; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
async function dialog(title: string) { return waitFor(() => {
  const item = screen.getAllByRole('dialog').find(element => within(element).queryAllByText(title).length > 0);
  expect(item).toBeTruthy(); return item!;
}); }
async function fill(entry: Case) { await act(async () => {
  await ui.handlers['ui.fillForm']?.({ pageId: entry.pageId, code: 'ui.fillForm', payload: { values: entry.values, formContext: { sessionId: 'synthetic-form' } } });
}); }
function seal(id: number): SealUsage { return { id, applicantUserId: 7, sealType: 'OFFICIAL', documentTitle: `Synthetic document ${id}`, usageReason: 'Synthetic reason',
  copies: 2, status: 'APPROVED', version: 3, taskId: id + 100, taskVersion: 2, createdAt: '2026-10-30T09:00:00', updatedAt: '2026-10-30T09:00:00',
  canWithdraw: false, canDecide: true, canRegisterUse: true, canReturn: true, canArchiveDocument: true }; }
function document(id: number, sealUsageId: number): SealUsageDocument { return { id, sealUsageId, displayName: `Synthetic file ${id}`, mimeType: 'text/plain', fileSize: 4,
  uploadedByUserId: 7, contentUrl: `/api/seal-documents/${id}`, createdAt: '2026-10-30T09:00:00' }; }
function change(): EmployeeChange { return { id: 1, employeeUserId: 8, employeeName: 'Synthetic employee', employeeEmail: 'employee@example.invalid', applicantUserId: 7,
  applicantName: 'Synthetic applicant', reviewApproverUserId: 7, reviewApproverName: 'Synthetic reviewer', changeType: 'REGULARIZATION', effectiveDate: '2026-11-01',
  reason: 'Synthetic change', status: 'PENDING', version: 2, canApprove: true, canWithdraw: true, submittedAt: '2026-10-30T09:00:00', createdAt: '2026-10-30T09:00:00', updatedAt: '2026-10-30T09:00:00' }; }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions };
  for (const method of [api.listMyVisitorBookings, api.listPendingVisitorBookings, api.listMySealUsages, api.listPendingSealUsages, hr.listEmployeeChanges]) method.mockResolvedValue({ records: [], total: 0 });
  for (const method of [api.visitorHostOptions, hr.employeeChangeReviewerOptions]) method.mockResolvedValue({ records: [{ id: 9, name: 'Synthetic reviewer' }], total: 1 });
  hr.overview.mockResolvedValue({ employees: [{ id: 8, name: 'Synthetic employee', email: 'employee@example.invalid' }], departments: [], positions: [] });
  api.listSealUsageDocuments.mockResolvedValue([]);
});
afterEach(cleanup);

it.each(cases)('$pageId keeps a replacement draft after an earlier write finishes and prevents a duplicate click', async entry => {
  const pending = deferred<object>(); entry.submit.mockReturnValueOnce(pending.promise);
  render(<Tree entry={entry} />); await waitFor(() => expect(entry.list).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: entry.create }));
  const old = await dialog(entry.title); await fill(entry);
  const submit = within(old).getByRole('button', { name: entry.submitLabel });
  fireEvent.click(submit); fireEvent.click(submit);
  await waitFor(() => expect(entry.submit).toHaveBeenCalledTimes(1));
  expect(within(old).getByLabelText(entry.field)).toBeDisabled();
  for (const input of within(old).queryAllByRole('combobox')) expect(input).toBeDisabled();
  fireEvent.click(await within(old).findByRole('button', { name: '关 闭' }));
  fireEvent.click(screen.getByRole('button', { name: entry.create }));
  const replacement = await dialog(entry.title);
  fireEvent.change(within(replacement).getByLabelText(entry.field), { target: { value: 'Replacement draft' } });
  await act(async () => pending.resolve({}));
  expect((within(await dialog(entry.title)).getByLabelText(entry.field) as HTMLInputElement).value).toBe('Replacement draft');
  expect(messages.success).not.toHaveBeenCalled();
  await waitFor(() => expect(entry.list).toHaveBeenCalledTimes(2));
});

it.each(cases)('$pageId rejects late write callbacks and captured refreshes after authority changes', async entry => {
  const pending = deferred<object>(); entry.submit.mockReturnValueOnce(pending.promise);
  const view = render(<Tree entry={entry} />); await waitFor(() => expect(entry.list).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: entry.create })); await dialog(entry.title); await fill(entry);
  const oldRefresh = ui.handlers['ui.refreshPage'];
  fireEvent.click(within(await dialog(entry.title)).getByRole('button', { name: entry.submitLabel }));
  await waitFor(() => expect(entry.submit).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(<Tree entry={entry} />);
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  await act(async () => {
    pending.resolve({});
    await oldRefresh?.({ pageId: entry.pageId, code: 'ui.refreshPage', payload: {} });
  });
  expect(entry.list).toHaveBeenCalledTimes(1); expect(messages.success).not.toHaveBeenCalled();
});

it.each(cases)('$pageId releases its submit lock after validation failure without sending a write', async entry => {
  render(<Tree entry={entry} />); await waitFor(() => expect(entry.list).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: entry.create })); const form = await dialog(entry.title);
  fireEvent.click(within(form).getByRole('button', { name: entry.submitLabel }));
  await waitFor(() => expect(form.querySelector('.ant-form-item-explain-error')).toBeTruthy());
  await within(form).findByRole('button', { name: '取 消' });
  expect(entry.submit).not.toHaveBeenCalled(); expect(messages.error).not.toHaveBeenCalled();
});

it('destroys a shared form preview when authority changes', async () => {
  const entry = cases[1], view = render(<Tree entry={entry} />);
  await waitFor(() => expect(entry.list).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('button', { name: entry.create })); await dialog(entry.title); await fill(entry);
  await act(async () => { await ui.handlers['ui.previewSubmission']?.({ pageId: entry.pageId, code: 'ui.previewSubmission', payload: { formContext: { sessionId: 'synthetic-form' } } }); });
  await dialog(i18n.t('oa.ai.uiPreviewTitle'));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(<Tree entry={entry} />);
  await waitFor(() => expect(screen.queryByText(i18n.t('oa.ai.uiPreviewTitle'))).toBeNull());
});

it('does not mix late archived documents from a previous seal application', async () => {
  const late = deferred<SealUsageDocument[]>(); api.listSealUsageDocuments.mockReturnValueOnce(late.promise).mockResolvedValueOnce([document(2, 2)]);
  api.listMySealUsages.mockResolvedValue({ records: [seal(1), seal(2)], total: 2 });
  render(<Tree entry={cases[1]} />); await screen.findByText('Synthetic document 2');
  fireEvent.click(screen.getAllByRole('button', { name: '文件留档' })[0]);
  let archive = await dialog('用印文件留档');
  fireEvent.click(within(archive).getByRole('button', { name: /^(Close|关闭)$/ }));
  fireEvent.click(screen.getAllByRole('button', { name: '文件留档' })[1]);
  archive = await dialog('用印文件留档'); await within(archive).findByText('Synthetic file 2');
  await act(async () => late.resolve([document(1, 1)]));
  expect(within(archive).queryByText('Synthetic file 1')).toBeNull();
  expect(within(archive).getByText('Synthetic file 2')).toBeTruthy();
});

it('does not close a replacement seal execution form when an old registration completes', async () => {
  const pending = deferred<object>(); api.registerSealUse.mockReturnValueOnce(pending.promise);
  api.listMySealUsages.mockResolvedValue({ records: [seal(1), seal(2)], total: 2 });
  render(<Tree entry={cases[1]} />); await screen.findByText('Synthetic document 2');
  fireEvent.click(screen.getAllByRole('button', { name: '登记用印' })[0]);
  let form = await dialog('登记实际用印');
  fireEvent.click(within(form).getByRole('button', { name: '登记用印' }));
  await waitFor(() => expect(api.registerSealUse).toHaveBeenCalledWith(1, expect.objectContaining({ version: 3 })));
  expect(within(form).getByLabelText('经办备注')).toBeDisabled();
  fireEvent.click(await within(form).findByRole('button', { name: '关 闭' }));
  fireEvent.click(screen.getAllByRole('button', { name: '登记用印' })[1]); form = await dialog('登记实际用印');
  fireEvent.change(within(form).getByLabelText('经办备注'), { target: { value: 'Second execution draft' } });
  await act(async () => pending.resolve({}));
  expect((within(await dialog('登记实际用印')).getByLabelText('经办备注') as HTMLInputElement).value).toBe('Second execution draft');
  expect(messages.success).not.toHaveBeenCalled();
});

it('does not refresh or notify a new archive after an old upload completes', async () => {
  const pending = deferred<SealUsageDocument>(); api.uploadSealUsageDocument.mockReturnValueOnce(pending.promise);
  api.listMySealUsages.mockResolvedValue({ records: [seal(1), seal(2)], total: 2 });
  render(<Tree entry={cases[1]} />); await screen.findByText('Synthetic document 2');
  fireEvent.click(screen.getAllByRole('button', { name: '文件留档' })[0]);
  let archive = await dialog('用印文件留档');
  const input = archive.querySelector<HTMLInputElement>('input[type="file"]'); expect(input).toBeTruthy();
  fireEvent.change(input!, { target: { files: [new File(['synthetic'], 'synthetic.txt', { type: 'text/plain' })] } });
  await waitFor(() => expect(api.uploadSealUsageDocument).toHaveBeenCalledTimes(1));
  fireEvent.click(within(archive).getByRole('button', { name: /^(Close|关闭)$/ }));
  fireEvent.click(screen.getAllByRole('button', { name: '文件留档' })[1]); archive = await dialog('用印文件留档');
  await waitFor(() => expect(api.listSealUsageDocuments).toHaveBeenCalledTimes(2));
  await act(async () => pending.resolve(document(1, 1)));
  expect(api.listSealUsageDocuments).toHaveBeenCalledTimes(2); expect(messages.success).not.toHaveBeenCalled();
  expect(within(archive).queryByText('Synthetic file 1')).toBeNull();
});

it('preserves a new seal approval form after an old approval completes', async () => {
  const pending = deferred<object>(); api.approveSealUsage.mockReturnValueOnce(pending.promise);
  api.listPendingSealUsages.mockResolvedValue({ records: [seal(1), seal(2)], total: 2 });
  render(<Tree entry={cases[1]} />); await waitFor(() => expect(api.listMySealUsages).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByRole('tab', { name: '待我审批' })); await screen.findByText('Synthetic document 2');
  fireEvent.click(screen.getAllByRole('button', { name: /^审\s*批$/ })[0]);
  let form = await dialog('审批用印申请'); fireEvent.click(within(form).getByRole('button', { name: '通 过' }));
  await waitFor(() => expect(api.approveSealUsage).toHaveBeenCalledWith(101, expect.objectContaining({ version: 2 })));
  fireEvent.click(within(form).getByRole('button', { name: /^(Close|关闭)$/ }));
  fireEvent.click(screen.getAllByRole('button', { name: /^审\s*批$/ })[1]); form = await dialog('审批用印申请');
  fireEvent.change(within(form).getByLabelText('审批意见'), { target: { value: 'Second decision draft' } });
  await act(async () => pending.resolve({}));
  expect((within(await dialog('审批用印申请')).getByLabelText('审批意见') as HTMLInputElement).value).toBe('Second decision draft');
  expect(messages.success).not.toHaveBeenCalled();
});

it('destroys an employee confirmation on revocation before any write is sent', async () => {
  hr.listEmployeeChanges.mockResolvedValue({ records: [change()], total: 1 });
  const view = render(<Tree entry={cases[2]} />); await screen.findByText('Synthetic employee');
  fireEvent.click(screen.getByRole('button', { name: /^通\s*过$/ }));
  await dialog('确认通过这笔员工变动？');
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(<Tree entry={cases[2]} />);
  await waitFor(() => expect(screen.queryByText('确认通过这笔员工变动？')).toBeNull());
  expect(hr.approveEmployeeChange).not.toHaveBeenCalled();
});
