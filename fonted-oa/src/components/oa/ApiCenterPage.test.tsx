import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { IntegrationEndpoint, IntegrationInvocation } from '@/lib/integrationApi';
import { message } from '@/lib/antdMessage';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), options: vi.fn(), create: vi.fn(), update: vi.fn(), status: vi.fn(), execute: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:api-center', 'integration:endpoint:manage', 'integration:endpoint:execute'], version: 1 }));
vi.mock('@/lib/integrationApi', () => ({ integrationApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import ApiCenterPage from './ApiCenterPage';

const endpoint: IntegrationEndpoint = { id: 11, code: 'EP-11', name: '测试接口甲', upstreamCode: 'sandbox', method: 'GET',
  relativePath: '/fixture', requestTemplate: '{"fixture":"private"}', description: '测试说明', status: 'DRAFT', version: 3,
  canManage: true, canExecute: true, allowedTransitions: ['ACTIVE', 'DISABLED'], updatedAt: '2026-10-23T09:00:00' };
const other: IntegrationEndpoint = { ...endpoint, id: 12, code: 'EP-12', name: '测试接口乙', version: 8 };
type Detail = { endpoint: IntegrationEndpoint; invocations: IntegrationInvocation[] };
function Probe() {
  const location = useLocation(); const navigate = useNavigate();
  return <><output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
    <output aria-label="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/oa/api-center?id=12')}>选择乙</button>
    <button onClick={() => navigate('/elsewhere')}>离开页面</button></>;
}
function view(path = '/oa/api-center', showPage = true) {
  return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="api-center">
    {showPage && <ApiCenterPage />}<Probe />
  </PageAgentContextProvider></App></MemoryRouter>;
}
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => {
  vi.clearAllMocks(); auth.permissions = ['route:api-center', 'integration:endpoint:manage', 'integration:endpoint:execute']; auth.version = 1;
  api.list.mockResolvedValue({ records: [endpoint, other], total: 2, page: 1, size: 20,
    canManage: true, canExecute: true, stats: { total: 2, active: 0, disabled: 0, successful: 0, failed: 0 } });
  api.detail.mockImplementation(async (id: number) => ({ endpoint: id === 11 ? endpoint : other, invocations: [] }));
  api.options.mockResolvedValue({ upstreams: [{ code: 'sandbox', label: '测试沙箱', available: true }] });
  api.update.mockResolvedValue({ ...endpoint, version: 4 });
  api.status.mockResolvedValue({ ...endpoint, status: 'ACTIVE', version: 4 });
  api.execute.mockResolvedValue({ id: 91, outcome: 'SUCCESS', durationMs: 10, operatorLabel: '测试', createdAt: '2026-10-23T09:01:00' });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe('integration endpoint Agent selection', () => {
  it('publishes applied filters rather than typed text, metadata or the first row', async () => {
    render(view());
    const search = await screen.findByPlaceholderText('搜索编码、名称或上游');
    fireEvent.change(search, { target: { value: 'EP-11' } });
    expect(context()).not.toHaveProperty('keyword');
    fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
    await waitFor(() => expect(context()).toEqual({ keyword: 'EP-11', page: 1, size: 20 }));
  });

  it('opens a protected result selection and publishes only its actual identity and version', async () => {
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toEqual({ endpointId: 11, version: 3, page: 1, size: 20 }));
    const drawer = screen.getByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(context()).not.toHaveProperty('endpointId'));
    expect(screen.getByLabelText('location').textContent).toBe('/oa/api-center');
  });

  it.each(['0', '-1', '1.5', '11tail', '9007199254740992'])('rejects invalid route identity: %s', async (id) => {
    render(view(`/oa/api-center?id=${id}`));
    await screen.findByText('EP-11');
    expect(api.detail).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('endpointId');
  });

  it('does not publish a mismatched protected detail', async () => {
    api.detail.mockResolvedValue({ endpoint: other, invocations: [] });
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(context()).not.toHaveProperty('endpointId');
  });

  it('ignores detail completion after closing', async () => {
    let resolve!: (detail: Detail) => void;
    api.detail.mockReturnValue(new Promise<Detail>((done) => { resolve = done; }));
    render(view('/oa/api-center?id=11'));
    const drawer = await screen.findByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await act(async () => resolve({ endpoint, invocations: [] }));
    expect(context()).not.toHaveProperty('endpointId'); expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('keeps the newer endpoint when an old detail finishes later', async () => {
    let resolve!: (detail: Detail) => void;
    api.detail.mockImplementation((id: number) => id === 11 ? new Promise<Detail>((done) => { resolve = done; })
      : Promise.resolve({ endpoint: other, invocations: [] }));
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledWith(11));
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 12, version: 8 }));
    await act(async () => resolve({ endpoint, invocations: [] }));
    expect(context()).toMatchObject({ endpointId: 12, version: 8 });
  });

  it('requires realtime management and execution permission as well as old row flags', async () => {
    auth.permissions = ['route:api-center'];
    render(view()); await screen.findByText('EP-11');
    expect(screen.queryByRole('button', { name: '登记接口' })).toBeNull();
    expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
    expect(screen.queryByRole('button', { name: '执行联调' })).toBeNull();
    expect(api.options).not.toHaveBeenCalled();
  });

  it('does not fetch business data after losing the page permission', async () => {
    auth.permissions = [];
    render(view('/oa/api-center?id=11'));
    await act(async () => undefined);
    expect(api.list).not.toHaveBeenCalled(); expect(api.detail).not.toHaveBeenCalled();
  });

  it('clears selected data and late requests on permission refresh', async () => {
    const page = render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11 }));
    auth.permissions = []; auth.version += 1;
    page.rerender(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).not.toHaveProperty('endpointId'));
    expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByText('EP-11')).toBeNull();
  });

  it('does not inherit a previous selection in a new endpoint form', async () => {
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11 }));
    fireEvent.click(screen.getByRole('button', { name: '登记接口' }));
    await screen.findByLabelText('接口编码');
    expect(context()).not.toHaveProperty('endpointId');
    const drawer = screen.getByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    expect(context()).not.toHaveProperty('endpointId');
  });

  it('uses the protected editor version and does not publish unsaved payload fields', async () => {
    api.detail.mockResolvedValue({ endpoint: { ...endpoint, version: 9 }, invocations: [] });
    render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11, version: 9 }));
    fireEvent.change(screen.getByLabelText('接口名称'), { target: { value: '未保存名称' } });
    expect(Object.keys(context()).sort()).toEqual(['endpointId', 'page', 'size', 'version']);
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith(11, expect.objectContaining({ version: 9, name: '未保存名称' })));
    await waitFor(() => expect(context()).not.toHaveProperty('endpointId'));
  });

  it('invalidates a pending editor options request when the drawer is closed', async () => {
    let resolve!: (options: { upstreams: [] }) => void;
    api.options.mockReturnValue(new Promise<{ upstreams: [] }>((done) => { resolve = done; }));
    render(view()); fireEvent.click(await screen.findByRole('button', { name: '登记接口' }));
    const drawer = await screen.findByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await act(async () => resolve({ upstreams: [] }));
    expect(screen.queryByRole('dialog')).toBeNull(); expect(context()).not.toHaveProperty('endpointId');
  });

  it('revalidates a status confirmation after its detail is closed', async () => {
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11 }));
    const drawer = screen.getByRole('dialog');
    fireEvent.click(within(drawer).getByRole('button', { name: '已启用' }));
    fireEvent.click(within(drawer).getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(api.status).not.toHaveBeenCalled();
  });

  it('refreshes only the selected detail and publishes its new version after status change', async () => {
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11, version: 3 }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '已启用' }));
    api.detail.mockResolvedValue({ endpoint: { ...endpoint, status: 'ACTIVE', version: 4, allowedTransitions: ['DISABLED'] }, invocations: [] });
    const confirmation = screen.getAllByRole('dialog').find((dialog) => within(dialog).queryAllByText('确认变更接口状态').length > 0)!;
    fireEvent.click(within(confirmation).getByRole('button', { name: /确\s*认|OK/ }));
    await waitFor(() => expect(api.status).toHaveBeenCalledWith(11, 'ACTIVE', 3));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11, version: 4 }));
  });

  it('keeps editor input and version when the server rejects a stale save', async () => {
    api.update.mockRejectedValue(new Error('VERSION_CONFLICT'));
    render(view()); fireEvent.click((await screen.findAllByRole('button', { name: /编\s*辑/ }))[0]);
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11, version: 3 }));
    fireEvent.change(screen.getByLabelText('接口名称'), { target: { value: '仍需保存' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(api.update).toHaveBeenCalled());
    expect(screen.getByLabelText('接口名称')).toHaveValue('仍需保存');
    expect(context()).toMatchObject({ endpointId: 11, version: 3 });
  });

  it('discards pending editor options when management is revoked', async () => {
    let resolve!: (options: { upstreams: [] }) => void;
    api.options.mockReturnValue(new Promise<{ upstreams: [] }>((done) => { resolve = done; }));
    const page = render(view()); fireEvent.click(await screen.findByRole('button', { name: '登记接口' }));
    await screen.findByRole('dialog'); auth.permissions = ['route:api-center']; auth.version += 1;
    page.rerender(view()); await act(async () => resolve({ upstreams: [] }));
    expect(screen.queryByRole('dialog')).toBeNull(); expect(context()).not.toHaveProperty('endpointId');
    expect(screen.queryByRole('button', { name: '登记接口' })).toBeNull();
  });

  it('invalidates an execution confirmation when realtime execute permission is revoked', async () => {
    const page = render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: '执行联调' }))[0]);
    auth.permissions = ['route:api-center', 'integration:endpoint:manage']; auth.version += 1;
    page.rerender(view());
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(api.execute).not.toHaveBeenCalled();
  });

  it('destroys this page confirmation when the component unmounts', async () => {
    const page = render(view());
    fireEvent.click((await screen.findAllByRole('button', { name: '执行联调' }))[0]);
    await screen.findByRole('dialog'); page.rerender(view('/oa/api-center', false));
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(api.execute).not.toHaveBeenCalled();
  });

  it('does not restore the old detail after a late status response and a newer selection', async () => {
    const success = vi.spyOn(message, 'success');
    let resolve!: (endpoint: IntegrationEndpoint) => void;
    api.status.mockReturnValue(new Promise<IntegrationEndpoint>((done) => { resolve = done; }));
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11 }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '已启用' }));
    const confirmation = screen.getAllByRole('dialog').find((dialog) => within(dialog).queryAllByText('确认变更接口状态').length > 0)!;
    fireEvent.click(within(confirmation).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() => expect(api.status).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 12, version: 8 }));
    await act(async () => resolve({ ...endpoint, status: 'ACTIVE', version: 4 }));
    expect(context()).toMatchObject({ endpointId: 12, version: 8 });
    expect(success).not.toHaveBeenCalled();
  });

  it('does not leave an old failure prompt over the newly selected endpoint', async () => {
    const error = vi.spyOn(message, 'error');
    let reject!: (reason: Error) => void;
    api.status.mockReturnValue(new Promise<IntegrationEndpoint>((_, fail) => { reject = fail; }));
    render(view('/oa/api-center?id=11'));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 11 }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '已启用' }));
    const confirmation = screen.getAllByRole('dialog').find((dialog) => within(dialog).queryAllByText('确认变更接口状态').length > 0)!;
    fireEvent.click(within(confirmation).getByRole('button', { name: /确\s*认/ }));
    await waitFor(() => expect(api.status).toHaveBeenCalled());
    fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
    await waitFor(() => expect(context()).toMatchObject({ endpointId: 12, version: 8 }));
    await act(async () => reject(new Error('VERSION_CONFLICT')));
    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1));
    expect(error).not.toHaveBeenCalled(); expect(context()).toMatchObject({ endpointId: 12, version: 8 });
  });
});
