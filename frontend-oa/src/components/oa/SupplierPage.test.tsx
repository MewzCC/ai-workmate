import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Supplier, SupplierDetail } from '@/lib/supplierApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), create: vi.fn(), update: vi.fn(), updateStatus: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['supplier:manage', 'supplier:read'], version: 1 }));
vi.mock('@/lib/supplierApi', () => ({ supplierApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import SupplierPage from './SupplierPage';

const supplier: Supplier = { id: 11, code: 'S-11', name: '供应商甲', category: 'SERVICE', supplierLevel: 'STANDARD',
  contactName: '测试联系人', contactEmail: 'fixture@example.invalid', contactPhone: 'TEST-ONLY',
  riskNote: '测试风险备注', paymentTerms: '测试结算资料', unifiedSocialCreditCode: 'T'.repeat(18),
  status: 'DRAFT', version: 3, canManage: true, allowedTransitions: [],
  createdAt: '2026-10-22T09:00:00', updatedAt: '2026-10-22T09:00:00' };
const other: Supplier = { ...supplier, id: 12, code: 'S-12', name: '供应商乙', version: 8 };
function Probe() {
  const location = useLocation(); const navigate = useNavigate();
  return <><output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
    <output aria-label="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/oa/suppliers?id=12')}>选择乙</button></>;
}
function view(path = '/oa/suppliers') {
  return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="suppliers">
    <SupplierPage /><Probe />
  </PageAgentContextProvider></App></MemoryRouter>;
}
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => {
  vi.clearAllMocks(); auth.permissions = ['supplier:manage', 'supplier:read']; auth.version = 1;
  api.list.mockResolvedValue({ records: [supplier, other], total: 2, page: 1, size: 20, canManage: true,
    stats: { total: 2, active: 0, suspended: 0, blacklisted: 0 } });
  api.detail.mockImplementation(async (id: number) => ({ supplier: id === 11 ? supplier : other, statusHistory: [] }));
  api.update.mockResolvedValue({ ...supplier, version: 4 });
});
afterEach(cleanup);

describe('supplier page Agent selection', () => {
  it('publishes applied filters, not unsent text or the first row', async () => {
    render(view());
    const search = await screen.findByPlaceholderText('搜索编码、名称、信用代码或联系人');
    fireEvent.change(search, { target: { value: 'S-11' } });
    expect(context()).not.toHaveProperty('keyword');
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
    await waitFor(() => expect(context()).toMatchObject({ keyword: 'S-11', page: 1, size: 20 }));
    expect(context()).not.toHaveProperty('supplierId');
  });

  it('publishes only the actual selected identity and version, never contact or risk fields', async () => {
    render(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ supplierId: 11, version: 3 }));
    expect(Object.keys(context()).sort()).toEqual(['page', 'size', 'supplierId', 'version']);
    const drawer = screen.getByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('supplierId'));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/suppliers');
  });

  it('does not publish a late response after the user closes the detail', async () => {
    let resolve!: (value: SupplierDetail) => void;
    api.detail.mockReturnValue(new Promise<SupplierDetail>((done) => { resolve = done; }));
    render(view('/oa/suppliers?id=11'));
    const drawer = await screen.findByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await act(async () => resolve({ supplier, statusHistory: [] }));
    expect(context()).not.toHaveProperty('supplierId');
    expect(screen.getByLabelText('location').textContent).toBe('/oa/suppliers');
  });

  it('does not let an earlier request replace the newer supplier', async () => {
    let resolve!: (value: SupplierDetail) => void;
    api.detail.mockImplementation((id: number) => id === 11
      ? new Promise<SupplierDetail>((done) => { resolve = done; }) : Promise.resolve({ supplier: other, statusHistory: [] }));
    render(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ supplierId: 12, version: 8 }));
    await act(async () => resolve({ supplier, statusHistory: [] }));
    expect(context()).toMatchObject({ supplierId: 12, version: 8 });
  });

  it('requires live management permission as well as server row flags', async () => {
    auth.permissions = ['supplier:read'];
    render(view());
    await screen.findByText('S-11');
    expect(screen.queryByRole('button', { name: '新增供应商' })).toBeNull();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
  });

  it('does not offer an editor for an individually non-manageable supplier', async () => {
    api.list.mockResolvedValue({ records: [{ ...supplier, canManage: false }], total: 1, page: 1, size: 20,
      canManage: true, stats: { total: 1, active: 0, suspended: 0, blacklisted: 0 } });
    render(view());
    await screen.findByText('S-11');
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
  });

  it.each(['0', '-1', '1.5', '11tail', '9007199254740992'])('rejects invalid route selection: %s', async (id) => {
    render(view(`/oa/suppliers?id=${id}`));
    await screen.findByText('S-11');
    expect(api.detail).not.toHaveBeenCalled();
    expect(context()).not.toHaveProperty('supplierId');
  });

  it('rejects a different returned supplier rather than trusting the URL', async () => {
    api.detail.mockResolvedValue({ supplier: other, statusHistory: [] });
    render(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(context()).not.toHaveProperty('supplierId');
  });

  it('removes the selected supplier when refreshed permissions deny its detail', async () => {
    const page = render(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ supplierId: 11, version: 3 }));
    api.detail.mockRejectedValue(new Error('FORBIDDEN'));
    auth.permissions = ['supplier:read']; auth.version += 1;
    page.rerender(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(context()).not.toHaveProperty('supplierId'));
    expect(screen.queryByRole('button', { name: '新增供应商' })).toBeNull();
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('does not restore the previous detail after closing a new-supplier form', async () => {
    render(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ supplierId: 11 }));
    fireEvent.click(screen.getByRole('button', { name: '新增供应商' }));
    expect(context()).not.toHaveProperty('supplierId');
    const form = screen.getByLabelText('供应商全称').closest('[role="dialog"]')!;
    fireEvent.click(within(form as HTMLElement).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('supplierId'));
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('submits the selected version and clears the finished editor selection', async () => {
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    expect(context()).toMatchObject({ supplierId: 11, version: 3 });
    fireEvent.change(screen.getByLabelText('供应商全称'), { target: { value: '测试新名称' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(11, expect.objectContaining({ name: '测试新名称', version: 3 })));
    await waitFor(() => expect(context()).not.toHaveProperty('supplierId'));
  });

  it('preserves the selected version and form after a rejected update', async () => {
    api.update.mockRejectedValue(new Error('VERSION_CONFLICT'));
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    fireEvent.change(screen.getByLabelText('供应商全称'), { target: { value: '待确认名称' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole('button', { name: /保\s*存/ })).not.toHaveClass('ant-btn-loading'));
    expect(screen.getByLabelText('供应商全称')).toHaveValue('待确认名称');
    expect(context()).toMatchObject({ supplierId: 11, version: 3 });
  });

  it('does not replace a newly selected supplier after an older manual write finishes', async () => {
    let resolve!: (value: Supplier) => void;
    api.update.mockReturnValue(new Promise<Supplier>((done) => { resolve = done; }));
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ supplierId: 12, version: 8 }));
    await act(async () => resolve({ ...supplier, version: 4 }));
    expect(context()).toMatchObject({ supplierId: 12, version: 8 });
    expect(screen.queryByLabelText('供应商全称')).not.toBeVisible();
  });

  it('allows a failed detail to be retried at the same route', async () => {
    api.detail.mockRejectedValueOnce(new Error('FORBIDDEN'));
    render(view('/oa/suppliers?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    fireEvent.click((await screen.findAllByRole('button', { name: /查\s*看/ }))[0]);
    await waitFor(() => expect(context()).toMatchObject({ supplierId: 11, version: 3 }));
    expect(api.detail).toHaveBeenCalledTimes(2);
  });
});
