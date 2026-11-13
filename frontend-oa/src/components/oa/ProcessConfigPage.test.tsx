import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { ApprovalProcess } from '@/lib/approvalEngineApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';
const api = vi.hoisted(() => ({ listProcesses: vi.fn(), getProcess: vi.fn(), listForms: vi.fn(), createProcess: vi.fn(), updateProcess: vi.fn(), deleteProcess: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:process-config', 'approval:read', 'approval:manage'], version: 1 }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import ProcessConfigPage from './ProcessConfigPage';
const process: ApprovalProcess = { id: 11, processKey: 'fixture', processName: '流程甲', description: 'private description', formId: 5,
  formName: '表单甲', nodeJson: '[{"nodeType":"APPROVAL","nodeName":"主管审核","approveType":"DIRECT_MANAGER","mode":"OR_SIGN"}]',
  status: 'DISABLED', version: 3, createdAt: '2026-10-23T09:00:00', updatedAt: '2026-10-23T09:00:00', canEdit: true, canDelete: true };
const other = { ...process, id: 12, processKey: 'other', processName: '流程乙', version: 8 };
function Probe() { const location = useLocation(); const navigate = useNavigate(); return <>
  <output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
  <output aria-label="location">{location.pathname}{location.search}</output>
  <button onClick={() => navigate('/oa/process-config?id=12')}>选择乙</button></>; }
function view(path = '/oa/process-config') { return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="process-config">
  <ProcessConfigPage /><Probe />
</PageAgentContextProvider></App></MemoryRouter>; }
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => { vi.clearAllMocks(); auth.permissions = ['route:process-config', 'approval:read', 'approval:manage']; auth.version = 1;
  api.listProcesses.mockResolvedValue({ records: [process, other], total: 2, page: 1, size: 20 });
  api.getProcess.mockImplementation(async (id: number) => id === 11 ? process : other);
  api.listForms.mockResolvedValue({ records: [], total: 0, page: 1, size: 100 });
  api.updateProcess.mockResolvedValue({ ...process, version: 4 });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
it('publishes only applied filters without selecting the first process or fetching editor options', async () => {
  render(view()); const search = await screen.findByPlaceholderText('按名称或标识搜索');
  fireEvent.change(search, { target: { value: 'fixture' } }); expect(context()).not.toHaveProperty('keyword');
  fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
  await waitFor(() => expect(context()).toEqual({ keyword: 'fixture', page: 1, size: 20 }));
  expect(api.listForms).not.toHaveBeenCalled();
});
it('locates protected process details and clears the actual ID and version on close', async () => {
  render(view('/oa/process-config?id=11'));
  await waitFor(() => expect(context()).toEqual({ processId: 11, version: 3, page: 1, size: 20 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  expect(context()).not.toHaveProperty('processId'); expect(screen.getByLabelText('location').textContent).toBe('/oa/process-config');
});
it('uses live maintenance permission rather than old canEdit flags', async () => {
  auth.permissions = ['route:process-config', 'approval:read']; render(view());
  await screen.findByText('流程甲'); expect(screen.queryByRole('button', { name: '新建流程' })).toBeNull();
  expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull(); expect(api.listForms).not.toHaveBeenCalled();
});
it('does not restore an older protected detail after a new selection', async () => {
  let resolve!: (result: ApprovalProcess) => void;
  api.getProcess.mockImplementation((id: number) => id === 11 ? new Promise<ApprovalProcess>((done) => { resolve = done; }) : Promise.resolve(other));
  render(view('/oa/process-config?id=11')); await waitFor(() => expect(api.getProcess).toHaveBeenCalledWith(11));
  fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
  await waitFor(() => expect(context()).toMatchObject({ processId: 12, version: 8 }));
  await act(async () => resolve(process)); expect(context()).toMatchObject({ processId: 12, version: 8 });
});
it.each(['0', '-1', '11tail', '9007199254740992'])('rejects an invalid process route ID: %s', async id => {
  render(view(`/oa/process-config?id=${id}`)); await screen.findByText('流程甲');
  expect(api.getProcess).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('processId');
});
it('does not publish a mismatched protected record', async () => {
  api.getProcess.mockResolvedValue(other); render(view('/oa/process-config?id=11'));
  await waitFor(() => expect(api.getProcess).toHaveBeenCalledWith(11));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(context()).not.toHaveProperty('processId');
});
it('does not read process data without both page and business read permissions', async () => {
  auth.permissions = ['route:process-config', 'approval:manage']; render(view('/oa/process-config?id=11'));
  await act(async () => undefined); expect(api.listProcesses).not.toHaveBeenCalled(); expect(api.getProcess).not.toHaveBeenCalled();
});
it('invalidates selected data and editor reads on permission refresh', async () => {
  const page = render(view('/oa/process-config?id=11'));
  await waitFor(() => expect(context()).toMatchObject({ processId: 11 }));
  auth.permissions = []; auth.version += 1; page.rerender(view('/oa/process-config?id=11'));
  await waitFor(() => expect(context()).not.toHaveProperty('processId'));
  expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByText('流程甲')).toBeNull();
});
it('opens the designer with a fresh protected version and preserves user input after failed save', async () => {
  render(view('/oa/process-config?id=11')); await waitFor(() => expect(context()).toMatchObject({ processId: 11 }));
  api.getProcess.mockResolvedValue({ ...process, version: 7 }); api.updateProcess.mockRejectedValue(new Error('VERSION_CONFLICT'));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  const input = await screen.findByLabelText('流程名称'); fireEvent.change(input, { target: { value: '保留草稿输入' } });
  fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
  await waitFor(() => expect(api.updateProcess).toHaveBeenCalledWith(11, expect.objectContaining({ processName: '保留草稿输入', version: 7, status: 'DISABLED' })));
  expect(screen.getByLabelText('流程名称')).toHaveValue('保留草稿输入'); expect(context()).toMatchObject({ processId: 11, version: 7 });
  expect(context()).not.toHaveProperty('nodeJson'); expect(context()).not.toHaveProperty('description');
});
it('reloads the actual saved version after a successful designer write', async () => {
  render(view('/oa/process-config?id=11')); await waitFor(() => expect(context()).toMatchObject({ version: 3 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  await screen.findByLabelText('流程名称'); api.getProcess.mockResolvedValue({ ...process, version: 4 });
  fireEvent.click(screen.getByRole('button', { name: '保存草稿' }));
  await waitFor(() => expect(api.updateProcess).toHaveBeenCalledWith(11, expect.objectContaining({ version: 3 })));
  await waitFor(() => expect(context()).toMatchObject({ processId: 11, version: 4 }));
});
it('does not restore an old process after its pending save completes', async () => {
  let resolve!: (result: ApprovalProcess) => void;
  api.updateProcess.mockReturnValue(new Promise<ApprovalProcess>(done => { resolve = done; }));
  render(view('/oa/process-config?id=11')); await waitFor(() => expect(context()).toMatchObject({ version: 3 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ })); await screen.findByLabelText('流程名称');
  fireEvent.click(screen.getByRole('button', { name: '保存草稿' })); await waitFor(() => expect(api.updateProcess).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
  await waitFor(() => expect(context()).toMatchObject({ processId: 12, version: 8 }));
  await act(async () => resolve({ ...process, version: 4 }));
  expect(context()).toMatchObject({ processId: 12, version: 8 }); expect(screen.getByLabelText('location').textContent).toBe('/oa/process-config?id=12');
});
it('does not reopen a designer after a late options response', async () => {
  let resolve!: (result: { records: [] }) => void;
  api.listForms.mockReturnValue(new Promise<{ records: [] }>(done => { resolve = done; }));
  render(view()); fireEvent.click(await screen.findByRole('button', { name: '新建流程' }));
  await waitFor(() => expect(api.listForms).toHaveBeenCalled());
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  await act(async () => resolve({ records: [] }));
  expect(screen.queryByRole('dialog')).toBeNull(); expect(context()).not.toHaveProperty('processId');
});
it('does not submit the same designer write twice while the first save is pending', async () => {
  let resolve!: (result: ApprovalProcess) => void;
  api.updateProcess.mockReturnValue(new Promise<ApprovalProcess>(done => { resolve = done; }));
  render(view('/oa/process-config?id=11')); await waitFor(() => expect(context()).toMatchObject({ processId: 11 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ })); await screen.findByLabelText('流程名称');
  const button = screen.getByRole('button', { name: '保存草稿' });
  await act(async () => { fireEvent.click(button); fireEvent.click(button); });
  await waitFor(() => expect(api.updateProcess).toHaveBeenCalled());
  await act(async () => resolve({ ...process, version: 4 }));
  expect(api.updateProcess).toHaveBeenCalledTimes(1);
});
it('keeps the verified bound form label when it is outside the first options page', async () => {
  render(view('/oa/process-config?id=11')); await waitFor(() => expect(context()).toMatchObject({ processId: 11 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  await screen.findByLabelText('流程名称');
  expect(within(screen.getByRole('dialog')).getByText('表单甲')).toBeTruthy();
});
