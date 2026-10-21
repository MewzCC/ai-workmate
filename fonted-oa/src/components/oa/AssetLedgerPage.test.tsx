import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AuthProvider, useAuth } from '@/components/auth/AuthProvider';
import type { AssetLedger } from '@/lib/adminAssetsApi';

const api = vi.hoisted(() => ({ listAssets: vi.fn(), updateAsset: vi.fn(), createAsset: vi.fn() }));
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
function renderPage() { return render(<AuthProvider><App><AssetLedgerPage /><AuthVersion /></App></AuthProvider>); }
beforeEach(() => {
  vi.clearAllMocks();
  auth.me.mockResolvedValue(authUser(['asset:write'], 1));
  api.listAssets.mockResolvedValue({ records: [asset], total: 1, page: 1, size: 10 });
});
afterEach(cleanup);

function ContextSnapshot() { return <output aria-label="asset-context">{JSON.stringify(usePageAgentContextSnapshot())}</output>; }
it('publishes the selected asset version only while the editor is open', async () => {
  render(<AuthProvider><App><PageAgentContextProvider pageId="asset-ledger"><AssetLedgerPage /><ContextSnapshot /></PageAgentContextProvider></App></AuthProvider>);
  fireEvent.click(await screen.findByRole('button', { name: '编辑' }));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('asset-context').textContent || '{}')).toMatchObject({ assetId: 9, version: 0 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /Cancel|取\s*消/ }));
  await waitFor(() => expect(JSON.parse(screen.getByLabelText('asset-context').textContent || '{}')).not.toHaveProperty('assetId'));
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
