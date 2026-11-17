import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { AssetAssignmentOption, AssetLedger } from '@/lib/adminAssetsApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';
import i18n from '@/i18n';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['assets:read', 'asset:write'] } }));
const api = vi.hoisted(() => ({ listAssets: vi.fn(), getAsset: vi.fn(), updateAsset: vi.fn(), createAsset: vi.fn(),
  returnAsset: vi.fn(), claimAsset: vi.fn(), transferAsset: vi.fn(), inventoryAsset: vi.fn(), assetDepartments: vi.fn(), assetCustodians: vi.fn() }));
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/adminAssetsApi', () => ({ adminAssetsApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('@/lib/nextCompat', () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => new URLSearchParams() }));
import AssetLedgerPage from './AssetLedgerPage';

function asset(id = 1, name = 'Synthetic asset'): AssetLedger { return { id, name, assetCode: `AS-${id}`, category: 'Device', status: 'IN_USE',
  departmentId: 10, ownerUserId: 8, version: 4, history: [], canEdit: true, canDelete: false, createdAt: '', updatedAt: '' }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function Snapshot() { return <output aria-label="asset-context">{JSON.stringify(usePageAgentContextSnapshot())}</output>; }
function view() { return <ConfigProvider locale={zhCN}><App><PageAgentContextProvider pageId="asset-ledger"><AssetLedgerPage /><Snapshot /></PageAgentContextProvider></App></ConfigProvider>; }
async function dialog(title: string) { return waitFor(() => {
  const element = screen.getAllByRole('dialog').find(item => within(item).queryByText(title));
  expect(element).toBeTruthy(); return element!;
}); }
async function detail() {
  await screen.findByText('Synthetic asset');
  fireEvent.click(screen.getByRole('button', { name: '资产详情' }));
  return dialog('AS-1 · Synthetic asset');
}
async function transfer() {
  const drawer = await detail();
  fireEvent.click(within(drawer).getByRole('button', { name: '跨部门调拨' }));
  return dialog('资产调拨');
}
async function chooseDepartment(modal: HTMLElement, name: string) {
  fireEvent.mouseDown(within(modal).getByLabelText('目标部门'));
  fireEvent.click(await screen.findByText(name));
}
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['assets:read', 'asset:write'] };
  api.listAssets.mockResolvedValue({ records: [asset()], total: 1 }); api.getAsset.mockResolvedValue(asset());
  api.assetDepartments.mockResolvedValue([{ id: 10, name: 'Source department', departmentId: null },
    { id: 20, name: 'Target department', departmentId: null }, { id: 30, name: 'Another department', departmentId: null }]);
  api.assetCustodians.mockResolvedValue({ records: [{ id: 9, name: 'Synthetic custodian', departmentId: 20 }], total: 1 });
});
afterEach(async () => { cleanup(); await i18n.changeLanguage('zh-CN'); });

it.each([
  { operation: 'CLAIM', label: '办理领用', title: '资产领用', status: 'IDLE' as const },
  { operation: 'TRANSFER', label: '跨部门调拨', title: '资产调拨', status: 'IN_USE' as const },
])('$operation uses protected candidates and sends one versioned write without human-resources access', async ({ operation, label, title, status }) => {
  const pending = deferred<AssetLedger>();
  const submitOperation = operation === 'CLAIM' ? api.claimAsset : api.transferAsset;
  submitOperation.mockReturnValueOnce(pending.promise);
  api.getAsset.mockResolvedValue({ ...asset(), status });
  render(view()); const drawer = await detail();
  fireEvent.click(within(drawer).getByRole('button', { name: label })); const modal = await dialog(title);
  await chooseDepartment(modal, 'Target department');
  fireEvent.mouseDown(within(modal).getByLabelText('目标保管人'));
  fireEvent.click(await screen.findByText('Synthetic custodian'));
  const submit = within(modal).getByRole('button', { name: '确 定' });
  act(() => { fireEvent.click(submit); fireEvent.click(submit); });
  await waitFor(() => expect(submitOperation).toHaveBeenCalledTimes(1));
  expect(submitOperation).toHaveBeenCalledWith(1, expect.objectContaining({ version: 4, targetDepartmentId: 20, targetOwnerUserId: 9 }));
  expect(within(modal).getByLabelText('目标保管人')).toBeDisabled();
  await act(async () => { pending.resolve({ ...asset(), departmentId: 20, ownerUserId: 9, version: 5 }); });
});

