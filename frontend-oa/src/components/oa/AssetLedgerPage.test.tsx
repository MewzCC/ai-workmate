import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { AssetLedger } from '@/lib/adminAssetsApi';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['assets:read', 'asset:write'] } }));
const api = vi.hoisted(() => ({ listAssets: vi.fn(), getAsset: vi.fn(), updateAsset: vi.fn(), createAsset: vi.fn(),
  returnAsset: vi.fn(), assetDepartments: vi.fn(), assetCustodians: vi.fn() }));
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => { const handlers: PageUiCommandHandlers = {}; return { handlers }; });
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/adminAssetsApi', () => ({ adminAssetsApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('@/lib/nextCompat', () => ({ useRouter: () => ({ replace: vi.fn() }), useSearchParams: () => new URLSearchParams() }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import AssetLedgerPage from './AssetLedgerPage';

function asset(id = 1, name = '测试资产'): AssetLedger { return { id, name, assetCode: `AS-${id}`, category: '设备', status: 'IN_USE',
  version: 4, history: [], canEdit: true, canDelete: false, createdAt: '2026-10-30T10:00:00', updatedAt: '2026-10-30T10:00:00' }; }
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (error: Error) => void;
  const promise = new Promise<T>((done, fail) => { resolve = done; reject = fail; }); return { promise, resolve, reject }; }
function Snapshot() { return <output aria-label="asset-context">{JSON.stringify(usePageAgentContextSnapshot())}</output>; }
function Tree() { return <ConfigProvider locale={zhCN}><App><PageAgentContextProvider pageId="asset-ledger"><AssetLedgerPage /><Snapshot /></PageAgentContextProvider></App></ConfigProvider>; }
function context() { return JSON.parse(screen.getByLabelText('asset-context').textContent || '{}'); }
// AntD's test-mode ID generator reuses "test-id" across portals; inspect each dialog's actual title.
async function findDialog(title: string) { return waitFor(() => {
  const dialog = screen.getAllByRole('dialog').find(item => within(item).queryByText(title));
  expect(dialog).toBeTruthy(); return dialog!;
}); }
async function command(code: 'ui.refreshPage' | 'ui.applyFilter', filters: Record<string, string> = {}) {
  await act(async () => { if (code === 'ui.applyFilter') await ui.handlers[code]?.({ pageId: 'asset-ledger', code, payload: { filters } });
    else await ui.handlers[code]?.({ pageId: 'asset-ledger', code, payload: {} }); });
}
beforeEach(() => {
  vi.clearAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions: ['assets:read', 'asset:write'] };
  api.listAssets.mockResolvedValue({ records: [asset()], total: 1, page: 1, size: 10 });
  api.getAsset.mockResolvedValue(asset()); api.assetDepartments.mockResolvedValue([]);
  api.assetCustodians.mockResolvedValue({ records: [], total: 0, page: 1, size: 100 });
});
afterEach(cleanup);

it('publishes only applied filters and refreshes without silently submitting a draft search', async () => {
  render(<Tree />); await screen.findByText('测试资产');
  const search = screen.getByPlaceholderText('搜索编码 / 名称 / 规格');
  fireEvent.change(search, { target: { value: '未提交条件' } });
  expect(context().keyword).toBeUndefined();
  await command('ui.refreshPage');
  expect(api.listAssets.mock.lastCall?.[0].keyword).toBeUndefined();
  await command('ui.applyFilter', { keyword: '正式条件' });
  expect(context().keyword).toBe('正式条件');
  fireEvent.change(search, { target: { value: '另一个草稿' } });
  await command('ui.refreshPage');
  expect(api.listAssets.mock.lastCall?.[0].keyword).toBe('正式条件');
  expect(context().keyword).toBe('正式条件');
});

it('does not close a new editor or erase its draft when an earlier save finishes', async () => {
  const pending = deferred<AssetLedger>(); api.updateAsset.mockReturnValueOnce(pending.promise);
  render(<Tree />); await screen.findByText('测试资产');
  fireEvent.click(screen.getByRole('button', { name: '编辑' }));
  let dialog = await findDialog('编辑资产');
  fireEvent.click(within(dialog).getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(api.updateAsset).toHaveBeenCalledWith(1, expect.objectContaining({ version: 4 })));
  await waitFor(() => expect(within(dialog).getByLabelText('资产名称')).toBeDisabled());
  fireEvent.click(await within(dialog).findByRole('button', { name: '关 闭' }));
  fireEvent.click(screen.getByRole('button', { name: '新增资产' }));
  dialog = await findDialog('新增资产');
  fireEvent.change(within(dialog).getByLabelText('资产名称'), { target: { value: '新表单草稿' } });
  await act(async () => pending.resolve({ ...asset(), version: 5 }));
  expect(await findDialog('新增资产')).toBeTruthy();
  expect((within(dialog).getByLabelText('资产名称') as HTMLInputElement).value).toBe('新表单草稿');
  expect(messages.success).not.toHaveBeenCalled();
  await waitFor(() => expect(api.listAssets).toHaveBeenCalledTimes(2));
});

