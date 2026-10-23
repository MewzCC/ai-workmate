import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { DictionaryItem, DictionaryType, DictionaryItemDetail } from '@/lib/dictionaryApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';

const api = vi.hoisted(() => ({ listTypes: vi.fn(), listItems: vi.fn(), typeDetail: vi.fn(), itemDetail: vi.fn(),
  createType: vi.fn(), updateType: vi.fn(), createItem: vi.fn(), updateItem: vi.fn(), setTypeStatus: vi.fn(),
  setItemStatus: vi.fn(), deleteType: vi.fn(), deleteItem: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:dictionary', 'dictionary:manage'], version: 1 }));
vi.mock('@/lib/dictionaryApi', () => ({ dictionaryApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import DictionaryPage from './DictionaryPage';

const type: DictionaryType = { id: 11, code: 'FIXTURE', name: '测试类型甲', description: 'private type description',
  status: 'ACTIVE', sortOrder: 0, itemCount: 1, activeItemCount: 1, version: 3, updatedAt: '2026-10-23T09:00:00', canManage: true };
const other: DictionaryType = { ...type, id: 12, code: 'OTHER', name: '测试类型乙', version: 8 };
const item: DictionaryItem = { id: 21, dictionaryTypeId: 11, value: 'FIRST', label: '测试字典项', description: 'private item description',
  status: 'ACTIVE', sortOrder: 0, usageCount: 0, version: 4, updatedAt: '2026-10-23T09:00:00', canManage: true, canDelete: true };
function Probe() {
  const location = useLocation(); const navigate = useNavigate();
  return <><output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
    <output aria-label="location">{location.pathname}{location.search}</output>
    <button onClick={() => navigate('/oa/dictionary?typeId=12')}>选择乙</button></>;
}
function view(path = '/oa/dictionary') {
  return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="dictionary">
    <DictionaryPage /><Probe />
  </PageAgentContextProvider></App></MemoryRouter>;
}
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => {
  vi.clearAllMocks(); auth.permissions = ['route:dictionary', 'dictionary:manage']; auth.version = 1;
  api.listTypes.mockResolvedValue({ records: [type, other], canManage: true });
  api.listItems.mockImplementation(async (id: number) => ({ records: id === 11 ? [item] : [], total: id === 11 ? 1 : 0, page: 1, size: 20, canManage: true }));
  api.typeDetail.mockImplementation(async (id: number) => id === 11 ? type : other);
  api.itemDetail.mockResolvedValue({ dictionaryType: type, item });
  api.updateType.mockResolvedValue({ ...type, version: 4 });
  api.updateItem.mockResolvedValue({ ...item, version: 5 });
  api.setItemStatus.mockResolvedValue({ ...item, status: 'DISABLED', version: 5 });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('does not publish a guessed first type or fetch its items before an explicit selection', async () => {
  render(view()); await screen.findByText('测试类型甲');
  expect(context()).toEqual({ page: 1, size: 20 }); expect(api.listItems).not.toHaveBeenCalled();
});
it('locates an item outside the current type list through its protected parent and independent version', async () => {
  api.listTypes.mockResolvedValue({ records: [other], canManage: true });
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toEqual({ dictionaryTypeId: 11, typeCode: 'FIXTURE', typeVersion: 3,
    dictionaryItemId: 21, itemValue: 'FIRST', version: 4, page: 1, size: 20 }));
  expect(within(screen.getByRole('dialog')).getByText('测试字典项')).toBeTruthy();
  expect(api.listItems).toHaveBeenCalledWith(11, expect.anything());
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(context()).not.toHaveProperty('dictionaryItemId'));
  expect(screen.getByLabelText('location').textContent).toBe('/oa/dictionary');
});
it.each(['typeId=0', 'itemId=21tail', 'itemId=9007199254740992', 'typeId=11&itemId=21'])('rejects malformed or ambiguous selection: %s', async (query) => {
  render(view(`/oa/dictionary?${query}`)); await screen.findByText('测试类型甲');
  expect(api.typeDetail).not.toHaveBeenCalled(); expect(api.itemDetail).not.toHaveBeenCalled();
  expect(context()).not.toHaveProperty('dictionaryTypeId');
});
it('rejects mismatched child or parent identity from detail responses', async () => {
  api.itemDetail.mockResolvedValue({ dictionaryType: other, item });
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(api.itemDetail).toHaveBeenCalledWith(21));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(context()).not.toHaveProperty('dictionaryItemId'); expect(api.listItems).not.toHaveBeenCalled();
});
it('ignores an item response that finishes after selecting a newer type', async () => {
  let resolve!: (result: DictionaryItemDetail) => void;
  api.itemDetail.mockReturnValue(new Promise<DictionaryItemDetail>((done) => { resolve = done; }));
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(api.itemDetail).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 12, version: 8 }));
  await act(async () => resolve({ dictionaryType: type, item }));
  expect(context()).toMatchObject({ dictionaryTypeId: 12, version: 8 }); expect(context()).not.toHaveProperty('dictionaryItemId');
});
it('requires realtime management in addition to stale server flags', async () => {
  auth.permissions = ['route:dictionary'];
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21 }));
  expect(screen.queryByRole('button', { name: '新建类型' })).toBeNull();
  expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
  expect(screen.queryByRole('switch')).toBeNull();
});
it('clears data, editor and context after live permission revocation', async () => {
  const page = render(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 11 }));
  auth.permissions = []; auth.version += 1; page.rerender(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).not.toHaveProperty('dictionaryTypeId'));
  expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByText('测试类型甲')).toBeNull();
});
it('uses the protected edit version instead of a stale row and preserves input on failed save', async () => {
  api.typeDetail.mockResolvedValue({ ...type, version: 7 }); api.updateType.mockRejectedValue(new Error('VERSION_CONFLICT'));
  render(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).toMatchObject({ version: 7 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  const name = await screen.findByLabelText('字典名称'); fireEvent.change(name, { target: { value: '保留输入' } });
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateType).toHaveBeenCalledWith(11, expect.objectContaining({ name: '保留输入', version: 7 })));
  expect(screen.getByLabelText('字典名称')).toHaveValue('保留输入'); expect(context()).toMatchObject({ version: 7 });
});
it('keeps the selected item and refreshes its actual new version after a successful edit', async () => {
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21, version: 4 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  await screen.findByLabelText('显示名称');
  const save = within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ });
  await waitFor(() => {
    expect(screen.getByLabelText('显示名称')).toHaveValue(item.label);
    expect(save).toBeEnabled();
    expect(context()).toMatchObject({ dictionaryItemId: 21, version: 4 });
  });
  api.itemDetail.mockResolvedValue({ dictionaryType: type, item: { ...item, version: 5 } });
  fireEvent.click(save);
  await waitFor(() => expect(api.updateItem).toHaveBeenCalledWith(11, 21, expect.objectContaining({ version: 4 })));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21, version: 5 }));
});
it('fails closed when the item page disallows maintenance despite old type and row flags', async () => {
  api.listItems.mockResolvedValue({ records: [item], total: 1, page: 1, size: 20, canManage: false });
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(api.listItems).toHaveBeenCalled()); await screen.findByRole('button', { name: 'FIRST' });
  expect(screen.queryByRole('button', { name: '新建字典项' })).toBeNull();
  expect(within(screen.getByRole('dialog')).queryByRole('button', { name: /编\s*辑/ })).toBeNull();
});
it('does not fetch dictionaries or detail without the current page permission', async () => {
  auth.permissions = []; render(view('/oa/dictionary?itemId=21')); await act(async () => undefined);
  expect(api.listTypes).not.toHaveBeenCalled(); expect(api.itemDetail).not.toHaveBeenCalled(); expect(api.listItems).not.toHaveBeenCalled();
});
it('does not restore a closed pending detail', async () => {
  let resolve!: (result: DictionaryItemDetail) => void;
  api.itemDetail.mockReturnValue(new Promise<DictionaryItemDetail>((done) => { resolve = done; }));
  render(view('/oa/dictionary?itemId=21'));
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Close' }));
  await act(async () => resolve({ dictionaryType: type, item }));
  expect(screen.queryByRole('dialog')).toBeNull(); expect(context()).not.toHaveProperty('dictionaryItemId');
});
it('does not navigate back to an old item after a pending save finishes', async () => {
  let resolve!: (result: DictionaryItem) => void;
  api.updateItem.mockReturnValue(new Promise<DictionaryItem>((done) => { resolve = done; }));
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  await screen.findByLabelText('显示名称');
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateItem).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 12, version: 8 }));
  await act(async () => resolve({ ...item, version: 5 }));
  expect(context()).toMatchObject({ dictionaryTypeId: 12, version: 8 });
  expect(screen.getByLabelText('location').textContent).toBe('/oa/dictionary?typeId=12');
});
it('invalidates a pending editor read when closed before detail returns', async () => {
  render(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 11 }));
  let resolve!: (result: DictionaryType) => void;
  api.typeDetail.mockReturnValue(new Promise<DictionaryType>((done) => { resolve = done; }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  await act(async () => resolve(type));
  expect(context()).not.toHaveProperty('dictionaryTypeId'); expect(screen.queryByRole('dialog')).toBeNull();
});
it('refreshes the selected actual version after a confirmed status change', async () => {
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21, version: 4 }));
  const row = (await screen.findByRole('button', { name: 'FIRST' })).closest('tr')!;
  fireEvent.click(within(row).getByRole('switch'));
  const confirmButton = await screen.findByRole('button', { name: /确\s*认/ });
  api.itemDetail.mockResolvedValue({ dictionaryType: type, item: { ...item, status: 'DISABLED', version: 5 } });
  fireEvent.click(confirmButton);
  await waitFor(() => expect(api.setItemStatus).toHaveBeenCalledWith(11, 21, 'DISABLED', 4));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21, version: 5 }));
});
it('destroys an old status confirmation when the selected detail closes', async () => {
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21 }));
  const row = (await screen.findByRole('button', { name: 'FIRST' })).closest('tr')!;
  fireEvent.click(within(row).getByRole('switch'));
  await screen.findByRole('button', { name: /确\s*认/ });
  fireEvent.click(screen.getByRole('button', { name: 'Close' }));
  await waitFor(() => expect(screen.queryByRole('button', { name: /确\s*认/ })).toBeNull());
  expect(api.setItemStatus).not.toHaveBeenCalled();
});
it('clears the active parent after deleting it even if its detail drawer was already closed', async () => {
  const empty = { ...type, itemCount: 0, activeItemCount: 0 };
  api.typeDetail.mockResolvedValue(empty); api.listTypes.mockResolvedValue({ records: [empty, other], canManage: true });
  api.listItems.mockResolvedValue({ records: [], total: 0, page: 1, size: 20, canManage: true }); api.deleteType.mockResolvedValue(undefined);
  render(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 11 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  fireEvent.click(await screen.findByRole('button', { name: '删除 测试类型甲' }));
  const confirmButton = await screen.findByRole('button', { name: /确\s*认/ });
  api.listTypes.mockResolvedValue({ records: [other], canManage: true });
  fireEvent.click(confirmButton);
  await waitFor(() => expect(api.deleteType).toHaveBeenCalledWith(11, 3));
  await waitFor(() => expect(screen.queryByRole('button', { name: '新建字典项' })).toBeNull());
});
it('publishes the verified parent but not stale item identity in a new item form', async () => {
  render(view('/oa/dictionary?itemId=21'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryItemId: 21 }));
  fireEvent.click(await screen.findByRole('button', { name: '新建字典项' }));
  await screen.findByLabelText('显示名称');
  expect(context()).toMatchObject({ dictionaryTypeId: 11, typeCode: 'FIXTURE', typeVersion: 3 });
  expect(context()).not.toHaveProperty('dictionaryItemId'); expect(context()).not.toHaveProperty('itemValue');
});
it('publishes applied filters only and never unsent form or search content', async () => {
  render(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 11 }));
  const search = screen.getByPlaceholderText('搜索项值或名称');
  fireEvent.change(search, { target: { value: 'FIRST' } }); expect(context()).not.toHaveProperty('keyword');
  fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
  await waitFor(() => expect(context()).toMatchObject({ keyword: 'FIRST', page: 1, size: 20 }));
  expect(context()).not.toHaveProperty('description'); expect(context()).not.toHaveProperty('label');
});
it('does not restore late list data after the authenticated scope loses access', async () => {
  let resolve!: (result: { records: DictionaryType[]; canManage: boolean }) => void;
  api.listTypes.mockReturnValue(new Promise<{ records: DictionaryType[]; canManage: boolean }>((done) => { resolve = done; }));
  const page = render(view()); await waitFor(() => expect(api.listTypes).toHaveBeenCalled());
  auth.permissions = []; auth.version += 1; page.rerender(view());
  await act(async () => resolve({ records: [type], canManage: true }));
  expect(screen.queryByText('测试类型甲')).toBeNull(); expect(screen.queryByRole('button', { name: '新建类型' })).toBeNull();
});
it('starts a new type without the previous identity and submits no previous version', async () => {
  api.createType.mockResolvedValue({ ...other, id: 31, code: 'CREATED', name: '新类型', version: 0 });
  api.typeDetail.mockImplementation(async (id: number) => id === 31 ? { ...other, id: 31, code: 'CREATED', name: '新类型', version: 0 } : type);
  render(view('/oa/dictionary?typeId=11'));
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 11 }));
  fireEvent.click(screen.getByRole('button', { name: '新建类型' }));
  await screen.findByLabelText('字典名称'); expect(context()).not.toHaveProperty('dictionaryTypeId');
  fireEvent.change(screen.getByLabelText('字典编码'), { target: { value: 'CREATED' } });
  fireEvent.change(screen.getByLabelText('字典名称'), { target: { value: '新类型' } });
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.createType).toHaveBeenCalledWith(expect.objectContaining({ code: 'CREATED', name: '新类型' })));
  expect(api.createType.mock.calls[0][0]).not.toHaveProperty('version');
  await waitFor(() => expect(context()).toMatchObject({ dictionaryTypeId: 31, version: 0 }));
});