it('loads all 205 candidate custodians instead of presenting only the first 100', async () => {
  api.assetCustodians.mockImplementation(async (departmentId: number, page: number, size: number) => ({
    records: Array.from({ length: Math.min(size, 205 - (page - 1) * size) }, (_, i) => ({ id: (page - 1) * size + i + 1, name: `Custodian ${(page - 1) * size + i + 1}`, departmentId })), total: 205,
  }));
  render(view()); const modal = await transfer(); await chooseDepartment(modal, 'Target department');
  await waitFor(() => expect(api.assetCustodians).toHaveBeenCalledTimes(3));
  fireEvent.mouseDown(within(modal).getByLabelText('目标保管人'));
  fireEvent.change(within(modal).getByLabelText('目标保管人'), { target: { value: 'Custodian 205' } });
  expect(await screen.findByText('Custodian 205')).toBeTruthy();
  expect(api.assetCustodians).toHaveBeenLastCalledWith(20, 3, 100);
});

it('keeps the selected asset and operation draft when switching language', async () => {
  render(view()); const modal = await transfer();
  fireEvent.change(within(modal).getByRole('textbox'), { target: { value: 'Unsaved operation reason' } });
  await act(async () => { await i18n.changeLanguage('en-US'); });
  expect(JSON.parse(screen.getByLabelText('asset-context').textContent || '{}').assetId).toBe(1);
  const translated = await dialog(i18n.t('adminAssets.asset.operation.TRANSFER.title'));
  expect(within(translated).getByRole('textbox')).toHaveValue('Unsaved operation reason');
  expect(api.transferAsset).not.toHaveBeenCalled();
});

it('stops the old candidate pagination after changing departments and never mixes its rows into the new department', async () => {
  const old = deferred<{ records: AssetAssignmentOption[]; total: number }>();
  api.assetCustodians.mockImplementation((departmentId: number) => departmentId === 20 ? old.promise
    : Promise.resolve({ records: [{ id: 31, name: 'New department custodian', departmentId: 30 }], total: 1 }));
  render(view()); const modal = await transfer(); await chooseDepartment(modal, 'Target department');
  await waitFor(() => expect(api.assetCustodians).toHaveBeenCalledWith(20, 1, 100));
  await chooseDepartment(modal, 'Another department');
  await waitFor(() => expect(api.assetCustodians).toHaveBeenCalledWith(30, 1, 100));
  await act(async () => { old.resolve({ records: Array.from({ length: 100 }, (_, i) => ({ id: i + 1, name: 'Old department custodian', departmentId: 20 })), total: 205 }); });
  fireEvent.mouseDown(within(modal).getByLabelText('目标保管人'));
  await screen.findByText('New department custodian');
  expect(screen.queryByText('Old department custodian')).toBeNull();
  expect(api.assetCustodians).toHaveBeenCalledTimes(2);
  expect(messages.error).not.toHaveBeenCalled();
});

it('clears the previous selected custodian when switching department rather than submitting an incompatible pair', async () => {
  render(view()); const modal = await transfer(); await chooseDepartment(modal, 'Target department');
  fireEvent.mouseDown(within(modal).getByLabelText('目标保管人'));
  fireEvent.click(await screen.findByText('Synthetic custodian'));
  await chooseDepartment(modal, 'Another department');
  fireEvent.click(within(modal).getByRole('button', { name: '确 定' }));
  await waitFor(() => expect(modal.querySelectorAll('.ant-form-item-explain-error').length).toBeGreaterThan(0));
  expect(api.transferAsset).not.toHaveBeenCalled();
  expect(messages.error).not.toHaveBeenCalled();
});

it('discards a failed subsequent candidate page rather than showing a misleading partial catalogue', async () => {
  api.assetCustodians.mockResolvedValueOnce({ records: Array.from({ length: 100 }, (_, i) => ({ id: i + 1, name: 'Partial candidate', departmentId: 20 })), total: 205 })
    .mockRejectedValueOnce(new Error('Synthetic candidate page failure'));
  render(view()); const modal = await transfer(); await chooseDepartment(modal, 'Target department');
  await waitFor(() => expect(messages.error).toHaveBeenCalledTimes(1));
  fireEvent.mouseDown(within(modal).getByLabelText('目标保管人'));
  expect(screen.queryByText('Partial candidate')).toBeNull();
});

