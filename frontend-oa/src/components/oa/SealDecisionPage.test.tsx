import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';

const permissions = ['route:seal-usage', 'seal:read:self', 'seal:create', 'approval:act'];
const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, permissions: [] as string[] } }));
const api = vi.hoisted(() => ({ listMySealUsages: vi.fn(), listPendingSealUsages: vi.fn(), getSealUsage: vi.fn(),
  approveSealUsage: vi.fn(), registerSealUse: vi.fn(), returnSeal: vi.fn(), listSealUsageDocuments: vi.fn() }));
const context = vi.hoisted(() => vi.fn());
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user, refresh: vi.fn() }) }));
vi.mock('@/lib/adminAssetsApi', () => ({ adminAssetsApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('./PageAgentContext', async () => ({
  ...await vi.importActual<typeof import('./PageAgentContext')>('./PageAgentContext'), usePublishPageAgentContext: context,
}));
import SealUsagePage from './SealUsagePage';

const assigned = { id: 9, documentTitle: 'Owned document', usageReason: 'Owned need', sealType: 'OFFICIAL', copies: 2,
  status: 'PENDING', version: 5, taskId: 20001, taskVersion: 2, canDecide: true, submittedAt: '2026-11-09T10:00:00' };
function view(path = '/oa/seal-usage') {
  return <MemoryRouter initialEntries={[path]}><ConfigProvider locale={zhCN}><App><SealUsagePage /></App></ConfigProvider></MemoryRouter>;
}
beforeEach(() => {
  vi.resetAllMocks(); auth.user = { id: 7, tenantId: 1, permissionVersion: 1, permissions };
  api.listMySealUsages.mockResolvedValue({ records: [], total: 0 });
  api.listPendingSealUsages.mockResolvedValue({ records: [assigned], total: 1 });
  api.approveSealUsage.mockResolvedValue({});
});
afterEach(cleanup);

it('publishes and submits the task version separately from the seal application version', async () => {
  render(view()); fireEvent.click(screen.getByRole('tab', { name: '待我审批' }));
  fireEvent.click(await screen.findByRole('button', { name: '审批' }));
  const dialog = await screen.findByRole('dialog');
  expect(context).toHaveBeenLastCalledWith(expect.objectContaining({ usageId: 9, taskId: 20001, taskVersion: 2 }));
  fireEvent.click(within(dialog).getByRole('button', { name: '通 过' }));
  await waitFor(() => expect(api.approveSealUsage).toHaveBeenCalledWith(20001, { version: 2, comment: undefined }));
});

it('does not substitute version zero when a task version is missing', async () => {
  api.listPendingSealUsages.mockResolvedValue({ records: [{ ...assigned, taskVersion: null }], total: 1 });
  render(view()); fireEvent.click(screen.getByRole('tab', { name: '待我审批' }));
  expect(await screen.findByRole('button', { name: '审批' })).toBeDisabled();
  expect(api.approveSealUsage).not.toHaveBeenCalled();
});

it('loads the receipt resource as a read-only detail without registering use or archive', async () => {
  api.getSealUsage.mockResolvedValue({ ...assigned, status: 'APPROVED', version: 6, canDecide: false });
  render(view('/oa/seal-usage?id=9'));
  await waitFor(() => expect(api.getSealUsage).toHaveBeenCalledWith(9));
  const dialog = await screen.findByRole('dialog');
  await waitFor(() => expect(within(dialog).getByText('用印申请详情')).toBeVisible());
  expect(await within(dialog).findByText('Owned document')).toBeInTheDocument();
  expect(within(dialog).queryByRole('button', { name: '审批' })).toBeNull();
  expect(api.registerSealUse).not.toHaveBeenCalled(); expect(api.returnSeal).not.toHaveBeenCalled();
  expect(api.listSealUsageDocuments).not.toHaveBeenCalled();
});

it('ignores a delayed private detail response after authority changes', async () => {
  let resolve!: (value: typeof assigned) => void;
  api.getSealUsage.mockReturnValueOnce(new Promise<typeof assigned>(done => { resolve = done; }));
  const page = render(view('/oa/seal-usage?id=9'));
  await waitFor(() => expect(api.getSealUsage).toHaveBeenCalledTimes(1));
  auth.user = { id: 8, tenantId: 2, permissionVersion: 2, permissions: [] }; page.rerender(view('/oa/seal-usage?id=9'));
  await act(async () => resolve(assigned));
  expect(screen.queryByText('Owned document')).toBeNull(); expect(screen.queryByRole('dialog')).toBeNull();
});
