import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '@/components/auth/AuthProvider';
import type { AssetLedger } from '@/lib/adminAssetsApi';

const api = vi.hoisted(() => ({ listAssets: vi.fn(), getAsset: vi.fn(), returnAsset: vi.fn(), updateAsset: vi.fn(), createAsset: vi.fn() }));
const auth = vi.hoisted(() => ({ me: vi.fn() }));
vi.mock('@/lib/authApi', async (load) => {
  const original = await load<typeof import('@/lib/authApi')>();
  return { ...original, authApi: { ...original.authApi, me: auth.me } };
});
vi.mock('@/lib/adminAssetsApi', async (load) => ({
  ...await load<typeof import('@/lib/adminAssetsApi')>(), adminAssetsApi: api,
}));
vi.mock('@/lib/hrApi', () => ({ hrApi: { overview: vi.fn().mockResolvedValue({ departments: [], positions: [], employees: [] }) } }));
import AssetLedgerPage from './AssetLedgerPage';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const asset: AssetLedger = { id: 9, assetCode: 'A1', name: 'Test asset', category: 'Device', status: 'IDLE', version: 0,
  history: [], createdAt: '2026-10-20T09:00:00', updatedAt: '2026-10-20T09:00:00', canEdit: true, canDelete: true };
function authUser(permissions: string[], permissionVersion: number) {
  return { id: 7, tenantId: 1, name: 'Test user', email: 'test@example.invalid', role: 'EMPLOYEE',
    roles: ['EMPLOYEE'], permissions, dataScopes: ['SELF'], permissionVersion };
}
function AuthVersion() { return <output aria-label="auth-version">{useAuth().user?.permissionVersion ?? 0}</output>; }
function renderPage(path = '/oa/asset-ledger') { return render(<MemoryRouter initialEntries={[path]}><AuthProvider><App><AssetLedgerPage /><AuthVersion /></App></AuthProvider></MemoryRouter>); }
beforeEach(() => {
  vi.clearAllMocks();
  auth.me.mockResolvedValue(authUser(['asset:write'], 1));
  api.listAssets.mockResolvedValue({ records: [asset], total: 1, page: 1, size: 10 });
  api.getAsset.mockResolvedValue({ ...asset, id: 23, name: 'Actual saved asset', version: 3 });
});
afterEach(cleanup);

function ContextSnapshot() { return <output aria-label="asset-context">{JSON.stringify(usePageAgentContextSnapshot())}</output>; }
it('publishes the selected asset version only while the editor is open', async () => {
  render(<MemoryRouter><AuthProvider><App><PageAgentContextProvider pageId="asset-ledger"><AssetLedgerPage /><ContextSnapshot /></PageAgentContextProvider></App></AuthProvider></MemoryRouter>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('asset-context').textContent || '{}')).toMatchObject({ assetId: 9, version: 0 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Cancel|取\s*消/ }));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('asset-context').textContent || '{}')).not.toHaveProperty('assetId'));
});

it('opens the actual asset returned by the server for an explicit route selection', async () => {
  renderPage('/oa/asset-ledger?id=23');
  expect(await screen.findByRole('dialog')).toBeTruthy();
  await waitFor(() => expect(api.getAsset).toHaveBeenCalledWith(23));
  expect(screen.getByText(/Actual saved asset/)).toBeTruthy();
  expect(api.updateAsset).not.toHaveBeenCalled();
  expect(api.createAsset).not.toHaveBeenCalled();
});

it('does not treat a malformed route ID as an asset selection', async () => {
  renderPage('/oa/asset-ledger?id=9007199254740992');
  await screen.findByText('Test asset');
  expect(api.getAsset).not.toHaveBeenCalled();
  expect(screen.queryByRole('dialog')).toBeNull();
});

it('closes a failed detail load rather than showing a stale or invented asset', async () => {
  api.getAsset.mockRejectedValue(new Error('Denied'));
  renderPage('/oa/asset-ledger?id=23');
  await waitFor(() => expect(api.getAsset).toHaveBeenCalledWith(23));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(screen.queryByText(/Actual saved asset/)).toBeNull();
  expect(api.updateAsset).not.toHaveBeenCalled();
});

