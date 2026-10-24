import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App } from 'antd';
import { MemoryRouter, useLocation, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { ApprovalRule } from '@/lib/approvalEngineApi';
import { message } from '@/lib/antdMessage';
import { PageAgentContextProvider, usePageAgentContextSnapshot } from './PageAgentContext';
const api = vi.hoisted(() => ({ listRules: vi.fn(), getRule: vi.fn(), createRule: vi.fn(), updateRule: vi.fn(), deleteRule: vi.fn() }));
const auth = vi.hoisted(() => ({ permissions: ['route:approval-rules', 'approval:read', 'approval:manage'], version: 1 }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissions: auth.permissions, permissionVersion: auth.version,
}, refresh: vi.fn() }) }));
import ApprovalRulesPage from './ApprovalRulesPage';
const rule: ApprovalRule = { id: 11, ruleKey: 'fixture', ruleName: '规则甲', ruleType: 'AMOUNT_THRESHOLD', priority: 100,
  conditionJson: '{"field":"amount","op":"gte","value":100}', actionJson: '{"appendNode":"FINANCE_REVIEW","enabled":true,"mode":"OR_SIGN"}',
  description: 'private rule details', status: 'DISABLED', version: 3, createdAt: '2026-10-24T09:00:00', updatedAt: '2026-10-24T09:00:00', canEdit: true, canDelete: true };
const other = { ...rule, id: 12, ruleKey: 'other', ruleName: '规则乙', version: 8 };
function Probe() { const location = useLocation(); const navigate = useNavigate(); return <>
  <output aria-label="context">{JSON.stringify(usePageAgentContextSnapshot())}</output>
  <output aria-label="location">{location.pathname}{location.search}</output>
  <button onClick={() => navigate('/oa/approval-rules?id=12')}>选择乙</button></>; }
function view(path = '/oa/approval-rules') { return <MemoryRouter initialEntries={[path]}><App><PageAgentContextProvider pageId="approval-rules">
  <ApprovalRulesPage /><Probe />
</PageAgentContextProvider></App></MemoryRouter>; }
function context() { return JSON.parse(screen.getByLabelText('context').textContent || '{}'); }
beforeEach(() => { vi.clearAllMocks(); auth.permissions = ['route:approval-rules', 'approval:read', 'approval:manage']; auth.version = 1;
  api.listRules.mockResolvedValue({ records: [rule, other], total: 2, page: 1, size: 20 });
  api.getRule.mockImplementation(async (id: number) => id === 11 ? rule : other);
  api.updateRule.mockResolvedValue({ ...rule, version: 4 });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); });
