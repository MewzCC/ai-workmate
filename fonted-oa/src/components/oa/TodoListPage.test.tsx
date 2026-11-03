import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { PageUiCommandHandlers, PageUiCommand } from '@/lib/pageUiCommands';
import { PageAgentContextProvider } from './PageAgentContext';
import { bindAuthResponseIdentity } from '@/lib/authEvents';

const auth = vi.hoisted(() => ({ user: { id: 7, tenantId: 1, permissionVersion: 1, roles: ['USER'], permissions: ['todo:read'], dataScopes: ['SELF'] } }));
const api = vi.hoisted(() => ({ list: vi.fn(), detail: vi.fn(), push: vi.fn(), error: vi.fn() }));
const ui = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: auth.user }) }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), todoApi: { list: api.list, detail: api.detail } }));
vi.mock('@/lib/nextCompat', () => ({ useRouter: () => ({ push: api.push }) }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));
vi.mock('./MyApplicationsPage', () => ({ leaveTypeLabel: (type: string) => type }));
vi.mock('./PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { ui.handlers = handlers; } }));
import TodoListPage from './TodoListPage';

const row = (name: string) => ({ id: 3, applicationId: 9, applicantUserId: 8, applicantName: name, leaveType: 'PERSONAL',
  durationHalfDays: 2, status: 'PENDING', version: 1, submittedAt: '2026-11-03T09:00:00', overdue: false });
const records = (name: string) => ({ records: [row(name)], total: 1, page: 1, size: 20 });
const tree = () => <PageAgentContextProvider pageId="todo"><TodoListPage /></PageAgentContextProvider>;
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { vi.clearAllMocks(); auth.user = { ...auth.user, id: 7, permissionVersion: 1, permissions: ['todo:read'] };
  bindAuthResponseIdentity('todo-user', 'read'); api.list.mockResolvedValue(records('Applicant A')); api.detail.mockResolvedValue({ id: 9 }); });
afterEach(cleanup);

it('applies numeric paging, status and date filters to the real query and visible controls', async () => {
  render(tree()); await screen.findByText(/Applicant A/);
  await act(async () => { await ui.handlers['ui.applyFilter']!({ pageId: 'todo', code: 'ui.applyFilter', payload: {
    filters: { status: 'APPROVED', page: 2, size: 50, from: '2026-11-01', to: '2026-11-03' },
  } }); });
  await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ status: 'APPROVED', page: 2, size: 50,
    from: '2026-11-01T00:00:00', to: '2026-11-03T00:00:00' }));
  expect(screen.getByDisplayValue('2026-11-01 00:00:00')).toBeTruthy();
  const invalid: Record<string, string | number>[] = [{ size: 51 }, { page: 0 }, { status: 'ADMIN' }, { from: '2026-02-30', to: '2026-03-01' }];
  for (const filters of invalid) await expect(async () => ui.handlers['ui.applyFilter']!({ pageId: 'todo', code: 'ui.applyFilter', payload: { filters } })).rejects.toThrow();
});

it('checks the protected detail endpoint before navigation and refuses denied resources', async () => {
  render(tree()); await screen.findByText(/Applicant A/);
  await act(async () => { await ui.handlers['ui.openDetail']!({ pageId: 'todo', code: 'ui.openDetail', payload: { recordId: 42 } }); });
  expect(api.detail).toHaveBeenCalledWith(42); expect(api.push).toHaveBeenCalledWith('/oa/approval-tasks/42?from=todo');
  api.push.mockClear(); api.detail.mockRejectedValueOnce(new Error('denied'));
  await expect(ui.handlers['ui.openDetail']!({ pageId: 'todo', code: 'ui.openDetail', payload: { recordId: 99 } })).rejects.toThrow('denied');
  expect(api.push).not.toHaveBeenCalled();
});

it('clears prior rows on revocation and suppresses late list and detail callbacks', async () => {
  const view = render(tree()); await screen.findByText(/Applicant A/);
  const lateList = deferred<ReturnType<typeof records>>(), lateDetail = deferred<{ id: number }>();
  api.list.mockReturnValueOnce(lateList.promise); api.detail.mockReturnValueOnce(lateDetail.promise);
  let list!: Promise<void> | void, detail!: Promise<void> | void;
  act(() => { list = ui.handlers['ui.refreshPage']!({ pageId: 'todo', code: 'ui.refreshPage', payload: {} });
    detail = ui.handlers['ui.openDetail']!({ pageId: 'todo', code: 'ui.openDetail', payload: { recordId: 42 } }); });
  const rejectedList = expect(list).rejects.toThrow(), rejectedDetail = expect(detail).rejects.toThrow();
  auth.user = { ...auth.user, permissionVersion: 2, permissions: [] }; view.rerender(tree());
  await waitFor(() => expect(screen.queryByText(/Applicant A/)).toBeNull());
  await act(async () => { lateList.resolve(records('Late applicant')); lateDetail.resolve({ id: 9 }); await rejectedList; await rejectedDetail; });
  expect(screen.queryByText(/Late applicant/)).toBeNull(); expect(api.push).not.toHaveBeenCalled();
});

it('shows a retryable failure instead of stale rows or an applied-success result', async () => {
  render(tree()); await screen.findByText(/Applicant A/);
  api.list.mockRejectedValueOnce(new Error('synthetic failure'));
  const command: PageUiCommand = { pageId: 'todo', code: 'ui.refreshPage', payload: {} };
  await act(async () => { await expect(ui.handlers['ui.refreshPage']!(command)).rejects.toThrow(); });
  expect(screen.queryByText(/Applicant A/)).toBeNull(); expect(screen.getByText('加载失败')).toBeTruthy();
  api.list.mockResolvedValueOnce(records('Recovered applicant')); fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
  await screen.findByText(/Recovered applicant/);
});