it('ignores a late detail response after the user closes the selected record', async () => {
  let complete!: (value: AssetLedger) => void;
  api.getAsset.mockReturnValue(new Promise<AssetLedger>((resolve) => { complete = resolve; }));
  renderPage('/oa/asset-ledger?id=23');
  const drawer = await screen.findByRole('dialog');
  fireEvent.click(within(drawer).getByRole('button', { name: /Close|关\s*闭/ }));
  await act(async () => complete({ ...asset, id: 23, name: 'Late private asset' }));
  expect(screen.queryByText(/Late private asset/)).toBeNull();
  expect(screen.queryByRole('dialog')).toBeNull();
});

it('does not reopen a closed record when an already submitted asset operation completes later', async () => {
  let complete!: (value: AssetLedger) => void;
  api.returnAsset.mockReturnValue(new Promise<AssetLedger>((resolve) => { complete = resolve; }));
  api.getAsset.mockResolvedValue({ ...asset, id: 23, name: 'In use asset', status: 'IN_USE' });
  renderPage('/oa/asset-ledger?id=23');
  // Initial authentication refreshes the protected route detail before actions become available.
  await waitFor(() => expect(screen.getByLabelText('auth-version').textContent).toBe('1'), { timeout: 3000 });
  fireEvent.click(await screen.findByRole('button', { name: '办理归还' }, { timeout: 3000 }));
  const modal = (await screen.findByText('资产归还')).closest('[role="dialog"]') as HTMLElement;
  fireEvent.click(within(modal).getByRole('button', { name: /OK|确\s*定/ }));
  await waitFor(() => expect(api.returnAsset).toHaveBeenCalledWith(23, expect.objectContaining({ version: 0 })));
  fireEvent.click(within(modal).getByRole('button', { name: /Cancel|取\s*消/ }));
  const drawer = screen.getByText(/In use asset/).closest('[role="dialog"]') as HTMLElement;
  fireEvent.click(within(drawer).getByRole('button', { name: /Close|关\s*闭/ }));
  await act(async () => complete({ ...asset, id: 23, name: 'Late operation result', version: 1 }));
  expect(screen.queryByText(/Late operation result/)).toBeNull();
  expect(screen.queryByRole('dialog')).toBeNull();
});

it('lets a writer create the first asset without requiring an editable data row', async () => {
  api.listAssets.mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: '新增资产' }));
  expect(within(screen.getByRole('dialog')).getByLabelText('资产编码')).toBeTruthy();
});

it('hides creation and stale editable-row actions without the real write permission', async () => {
  auth.me.mockResolvedValue(authUser([], 2));
  renderPage();
  await screen.findByText('Test asset');
  await waitFor(() => expect(screen.getByLabelText('auth-version').textContent).toBe('2'));
  expect(screen.queryByRole('button', { name: '新增资产' })).toBeNull();
  expect(screen.queryByRole('button', { name: '编辑' })).toBeNull();
});

it('disables the open asset editor when the server permission snapshot changes', async () => {
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  auth.me.mockResolvedValue(authUser([], 2));
  fireEvent.focus(window);
  await waitFor(() => expect(screen.getByLabelText('auth-version').textContent).toBe('2'));
  const save = within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ });
  expect(save.hasAttribute('disabled')).toBe(true);
  fireEvent.click(save);
  expect(api.updateAsset).not.toHaveBeenCalled();
  expect(api.createAsset).not.toHaveBeenCalled();
});

it('refreshes stale resource flags after write permission is granted without signing in again', async () => {
  auth.me.mockResolvedValue(authUser([], 1));
  api.listAssets.mockResolvedValue({ records: [{ ...asset, canEdit: false }], total: 1, page: 1, size: 10 });
  renderPage();
  await screen.findByText('Test asset');
  expect(screen.queryByRole('button', { name: '编辑' })).toBeNull();
  api.listAssets.mockResolvedValue({ records: [asset], total: 1, page: 1, size: 10 });
  auth.me.mockResolvedValue(authUser(['asset:write'], 2));
  fireEvent.focus(window);
  expect(await screen.findByRole('button', { name: '编辑' })).toBeTruthy();
});
