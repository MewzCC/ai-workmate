import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { ApprovalForm } from '@/lib/approvalEngineApi';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';
const api = vi.hoisted(() => ({ listForms: vi.fn(), getForm: vi.fn(), createForm: vi.fn(), updateForm: vi.fn(), deleteForm: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:form-engine', 'approval:read', 'approval:manage'], version: 1 }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import FormEnginePage from './FormEnginePage';
const form: ApprovalForm = { id: 11, formKey: 'fixture', formName: '表单甲', description: 'private description',
  schemaJson: '{"fields":[{"name":"reason","label":"事由","type":"text","required":true,"options":[],"width":"full"}]}',
  status: 'DISABLED', version: 3, createdAt: '2026-10-23T09:00:00', updatedAt: '2026-10-23T09:00:00', canEdit: true, canDelete: true };
const other = { ...form, id: 12, formKey: 'other', formName: '表单乙', version: 8 };
function Probe() { const location = useLocation(); const navigate = useNavigate(); return <>
  <output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
  <output aria-label="location">{location.pathname}{location.search}</output>
  <button onClick={() => navigate('/oa/form-engine?id=12')}>选择乙</button></>; }
function view(path = '/oa/form-engine') { return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="form-engine">
  <FormEnginePage /><Probe />
</PageAgentContextProvider></App></MemoryRouter>; }
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => { vi.clearAllMocks(); auth.permissions = ['route:form-engine', 'approval:read', 'approval:manage']; auth.version = 1;
  api.listForms.mockResolvedValue({ records: [form, other], total: 2, page: 1, size: 20 });
  api.getForm.mockImplementation(async (id: number) => id === 11 ? form : other);
  api.updateForm.mockResolvedValue({ ...form, version: 4 });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
it('writes name rather than a designer-only key so published forms keep their business field identity', async () => {
  render(view()); await screen.findByText('表单甲');
  fireEvent.click(screen.getAllByRole('button', { name: /编\s*辑/ })[0]);
  await screen.findByLabelText('表单名称'); fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateForm).toHaveBeenCalled());
  expect(JSON.parse(api.updateForm.mock.calls[0][1].schemaJson).fields[0]).toMatchObject({ name: 'reason' });
  expect(JSON.parse(api.updateForm.mock.calls[0][1].schemaJson).fields[0]).not.toHaveProperty('key');
});
it('publishes applied filters without guessing the first record or sending unsaved schema', async () => {
  render(view()); const search = await screen.findByPlaceholderText('按名称或标识搜索');
  fireEvent.change(search, { target: { value: 'fixture' } }); expect(context()).not.toHaveProperty('keyword');
  fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
  await waitFor(() => expect(context()).toEqual({ keyword: 'fixture', page: 1, size: 20 }));
});
it('reads the selected protected form and clears identity and version on close', async () => {
  render(view('/oa/form-engine?id=11'));
  await waitFor(() => expect(context()).toEqual({ formId: 11, version: 3, page: 1, size: 20 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  expect(context()).not.toHaveProperty('formId'); expect(screen.getByLabelText('location').textContent).toBe('/oa/form-engine');
});
it('hides maintenance despite stale row flags when the live permission is absent', async () => {
  auth.permissions = ['route:form-engine', 'approval:read']; render(view());
  await screen.findByText('表单甲'); expect(screen.queryByRole('button', { name: '新建表单' })).toBeNull();
  expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
});
it('uses fresh protected versions and serializes persisted field names after a failed save', async () => {
  render(view('/oa/form-engine?id=11')); await waitFor(() => expect(context()).toMatchObject({ formId: 11 }));
  api.getForm.mockResolvedValue({ ...form, version: 7 }); api.updateForm.mockRejectedValue(new Error('VERSION_CONFLICT'));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  const input = await screen.findByLabelText('表单名称'); fireEvent.change(input, { target: { value: '保留输入' } });
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateForm).toHaveBeenCalledWith(11, expect.objectContaining({ formName: '保留输入', version: 7 })));
  const schema = JSON.parse(api.updateForm.mock.calls[0][1].schemaJson);
  expect(schema.fields).toEqual([{ name: 'reason', label: '事由', type: 'text', required: true, placeholder: '', options: [], width: 'full' }]);
  expect(screen.getByLabelText('表单名称')).toHaveValue('保留输入');
  expect(context()).toEqual({ formId: 11, version: 7, page: 1, size: 20 });
});
it('preserves legacy designer field keys when converting them to canonical persisted names', async () => {
  api.getForm.mockResolvedValue({ ...form, schemaJson: '{"fields":[{"key":"legacyReason","label":"旧事由","type":"text","required":false}]}' });
  render(view()); await screen.findByText('表单甲');
  fireEvent.click(screen.getAllByRole('button', { name: /编\s*辑/ })[0]); await screen.findByLabelText('表单名称');
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateForm).toHaveBeenCalled());
  expect(JSON.parse(api.updateForm.mock.calls[0][1].schemaJson).fields[0]).toMatchObject({ name: 'legacyReason' });
});
it.each(['0', '-1', '11tail', '9007199254740992'])('does not read an invalid form ID: %s', async id => {
  render(view(`/oa/form-engine?id=${id}`)); await screen.findByText('表单甲');
  expect(api.getForm).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('formId');
});
it('does not publish a protected record with an unexpected identity', async () => {
  api.getForm.mockResolvedValue(other); render(view('/oa/form-engine?id=11'));
  await waitFor(() => expect(api.getForm).toHaveBeenCalledWith(11));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(context()).not.toHaveProperty('formId');
});
it('requires live page and business read permissions before loading any records', async () => {
  auth.permissions = ['route:form-engine', 'approval:manage']; render(view('/oa/form-engine?id=11'));
  await act(async () => undefined); expect(api.listForms).not.toHaveBeenCalled(); expect(api.getForm).not.toHaveBeenCalled();
});
it('clears selected data and an open editor when live permissions change', async () => {
  const page = render(view('/oa/form-engine?id=11')); await waitFor(() => expect(context()).toMatchObject({ formId: 11 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ })); await screen.findByLabelText('表单名称');
  auth.permissions = []; auth.version += 1; page.rerender(view('/oa/form-engine?id=11'));
  await waitFor(() => expect(context()).not.toHaveProperty('formId'));
  expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByText('表单甲')).toBeNull();
});
it('does not overwrite a newer form selection with an earlier detail response', async () => {
  let resolve!: (result: ApprovalForm) => void;
  api.getForm.mockImplementation((id: number) => id === 11 ? new Promise<ApprovalForm>(done => { resolve = done; }) : Promise.resolve(other));
  render(view('/oa/form-engine?id=11')); await waitFor(() => expect(api.getForm).toHaveBeenCalledWith(11));
  fireEvent.click(screen.getByRole('button', { name: '选择乙' }));
  await waitFor(() => expect(context()).toMatchObject({ formId: 12, version: 8 }));
  await act(async () => resolve(form)); expect(context()).toMatchObject({ formId: 12, version: 8 });
});
it('reloads the actual new form version after publishing', async () => {
  render(view('/oa/form-engine?id=11')); await waitFor(() => expect(context()).toMatchObject({ formId: 11 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ })); await screen.findByLabelText('表单名称');
  api.getForm.mockResolvedValue({ ...form, version: 4, status: 'ENABLED' });
  fireEvent.click(screen.getByRole('button', { name: /发\s*布/ }));
  await waitFor(() => expect(api.updateForm).toHaveBeenCalledWith(11, expect.objectContaining({ version: 3, status: 'ENABLED' })));
  await waitFor(() => expect(context()).toMatchObject({ formId: 11, version: 4 }));
});
it('does not restore an old form when its pending write finishes after navigation', async () => {
  let resolve!: (result: ApprovalForm) => void;
  api.updateForm.mockReturnValue(new Promise<ApprovalForm>(done => { resolve = done; }));
  render(view('/oa/form-engine?id=11')); await waitFor(() => expect(context()).toMatchObject({ formId: 11 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ })); await screen.findByLabelText('表单名称');
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ })); await waitFor(() => expect(api.updateForm).toHaveBeenCalled());
  fireEvent.click(screen.getByRole('button', { name: '选择乙' })); await waitFor(() => expect(context()).toMatchObject({ formId: 12 }));
  await act(async () => resolve({ ...form, version: 4 })); expect(context()).toMatchObject({ formId: 12, version: 8 });
});
it('does not reopen a closed editor after its protected read completes', async () => {
  let resolve!: (result: ApprovalForm) => void;
  api.getForm.mockReturnValue(new Promise<ApprovalForm>(done => { resolve = done; }));
  render(view()); await screen.findByText('表单甲'); fireEvent.click(screen.getAllByRole('button', { name: /编\s*辑/ })[0]);
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  await act(async () => resolve(form)); expect(screen.queryByRole('dialog')).toBeNull(); expect(context()).not.toHaveProperty('formId');
});
it('keeps a new designer independent of the previously selected form', async () => {
  render(view('/oa/form-engine?id=11')); await waitFor(() => expect(context()).toMatchObject({ formId: 11 }));
  fireEvent.click(screen.getByRole('button', { name: '新建表单' })); await screen.findByLabelText('表单名称');
  expect(context()).not.toHaveProperty('formId'); expect(screen.getByLabelText('表单名称')).toHaveValue('');
});
it('creates a real form definition and locates only the returned protected record', async () => {
  api.createForm.mockResolvedValue({ ...other, version: 0 });
  render(view()); fireEvent.click(await screen.findByRole('button', { name: '新建表单' }));
  fireEvent.change(await screen.findByLabelText('表单标识'), { target: { value: 'created_form' } });
  fireEvent.change(screen.getByLabelText('表单名称'), { target: { value: '新表单' } });
  fireEvent.click(screen.getByRole('button', { name: '单行文本' }));
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.createForm).toHaveBeenCalledWith(expect.objectContaining({ formKey: 'created_form', formName: '新表单', status: 'DISABLED' })));
  expect(JSON.parse(api.createForm.mock.calls[0][0].schemaJson).fields[0]).toMatchObject({ name: 'text1', type: 'text' });
  await waitFor(() => expect(context()).toMatchObject({ formId: 12, version: 8 }));
  expect(screen.getByLabelText('location').textContent).toBe('/oa/form-engine?id=12');
});
