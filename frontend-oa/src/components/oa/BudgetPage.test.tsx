import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { Budget } from '@/lib/budgetApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), options: vi.fn(), update: vi.fn(), create: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['budget:manage', 'budget:read'], version: 1 }));
vi.mock('@/lib/budgetApi', () => ({ budgetApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import BudgetPage from './BudgetPage';

const budget: Budget = { id: 11, code: 'B-11', name: '预算甲', fiscalYear: 2026, ownerUserId: 7,
  totalAmount: 1000, currency: 'CNY', warningThreshold: 80, ownerLabel: '负责人', occupiedAmount: 0,
  spentAmount: 0, availableAmount: 1000, utilizationPercent: 0, alertLevel: 'NORMAL', status: 'DRAFT',
  version: 3, updatedAt: '', canManage: true, allowedTransitions: [] };
const other: Budget = { ...budget, id: 12, code: 'B-12', name: '预算乙', version: 8 };
function Probe() {
  const context = usePageAgentContextSnapshot();
  const location = useLocation();
  const navigate = useNavigate();
  return <><output aria-label="context">{JSON.stringify(context)}</output>
    <output aria-label="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/oa/budget?id=12')}>选择乙</button></>;
}
function view(path = '/oa/budget') {
  return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="budget">
    <BudgetPage /><Probe />
  </PageAgentContextProvider></App></MemoryRouter>;
}
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => {
  vi.clearAllMocks();
  auth.permissions = ['budget:manage', 'budget:read']; auth.version = 1;
  api.list.mockResolvedValue({ records: [budget, other], total: 2, page: 1, size: 20, canManage: true,
    stats: { totalPlans: 2, activePlans: 0, warningPlans: 0, totalAmount: 0, occupiedAmount: 0, spentAmount: 0, availableAmount: 0 } });
  api.detail.mockImplementation(async (id: number) => ({ budget: id === 11 ? budget : other, transactions: [] }));
  api.options.mockResolvedValue({ owners: [{ id: 7, label: '负责人' }] });
  api.update.mockResolvedValue({ ...budget, version: 4 });
});
afterEach(cleanup);

describe('budget page Agent selection', () => {
  it('publishes filters without leaking an owner identity or a list row as the selected record', async () => {
    render(view());
    await screen.findByText('预算甲');
    const search = screen.getByPlaceholderText('搜索编号、名称或负责人');
    fireEvent.change(search, { target: { value: 'B-11' } });
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
    await waitFor(() => expect(context()).toMatchObject({ keyword: 'B-11', page: 1, size: 20 }));
    expect(context()).not.toHaveProperty('budgetId');
    expect(context()).not.toHaveProperty('ownerUserId');
  });

  it('loads the requested real detail and publishes its ID and version, then clears selection on close', async () => {
    render(view('/oa/budget?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 3 }));
    const drawer = screen.getByRole('dialog');
    expect(within(drawer).getByText('B-11')).toBeTruthy();
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('budgetId'));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/budget');
  });

  it('keeps the edited version only while the editor is active', async () => {
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 3 }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('budgetId'));
  });

  it('does not restore a closed detail when its read finishes late', async () => {
    let resolve!: (value: { budget: Budget; transactions: [] }) => void;
    api.detail.mockReturnValue(new Promise((done) => { resolve = done; }));
    render(view('/oa/budget?id=11'));
    const drawer = await screen.findByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await act(async () => resolve({ budget, transactions: [] }));
    expect(context()).not.toHaveProperty('budgetId');
    expect(screen.getByLabelText('location').textContent).toBe('/oa/budget');
  });

  it('never overwrites a newer selected budget with an older detail response', async () => {
    let resolve!: (value: { budget: Budget; transactions: [] }) => void;
    api.detail.mockImplementation((id: number) => id === 11
      ? new Promise((done) => { resolve = done; }) : Promise.resolve({ budget: other, transactions: [] }));
    render(view('/oa/budget?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 12, version: 8 }));
    await act(async () => resolve({ budget, transactions: [] }));
    expect(context()).toMatchObject({ budgetId: 12, version: 8 });
  });

  it('does not trust stale manageable rows after the live write permission is lost', async () => {
    auth.permissions = ['budget:read'];
    render(view());
    await screen.findByText('预算甲');
    expect(screen.queryByRole('button', { name: '新建预算' })).toBeNull();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
  });

  it.each(['0', '-1', '1.5', '9007199254740992', 'invalid'])('does not read or publish an invalid requested ID: %s', async (id) => {
    render(view(`/oa/budget?id=${id}`));
    await screen.findByText('预算甲');
    expect(api.detail).not.toHaveBeenCalled();
    expect(context()).not.toHaveProperty('budgetId');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('rejects a different returned resource instead of guessing from the visible rows', async () => {
    api.detail.mockResolvedValue({ budget: other, transactions: [] });
    render(view('/oa/budget?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(context()).not.toHaveProperty('budgetId');
  });

  it('clears stale selection and refetches after a live permission version changes', async () => {
    const rendered = render(view('/oa/budget?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 3 }));
    api.detail.mockRejectedValue(new Error('PERMISSION_DENIED'));
    auth.permissions = ['budget:read']; auth.version = 2;
    rendered.rerender(view('/oa/budget?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(context()).not.toHaveProperty('budgetId'));
    expect(screen.queryByRole('button', { name: '新建预算' })).toBeNull();
  });

  it('ignores owner-option responses that arrive after write permission is revoked', async () => {
    let resolve!: (value: { owners: [] }) => void;
    api.options.mockReturnValue(new Promise((done) => { resolve = done; }));
    const rendered = render(view());
    fireEvent.click(await screen.findByRole('button', { name: '新建预算' }));
    auth.permissions = ['budget:read']; auth.version = 2;
    rendered.rerender(view());
    await act(async () => resolve({ owners: [] }));
    expect(context()).not.toHaveProperty('budgetId');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('submits the selected version rather than any later list version', async () => {
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText('预算名称'), { target: { value: '预算甲更新' } });
    fireEvent.click(within(dialog).getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(11,
      expect.objectContaining({ name: '预算甲更新', version: 3 })));
    await waitFor(() => expect(context()).not.toHaveProperty('budgetId'));
  });

  it('does not keep an old editor selected after navigating to another returned budget', async () => {
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 3 }));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 12, version: 8 }));
    expect(screen.queryByRole('textbox', { name: '预算名称' })).toBeNull();
  });

  it('refreshes the selected detail version after a successful manual edit', async () => {
    api.detail.mockResolvedValueOnce({ budget, transactions: [] })
      .mockResolvedValue({ budget: { ...budget, version: 4 }, transactions: [] });
    render(view('/oa/budget?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 3 }));
    fireEvent.click(screen.getAllByRole('button', { name: /编\s*辑/ })[0]);
    await screen.findByLabelText('预算名称');
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledOnce());
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 4 }));
  });

  it('lets the user retry a failed detail without changing the record URL', async () => {
    api.detail.mockRejectedValueOnce(new Error('temporarily unavailable'))
      .mockResolvedValue({ budget, transactions: [] });
    render(view('/oa/budget?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledOnce());
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    fireEvent.click(await screen.findByRole('button', { name: '预算甲' }));
    await waitFor(() => expect(context()).toMatchObject({ budgetId: 11, version: 3 }));
    expect(api.detail).toHaveBeenCalledTimes(2);
  });
});
