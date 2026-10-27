import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BusinessContract, ContractDetail } from '@/lib/contractApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), options: vi.fn(), create: vi.fn(), update: vi.fn(),
  updateStatus: vi.fn(), updateFulfillment: vi.fn(), recordPayment: vi.fn(), remind: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['contract:read', 'contract:manage'], version: 1 }));
vi.mock('@/lib/contractApi', () => ({ contractApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import ContractPage from './ContractPage';

const contract: BusinessContract = { id: 11, code: 'FIXTURE-11', name: '测试合同甲', contractType: 'SERVICE',
  counterpartyName: '测试签约主体', ownerUserId: 7, ownerLabel: '测试负责人', amount: 1000, paidAmount: 0,
  currency: 'CNY', startDate: '2026-10-22', endDate: '2027-10-22', summary: '测试合同私有摘要',
  status: 'DRAFT', fulfillmentStatus: 'NOT_STARTED', expiryState: 'NORMAL', daysUntilExpiry: 365,
  reminderCount: 0, version: 3, createdAt: '2026-10-22T09:00:00', updatedAt: '2026-10-22T09:00:00',
  canManage: true, allowedTransitions: ['ACTIVE'], allowedFulfillmentStatuses: [], canRecordPayment: false, canRemind: false };
const other: BusinessContract = { ...contract, id: 12, name: '测试合同乙', code: 'FIXTURE-12', version: 8 };
function Probe() {
  const location = useLocation(); const navigate = useNavigate();
  return <><output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
    <output aria-label="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/oa/contracts?id=12')}>选择乙</button>
    <button onClick={() => navigate('/oa/contracts')}>关闭记录路由</button></>;
}
function view(path = '/oa/contracts') {
  return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="contracts">
    <ContractPage /><Probe />
  </PageAgentContextProvider></App></MemoryRouter>;
}
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => {
  vi.clearAllMocks(); auth.permissions = ['contract:read', 'contract:manage']; auth.version = 1;
  api.list.mockResolvedValue({ records: [contract], total: 1, page: 1, size: 20, canManage: true,
    stats: { total: 1, active: 0, expiring: 0, expired: 0, outstandingAmount: 1000 } });
  api.detail.mockImplementation(async (id: number) => ({ contract: id === 11 ? contract : other, events: [] }));
  api.options.mockResolvedValue({ owners: [{ id: 7, label: '测试负责人' }], suppliers: [] });
  api.update.mockResolvedValue({ ...contract, version: 4 });
});
afterEach(cleanup);

describe('contract page Agent selection', () => {
  it('publishes applied filters without selecting the first row or unsent search text', async () => {
    render(view()); const search = await screen.findByPlaceholderText('搜索合同编号、名称、相对方或负责人');
    fireEvent.change(search, { target: { value: 'FIXTURE-11' } });
    expect(context()).toEqual({ page: 1, size: 20 });
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
    await waitFor(() => expect(context()).toEqual({ page: 1, size: 20, keyword: 'FIXTURE-11' }));
  });
  it('publishes only the selected actual identity and version and clears on close', async () => {
    render(view('/oa/contracts?id=11'));
    await waitFor(() => expect(context()).toEqual({ contractId: 11, version: 3, page: 1, size: 20 }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('contractId'));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/contracts');
  });
  it('ignores a pending detail after closing', async () => {
    let resolve!: (value: ContractDetail) => void;
    api.detail.mockReturnValue(new Promise<ContractDetail>((done) => { resolve = done; }));
    render(view('/oa/contracts?id=11'));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Close' }));
    await act(async () => resolve({ contract, events: [] }));
    expect(context()).not.toHaveProperty('contractId'); expect(screen.queryByRole('dialog')).toBeNull();
  });
  it('does not let an earlier read overwrite a newer contract selection', async () => {
    let resolve!: (value: ContractDetail) => void;
    api.detail.mockImplementation((id: number) => id === 11 ? new Promise<ContractDetail>((done) => { resolve = done; })
      : Promise.resolve({ contract: other, events: [] }));
    render(view('/oa/contracts?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ contractId: 12, version: 8 }));
    await act(async () => resolve({ contract, events: [] }));
    expect(context()).toMatchObject({ contractId: 12, version: 8 });
  });
  it('requires live management permission even if the server row flags are stale', async () => {
    auth.permissions = ['contract:read']; render(view()); await screen.findByText('FIXTURE-11');
    expect(screen.queryByRole('button', { name: '新建合同' })).toBeNull();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
    expect(api.options).not.toHaveBeenCalled();
  });
  it('does not offer an editor for an individually non-manageable contract', async () => {
    api.list.mockResolvedValue({ records: [{ ...contract, canManage: false }], total: 1, canManage: true,
      stats: { total: 1, active: 0, expiring: 0, expired: 0, outstandingAmount: 1000 } });
    render(view()); await screen.findByText('FIXTURE-11');
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
  });
  it.each(['0', '-1', '1.5', '11tail', '9007199254740992'])('does not read an invalid route ID: %s', async (id) => {
    render(view(`/oa/contracts?id=${id}`)); await screen.findByText('FIXTURE-11');
    expect(api.detail).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('contractId');
  });
  it('rejects a mismatched actual detail instead of trusting the requested ID', async () => {
    api.detail.mockResolvedValue({ contract: other, events: [] }); render(view('/oa/contracts?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(context()).not.toHaveProperty('contractId');
  });
  it('removes stale data and refetches after the live permission version changes', async () => {
    const page = render(view('/oa/contracts?id=11'));
    await waitFor(() => expect(context()).toHaveProperty('contractId', 11));
    api.detail.mockRejectedValue(new Error('FORBIDDEN')); auth.version += 1; auth.permissions = ['contract:read'];
    page.rerender(view('/oa/contracts?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(context()).not.toHaveProperty('contractId'));
    expect(screen.queryByRole('button', { name: '新建合同' })).toBeNull();
  });
  it('does not restore old details after a new-contract form is closed', async () => {
    render(view('/oa/contracts?id=11'));
    await waitFor(() => expect(context()).toHaveProperty('contractId', 11));
    fireEvent.click(screen.getByRole('button', { name: '新建合同' }));
    expect(context()).not.toHaveProperty('contractId');
    fireEvent.click(within(screen.getByLabelText('合同名称').closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('contractId'));
  });
  it('submits the selected contract version and removes the finished editor selection', async () => {
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    await waitFor(() => expect(context()).toMatchObject({ contractId: 11, version: 3 }));
    fireEvent.change(screen.getByLabelText('合同名称'), { target: { value: '测试新名称' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(11, expect.objectContaining({ name: '测试新名称', version: 3,
      startDate: '2026-10-22', endDate: '2027-10-22' })));
    await waitFor(() => expect(context()).not.toHaveProperty('contractId'));
  });
  it('keeps unsaved input and the original version on a rejected update', async () => {
    api.update.mockRejectedValue(new Error('VERSION_CONFLICT'));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    fireEvent.change(screen.getByLabelText('合同名称'), { target: { value: '待确认名称' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole('button', { name: /保\s*存/ })).not.toHaveClass('ant-btn-loading'));
    expect(screen.getByLabelText('合同名称')).toHaveValue('待确认名称');
    expect(context()).toMatchObject({ contractId: 11, version: 3 });
  });
  it('does not replace a newer selection after an earlier manual save finishes', async () => {
    let resolve!: (value: BusinessContract) => void;
    api.update.mockReturnValue(new Promise<BusinessContract>((done) => { resolve = done; }));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ contractId: 12, version: 8 }));
    await act(async () => resolve({ ...contract, version: 4 }));
    expect(context()).toMatchObject({ contractId: 12, version: 8 });
  });
  it('does not accept owner-option results after management permission is revoked', async () => {
    let resolve!: (value: { owners: { id: number; label: string }[]; suppliers: [] }) => void;
    api.options.mockReturnValue(new Promise((done) => { resolve = done; }));
    const page = render(view()); fireEvent.click(await screen.findByRole('button', { name: /编\s*辑/ }));
    await waitFor(() => expect(api.options).toHaveBeenCalledTimes(1));
    auth.version += 1; auth.permissions = ['contract:read']; page.rerender(view());
    await act(async () => resolve({ owners: [{ id: 7, label: '迟到私有负责人' }], suppliers: [] }));
    expect(screen.queryByText('迟到私有负责人')).toBeNull();
    expect(context()).not.toHaveProperty('contractId');
  });
  it('retries a failed detail at the same URL', async () => {
    api.detail.mockRejectedValueOnce(new Error('FORBIDDEN')); render(view('/oa/contracts?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    fireEvent.click(await screen.findByRole('button', { name: /查\s*看/ }));
    await waitFor(() => expect(context()).toHaveProperty('contractId', 11)); expect(api.detail).toHaveBeenCalledTimes(2);
  });
  it.each([
    ['status', '完成合同', '确认合同状态变更', 'updateStatus', 'COMPLETED'],
    ['fulfillment', '标记已履约', '更新履约状态', 'updateFulfillment', 'FULFILLED'],
    ['reminder', '催办到期', '确认发送到期提醒？', 'remind', undefined],
  ] as const)('refreshes the selected version after a confirmed %s operation', async (_, label, titleText, method, target) => {
    const active: BusinessContract = { ...contract, status: 'ACTIVE', allowedTransitions: ['COMPLETED'],
      allowedFulfillmentStatuses: ['FULFILLED'], canRemind: true };
    api.detail.mockResolvedValueOnce({ contract: active, events: [] }).mockResolvedValue({ contract: { ...active, version: 4 }, events: [] });
    api[method].mockResolvedValue({ ...active, version: 4 }); render(view('/oa/contracts?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: label }));
    const [title] = await screen.findAllByText(titleText);
    fireEvent.click(within(title.closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: label }));
    await waitFor(() => method === 'remind' ? expect(api[method]).toHaveBeenCalledWith(11, 3)
      : expect(api[method]).toHaveBeenCalledWith(11, target, 3, undefined));
    await waitFor(() => expect(context()).toMatchObject({ contractId: 11, version: 4 }));
  });
  it('does not execute a pending confirmation after permission is revoked', async () => {
    const page = render(view('/oa/contracts?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: '启用合同' }));
    const [title] = await screen.findAllByText('确认合同状态变更');
    const confirm = within(title.closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: '启用合同' });
    auth.version += 1; auth.permissions = ['contract:read']; page.rerender(view('/oa/contracts?id=11'));
    await waitFor(() => expect(screen.queryByRole('button', { name: '新建合同' })).toBeNull()); fireEvent.click(confirm);
    await waitFor(() => expect(screen.queryAllByText('确认合同状态变更')).toHaveLength(0));
    expect(api.updateStatus).not.toHaveBeenCalled();
  });
  it('records the payment ledger at the selected version and refreshes actual details', async () => {
    const active: BusinessContract = { ...contract, status: 'ACTIVE', canRecordPayment: true };
    api.detail.mockResolvedValueOnce({ contract: active, events: [] }).mockResolvedValue({ contract: { ...active, version: 4, paidAmount: 100 }, events: [] });
    api.recordPayment.mockResolvedValue({ ...active, version: 4, paidAmount: 100 }); render(view('/oa/contracts?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: '登记付款' }));
    fireEvent.change(screen.getByLabelText('本次付款金额'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('付款凭证号'), { target: { value: 'FIXTURE-PAY' } });
    const modal = screen.getByText('登记合同付款').closest('[role="dialog"]') as HTMLElement;
    fireEvent.click(within(modal).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() => expect(api.recordPayment).toHaveBeenCalledWith(11, 100, expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), 'FIXTURE-PAY', 3, undefined));
    await waitFor(() => expect(context()).toMatchObject({ contractId: 11, version: 4 }));
  });
  it('does not refresh an old detail after a status request completes on another selection', async () => {
    let resolve!: (value: BusinessContract) => void;
    api.updateStatus.mockReturnValue(new Promise<BusinessContract>((done) => { resolve = done; }));
    render(view('/oa/contracts?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: '启用合同' }));
    const [title] = await screen.findAllByText('确认合同状态变更');
    fireEvent.click(within(title.closest('[role="dialog"]') as HTMLElement).getByRole('button', { name: '启用合同' }));
    await waitFor(() => expect(api.updateStatus).toHaveBeenCalledWith(11, 'ACTIVE', 3, undefined));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ contractId: 12, version: 8 }));
    await act(async () => resolve({ ...contract, status: 'ACTIVE', version: 4 }));
    expect(context()).toMatchObject({ contractId: 12, version: 8 });
    expect(api.detail.mock.calls.filter(([id]) => id === 11)).toHaveLength(1);
  });
  it('does not reopen a closed contract after an already submitted ledger entry finishes', async () => {
    let resolve!: (value: BusinessContract) => void;
    const active: BusinessContract = { ...contract, status: 'ACTIVE', canRecordPayment: true };
    api.detail.mockResolvedValue({ contract: active, events: [] });
    api.recordPayment.mockReturnValue(new Promise<BusinessContract>((done) => { resolve = done; }));
    render(view('/oa/contracts?id=11'));
    fireEvent.click(await screen.findByRole('button', { name: '登记付款' }));
    fireEvent.change(screen.getByLabelText('本次付款金额'), { target: { value: '100' } });
    fireEvent.change(screen.getByLabelText('付款凭证号'), { target: { value: 'FIXTURE-PAY' } });
    const modal = screen.getByText('登记合同付款').closest('[role="dialog"]') as HTMLElement;
    fireEvent.click(within(modal).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() => expect(api.recordPayment).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '关闭记录路由' }));
    await waitFor(() => expect(context()).not.toHaveProperty('contractId'));
    await act(async () => resolve({ ...active, version: 4, paidAmount: 100 }));
    expect(context()).not.toHaveProperty('contractId');
    expect(screen.queryByRole('dialog')).toBeNull(); expect(api.detail).toHaveBeenCalledTimes(1);
  });
});