it('shows exact saved conditions and actions to a read-only user without publishing them as context', async () => {
  auth.permissions = ['route:approval-rules', 'approval:read']; render(view('/oa/approval-rules?id=11'));
  const condition = await screen.findByLabelText('条件 JSON');
  expect(condition).toHaveValue(rule.conditionJson); expect(condition).toHaveAttribute('readonly');
  const action = screen.getByLabelText('动作 JSON'); expect(action).toHaveValue(rule.actionJson); expect(action).toHaveAttribute('readonly');
  expect(context()).toEqual({ ruleId: 11, version: 3, page: 1, size: 20 });
  expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
});
it('reports creation rather than update after creating an enabled rule', async () => {
  const success = vi.spyOn(message, 'success'); api.createRule.mockResolvedValue(other);
  render(view()); fireEvent.click(await screen.findByRole('button', { name: '新建规则' }));
  fireEvent.change(await screen.findByLabelText('规则标识'), { target: { value: 'created_rule' } });
  fireEvent.change(screen.getByLabelText('规则名称'), { target: { value: '新规则' } });
  fireEvent.mouseDown(screen.getByLabelText('规则类型'));
  fireEvent.click(await screen.findByText('金额阈值', { selector: '.ant-select-item-option-content' }));
  fireEvent.change(screen.getByPlaceholderText('条件值'), { target: { value: '100' } });
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.createRule).toHaveBeenCalledWith(expect.objectContaining({ ruleKey: 'created_rule', ruleName: '新规则', status: 'ENABLED' })));
  await waitFor(() => expect(context()).toMatchObject({ ruleId: 12, version: 8 }));
  expect(success).toHaveBeenCalledWith('创建成功'); expect(success).not.toHaveBeenCalledWith('更新成功');
});
it('publishes applied filters without choosing a first rule or exposing rule content', async () => {
  render(view()); const search = await screen.findByPlaceholderText('按名称或标识搜索');
  fireEvent.change(search, { target: { value: 'fixture' } }); expect(context()).not.toHaveProperty('keyword');
  fireEvent.keyDown(search, { key: 'Enter', code: 'Enter' });
  await waitFor(() => expect(context()).toEqual({ keyword: 'fixture', page: 1, size: 20 }));
  expect(api.getRule).not.toHaveBeenCalled();
});
it('locates protected rule details and clears ID and version when closed', async () => {
  render(view('/oa/approval-rules?id=11'));
  await waitFor(() => expect(context()).toEqual({ ruleId: 11, version: 3, page: 1, size: 20 }));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  expect(context()).not.toHaveProperty('ruleId'); expect(screen.getByLabelText('location').textContent).toBe('/oa/approval-rules');
});
it('uses current maintenance permission instead of stale row edit flags', async () => {
  auth.permissions = ['route:approval-rules', 'approval:read']; render(view());
  await screen.findByText('规则甲'); expect(screen.queryByRole('button', { name: '新建规则' })).toBeNull();
  expect(screen.queryByRole('button', { name: /编\s*辑/ })).toBeNull();
});
it('does not fetch business records without read permission', async () => {
  auth.permissions = ['route:approval-rules', 'approval:manage']; render(view('/oa/approval-rules?id=11'));
  await act(async () => undefined); expect(api.listRules).not.toHaveBeenCalled(); expect(api.getRule).not.toHaveBeenCalled();
});
it('does not restore a late protected detail after another selection', async () => {
  let resolve!: (result: ApprovalRule) => void;
  api.getRule.mockImplementation((id: number) => id === 11 ? new Promise<ApprovalRule>(done => { resolve = done; }) : Promise.resolve(other));
  render(view('/oa/approval-rules?id=11')); await waitFor(() => expect(api.getRule).toHaveBeenCalledWith(11));
  fireEvent.click(screen.getByRole('button', { name: '选择乙' })); await waitFor(() => expect(context()).toMatchObject({ ruleId: 12, version: 8 }));
  await act(async () => resolve(rule)); expect(context()).toMatchObject({ ruleId: 12, version: 8 });
});
it('invalidates selected details on permission refresh', async () => {
  const page = render(view('/oa/approval-rules?id=11')); await waitFor(() => expect(context()).toMatchObject({ ruleId: 11 }));
  auth.permissions = []; auth.version += 1; page.rerender(view('/oa/approval-rules?id=11'));
  await waitFor(() => expect(context()).not.toHaveProperty('ruleId')); expect(screen.queryByRole('dialog')).toBeNull();
  expect(screen.queryByText('规则甲')).toBeNull();
});
it('fetches a fresh edit version and keeps user input after a failed save', async () => {
  render(view('/oa/approval-rules?id=11')); await waitFor(() => expect(context()).toMatchObject({ ruleId: 11 }));
  api.getRule.mockResolvedValue({ ...rule, version: 7 }); api.updateRule.mockRejectedValue(new Error('VERSION_CONFLICT'));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  const input = await screen.findByLabelText('规则名称');
  await waitFor(() => expect(input).toHaveValue('规则甲'));
  await waitFor(() => expect(context()).toMatchObject({ ruleId: 11, version: 7 }));
  fireEvent.change(input, { target: { value: '保留规则输入' } });
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateRule).toHaveBeenCalledWith(11, expect.objectContaining({ ruleName: '保留规则输入', version: 7 })));
  expect(input).toHaveValue('保留规则输入'); expect(context()).toMatchObject({ ruleId: 11, version: 7 });
});
it.each(['0', '-1', '11tail', '9007199254740992'])('rejects invalid rule route identity: %s', async id => {
  render(view(`/oa/approval-rules?id=${id}`)); await screen.findByText('规则甲');
  expect(api.getRule).not.toHaveBeenCalled(); expect(context()).not.toHaveProperty('ruleId');
});
it('rejects a mismatched protected rule response', async () => {
  api.getRule.mockResolvedValue(other); render(view('/oa/approval-rules?id=11'));
  await waitFor(() => expect(api.getRule).toHaveBeenCalledWith(11));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(context()).not.toHaveProperty('ruleId');
});
it('does not fall back to a list record after protected detail failure', async () => {
  api.getRule.mockRejectedValue(new Error('RESOURCE_NOT_FOUND')); render(view('/oa/approval-rules?id=11'));
  await waitFor(() => expect(api.getRule).toHaveBeenCalledWith(11));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull()); expect(context()).not.toHaveProperty('ruleId');
});
it('does not reopen details after closing a pending request', async () => {
  let resolve!: (result: ApprovalRule) => void;
  api.getRule.mockImplementation(() => new Promise<ApprovalRule>(done => { resolve = done; }));
  render(view('/oa/approval-rules?id=11')); await waitFor(() => expect(api.getRule).toHaveBeenCalledWith(11));
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Close' }));
  await act(async () => resolve(rule)); expect(screen.queryByRole('dialog')).toBeNull(); expect(context()).not.toHaveProperty('ruleId');
});
it('reads the actual saved version before republishing rule context', async () => {
  render(view('/oa/approval-rules?id=11')); await waitFor(() => expect(context()).toMatchObject({ ruleId: 11, version: 3 }));
  api.getRule.mockResolvedValue({ ...rule, version: 7 });
  fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /编\s*辑/ }));
  const input = await screen.findByLabelText('规则名称'); await waitFor(() => expect(input).toHaveValue('规则甲'));
  await waitFor(() => expect(context()).toMatchObject({ ruleId: 11, version: 7 }));
  fireEvent.change(input, { target: { value: '修改后的规则' } });
  const saved = { ...rule, ruleName: '修改后的规则', version: 8 }; api.updateRule.mockResolvedValue(saved); api.getRule.mockResolvedValue(saved);
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(api.updateRule).toHaveBeenCalledWith(11, expect.objectContaining({ version: 7 })));
  await waitFor(() => expect(context()).toEqual({ ruleId: 11, version: 8, page: 1, size: 20 }));
  expect(within(screen.getByRole('dialog')).getByText('修改后的规则')).toBeInTheDocument();
});