it('keeps the replacement editor after a versioned save completes and prevents a duplicate save click', async () => {
  const pending = deferred<AssetLedger>(); api.updateAsset.mockReturnValueOnce(pending.promise);
  render(view()); await screen.findByText('Synthetic asset');
  fireEvent.click(screen.getByRole('button', { name: '编辑' })); const old = await dialog('编辑资产');
  const save = within(old).getByRole('button', { name: '保 存' });
  act(() => { fireEvent.click(save); fireEvent.click(save); });
  await waitFor(() => expect(api.updateAsset).toHaveBeenCalledTimes(1));
  expect(api.updateAsset).toHaveBeenCalledWith(1, expect.objectContaining({ version: 4 }));
  expect(within(old).getByLabelText('资产名称')).toBeDisabled();
  fireEvent.click(within(old).getByRole('button', { name: '关 闭' }));
  fireEvent.click(screen.getByRole('button', { name: '新增资产' })); const next = await dialog('新增资产');
  fireEvent.change(within(next).getByLabelText('资产名称'), { target: { value: 'Replacement draft' } });
  await act(async () => { pending.resolve({ ...asset(), version: 5 }); });
  expect(within(await dialog('新增资产')).getByLabelText('资产名称')).toHaveValue('Replacement draft');
  expect(messages.success).not.toHaveBeenCalled();
});

it('keeps a version-conflicted draft available for review and retry', async () => {
  api.updateAsset.mockRejectedValueOnce(new Error('Synthetic version conflict'));
  render(view()); await screen.findByText('Synthetic asset');
  fireEvent.click(screen.getByRole('button', { name: '编辑' })); const modal = await dialog('编辑资产');
  fireEvent.change(within(modal).getByLabelText('资产名称'), { target: { value: 'Draft for reconciliation' } });
  fireEvent.click(within(modal).getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(messages.error).toHaveBeenCalledTimes(1));
  expect(within(modal).getByLabelText('资产名称')).toHaveValue('Draft for reconciliation');
  expect(messages.success).not.toHaveBeenCalled();
  expect(api.listAssets).toHaveBeenCalledTimes(1);
});

it('publishes applied search filters and refreshes a completed write without submitting a new search draft', async () => {
  const pending = deferred<AssetLedger>(); api.returnAsset.mockReturnValueOnce(pending.promise);
  render(view()); const drawer = await detail();
  const search = screen.getByPlaceholderText('搜索编码 / 名称 / 规格');
  fireEvent.change(search, { target: { value: 'Applied search' } });
  fireEvent.keyDown(search, { key: 'Enter', code: 'Enter', charCode: 13 });
  await waitFor(() => expect(api.listAssets).toHaveBeenCalledTimes(2));
  fireEvent.change(search, { target: { value: 'Unsubmitted draft search' } });
  fireEvent.click(within(drawer).getByRole('button', { name: '办理归还' })); const modal = await dialog('资产归还');
  fireEvent.click(within(modal).getByRole('button', { name: '确 定' }));
  await waitFor(() => expect(api.returnAsset).toHaveBeenCalledTimes(1));
  await act(async () => { pending.resolve({ ...asset(), status: 'IDLE', version: 5 }); });
  expect(api.listAssets).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'Applied search' }));
  expect(JSON.parse(screen.getByLabelText('asset-context').textContent || '{}').keyword).toBe('Applied search');
});

it('suppresses pending writes and candidate pagination after authority is revoked', async () => {
  const pending = deferred<AssetLedger>(), candidates = deferred<{ records: AssetAssignmentOption[]; total: number }>();
  api.updateAsset.mockReturnValueOnce(pending.promise); api.assetCustodians.mockReturnValueOnce(candidates.promise);
  const page = render(view()); const modal = await transfer(); await chooseDepartment(modal, 'Target department');
  await waitFor(() => expect(api.assetCustodians).toHaveBeenCalledTimes(1));
  // Close the operation and submit an independent edit before revoking authority.
  fireEvent.click(within(modal).getByRole('button', { name: '取 消' }));
  fireEvent.click(screen.getByRole('button', { name: '编辑' })); const editor = await dialog('编辑资产');
  fireEvent.click(within(editor).getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(api.updateAsset).toHaveBeenCalledTimes(1));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; page.rerender(view());
  await act(async () => {
    pending.resolve({ ...asset(), version: 5 });
    candidates.resolve({ records: Array.from({ length: 100 }, (_, i) => ({ id: i + 1, name: 'Private candidate', departmentId: 20 })), total: 205 });
  });
  expect(messages.success).not.toHaveBeenCalled();
  expect(screen.queryByText('Synthetic asset')).toBeNull();
  expect(screen.queryByText('Private candidate')).toBeNull();
  expect(api.assetCustodians).toHaveBeenCalledTimes(1);
  expect(api.listAssets).toHaveBeenCalledTimes(1);
});