it('preserves the current editor draft on a version conflict', async () => {
  api.updateAsset.mockRejectedValueOnce(new Error('Synthetic version conflict'));
  render(<Tree />); await screen.findByText('测试资产');
  fireEvent.click(screen.getByRole('button', { name: '编辑' }));
  const dialog = await findDialog('编辑资产');
  fireEvent.change(within(dialog).getByLabelText('资产名称'), { target: { value: '等待核对版本的草稿' } });
  fireEvent.click(within(dialog).getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(messages.error).toHaveBeenCalled());
  expect((within(dialog).getByLabelText('资产名称') as HTMLInputElement).value).toBe('等待核对版本的草稿');
  expect(messages.success).not.toHaveBeenCalled();
  expect(api.listAssets).toHaveBeenCalledTimes(1);
});

it('ignores a completed operation after its detail is closed and another asset is opened', async () => {
  const pending = deferred<AssetLedger>(); api.returnAsset.mockReturnValueOnce(pending.promise);
  api.listAssets.mockResolvedValue({ records: [asset(), asset(2, '第二资产')], total: 2, page: 1, size: 10 });
  render(<Tree />); await screen.findByText('第二资产');
  fireEvent.click(screen.getAllByRole('button', { name: '资产详情' })[0]);
  const detail = await findDialog('AS-1 · 测试资产');
  fireEvent.click(within(detail).getByRole('button', { name: '办理归还' }));
  const operation = await findDialog('资产归还');
  fireEvent.click(within(operation).getByRole('button', { name: '确 定' }));
  await waitFor(() => expect(api.returnAsset).toHaveBeenCalledWith(1, expect.objectContaining({ version: 4 })));
  fireEvent.click(await within(operation).findByRole('button', { name: '关 闭' }));
  fireEvent.click(within(detail).getByRole('button', { name: '关闭' }));
  api.getAsset.mockResolvedValueOnce(asset(2, '第二资产'));
  fireEvent.click(screen.getAllByRole('button', { name: '资产详情' })[1]);
  await findDialog('AS-2 · 第二资产');
  await act(async () => pending.resolve({ ...asset(), status: 'IDLE', version: 5 }));
  expect(await findDialog('AS-2 · 第二资产')).toBeTruthy();
  expect(messages.success).not.toHaveBeenCalled();
});

it('clears private data and rejects late saves and reads after permission revocation', async () => {
  const pending = deferred<AssetLedger>(), list = deferred<{ records: AssetLedger[]; total: number }>();
  api.updateAsset.mockReturnValueOnce(pending.promise);
  const view = render(<Tree />); await screen.findByText('测试资产');
  fireEvent.click(screen.getByRole('button', { name: '编辑' }));
  const dialog = await findDialog('编辑资产');
  fireEvent.click(within(dialog).getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(api.updateAsset).toHaveBeenCalledTimes(1));
  api.listAssets.mockReturnValueOnce(list.promise);
  let refresh!: Promise<void>;
  await act(async () => { refresh = Promise.resolve(ui.handlers['ui.refreshPage']?.({ pageId: 'asset-ledger', code: 'ui.refreshPage', payload: {} })).then(() => undefined); });
  await waitFor(() => expect(api.listAssets).toHaveBeenCalledTimes(2));
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] };
  view.rerender(<Tree />);
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(screen.queryByText('测试资产')).toBeNull();
  await act(async () => { pending.resolve({ ...asset(), version: 5 }); list.resolve({ records: [asset(9, '旧身份数据')], total: 1 }); await refresh; });
  expect(screen.queryByText('旧身份数据')).toBeNull();
  expect(messages.success).not.toHaveBeenCalled();
  expect(api.listAssets).toHaveBeenCalledTimes(2);
});

it('does not refresh or notify after a pending save outlives the page', async () => {
  const pending = deferred<AssetLedger>(); api.updateAsset.mockReturnValueOnce(pending.promise);
  const view = render(<Tree />); await screen.findByText('测试资产');
  fireEvent.click(screen.getByRole('button', { name: '编辑' }));
  const dialog = await findDialog('编辑资产');
  fireEvent.click(within(dialog).getByRole('button', { name: '保 存' }));
  await waitFor(() => expect(api.updateAsset).toHaveBeenCalledTimes(1));
  view.unmount(); await act(async () => pending.resolve({ ...asset(), version: 5 }));
  expect(messages.success).not.toHaveBeenCalled(); expect(api.listAssets).toHaveBeenCalledTimes(1);
});
