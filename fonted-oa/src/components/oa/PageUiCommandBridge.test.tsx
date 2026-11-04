import { useState } from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgentTaskDetail } from '@/types/oa';
import { PageAgentContextProvider } from './PageAgentContext';
import { PageUiCommandBridge, usePageUiCommandBridge, useRegisterPageUiCommands, useRegisterPageUiViews } from './PageUiCommandBridge';
import { bindAuthResponseIdentity } from '@/lib/authEvents';

const api = vi.hoisted(() => ({ navigation: vi.fn(), capability: vi.fn(), detail: vi.fn() }));
vi.mock('@/lib/navigationApi', async load => ({ ...await load<typeof import('@/lib/navigationApi')>(), getNavigation: api.navigation }));
vi.mock('@/lib/oaApi', async load => ({ ...await load<typeof import('@/lib/oaApi')>(), getPageCapabilities: api.capability }));

function task(results: unknown[], pageContext: unknown = {}): AgentTaskDetail {
  return { taskId: 'task-ui-1', pageId: 'ai-workspace', input: 'Open my application', pageContext, plan: {}, planHash: null,
    planVersion: 1, riskLevel: 'L0', status: 'SUCCEEDED', timeoutAt: null, createdAt: '', updatedAt: '', finishedAt: '', errorCode: null,
    steps: results.map((result, index) => ({ sequence: index + 1, toolCode: 'page.ui.prepare', riskLevel: 'L0', status: 'SUCCEEDED',
      arguments: {}, result, resultSummary: null, errorCode: null, startedAt: null, finishedAt: null })) };
}
const selectGeneric = { pageId: 'my-applications', code: 'ui.applyFilter', fields: [{ name: 'view', value: 'generic' }] };
const openDetail = { pageId: 'my-applications', code: 'ui.openDetail', recordId: 7, fields: [] };
const capabilityFor = (pageId: string, uiCommands: string[]) => ({ pageId, uiCommands, tools: [{ code: 'page.ui.prepare' }] });

function Page({ request }: { request: AgentTaskDetail }) {
  const [view, setView] = useState('leave');
  const [result, setResult] = useState('idle');
  const bridge = usePageUiCommandBridge();
  useRegisterPageUiViews(view, ['leave', 'generic'], setView);
  useRegisterPageUiCommands({ 'ui.openDetail': () => { api.detail(view); } }, null, view);
  return <><output aria-label="view">{view}</output><output aria-label="result">{result}</output>
    <button onClick={() => { void bridge!.applyTask(request).then(() => setResult('done')).catch(() => setResult('rejected')); }}>apply</button>
    <button onClick={() => setView('leave')}>manual-leave</button></>;
}
function renderPage(request: AgentTaskDetail) {
  return render(<PageUiCommandBridge pageId="my-applications" navigate={() => false} refresh={() => {}}>
    <PageAgentContextProvider pageId="my-applications"><Page request={request} /></PageAgentContextProvider>
  </PageUiCommandBridge>);
}

describe('controlled page view dispatch', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    bindAuthResponseIdentity('bridge-user', 'original-permissions');
    api.navigation.mockResolvedValue([{ routeKey: 'my-applications', name: 'Applications', routeType: 'PAGE',
      componentKey: 'MY_APPLICATIONS', path: '/oa/my-applications', sortOrder: 1, children: [] }]);
    api.capability.mockImplementation((pageId: string) => Promise.resolve(capabilityFor(pageId, ['ui.applyFilter', 'ui.openDetail', 'ui.fillForm'])));
  });
  afterEach(cleanup);

  it('selects an explicit authorized view before using the same-task detail identifier', async () => {
    renderPage(task([selectGeneric, openDetail])); fireEvent.click(screen.getByText('apply'));
    await waitFor(() => expect(screen.getByLabelText('result').textContent).toBe('done'));
    expect(api.detail).toHaveBeenCalledTimes(1);
    expect(api.detail).toHaveBeenCalledWith('generic');
    expect(api.navigation).toHaveBeenCalledTimes(2);
  });
  it('rejects an ambiguous Workspace detail without selecting a view', async () => {
    renderPage(task([openDetail])); fireEvent.click(screen.getByText('apply'));
    await waitFor(() => expect(screen.getByLabelText('result').textContent).toBe('rejected'));
    expect(api.detail).not.toHaveBeenCalled();
  });
  it('checks fresh capabilities before changing a local view', async () => {
    api.capability.mockResolvedValue(capabilityFor('my-applications', ['ui.openDetail']));
    renderPage(task([selectGeneric])); fireEvent.click(screen.getByText('apply'));
    await waitFor(() => expect(screen.getByLabelText('result').textContent).toBe('rejected'));
    expect(screen.getByLabelText('view').textContent).toBe('leave');
    expect(api.detail).not.toHaveBeenCalled();
  });
  it('does not replay already applied actions after a human changes the view', async () => {
    renderPage(task([selectGeneric, openDetail])); fireEvent.click(screen.getByText('apply'));
    await waitFor(() => expect(api.detail).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByText('manual-leave')); fireEvent.click(screen.getByText('apply'));
    await waitFor(() => expect(screen.getByLabelText('view').textContent).toBe('leave'));
    expect(api.detail).toHaveBeenCalledTimes(1);
  });
  it('rejects unavailable views before invoking any registered page operation', async () => {
    renderPage(task([{ ...selectGeneric, fields: [{ name: 'view', value: 'admin' }] }]));
    fireEvent.click(screen.getByText('apply'));
    await waitFor(() => expect(screen.getByLabelText('result').textContent).toBe('rejected'), { timeout: 4500 });
    expect(api.detail).not.toHaveBeenCalled();
  });
});

describe('page interaction lifecycle and form ownership', () => {
  let bridge: NonNullable<ReturnType<typeof usePageUiCommandBridge>>;
  const refresh = vi.fn();
  function Probe() { bridge = usePageUiCommandBridge()!; return null; }
  const navigation = ['todo', 'messages'].map(page => ({ routeKey: page, name: page, routeType: 'PAGE',
    componentKey: 'TODO_LIST', path: `/oa/${page}`, sortOrder: 1, children: [] }));
  const refreshCommand = { pageId: 'todo', code: 'ui.refreshPage', fields: [] };
  const shell = (pageId = 'todo') => <PageUiCommandBridge pageId={pageId} navigate={() => false} refresh={refresh}><Probe /></PageUiCommandBridge>;
  function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>(done => { resolve = done; });
    return { promise, resolve };
  }
  beforeEach(() => {
    vi.clearAllMocks();
    bindAuthResponseIdentity('bridge-user', 'original-permissions');
    api.navigation.mockResolvedValue(navigation);
    api.capability.mockImplementation((pageId: string) => Promise.resolve(capabilityFor(pageId, ['ui.refreshPage', 'ui.navigate', 'ui.openCreateForm', 'ui.fillForm'])));
  });
  afterEach(cleanup);

  it('rejects a delayed command after page A to B to A changes', async () => {
    const response = deferred<typeof navigation>(); api.navigation.mockReturnValue(response.promise);
    const view = render(shell());
    const result = bridge.applyTask(task([refreshCommand]));
    const rejected = expect(result).rejects.toThrow();
    view.rerender(shell('messages')); view.rerender(shell('todo'));
    await act(async () => { response.resolve(navigation); await rejected; });
    expect(refresh).not.toHaveBeenCalled();
  });
  it('rejects delayed capabilities after authorization changes and returns to the same values', async () => {
    const response = deferred<typeof navigation>(); api.navigation.mockReturnValue(response.promise);
    render(shell());
    const rejected = expect(bridge.applyTask(task([refreshCommand]))).rejects.toThrow();
    bindAuthResponseIdentity('bridge-user', 'revoked'); bindAuthResponseIdentity('bridge-user', 'original-permissions');
    await act(async () => { response.resolve(navigation); await rejected; });
    expect(refresh).not.toHaveBeenCalled();
  });
  it('does not apply a response after the owning bridge unmounts', async () => {
    const response = deferred<typeof navigation>(); api.navigation.mockReturnValue(response.promise);
    const view = render(shell());
    const rejected = expect(bridge.applyTask(task([refreshCommand]))).rejects.toThrow();
    view.unmount();
    response.resolve(navigation); await rejected;
    expect(refresh).not.toHaveBeenCalled();
  });
  it('requires the freshly available page preparation tool before navigation or refresh', async () => {
    const navigate = vi.fn(() => true);
    api.capability.mockResolvedValue({ ...capabilityFor('messages', ['ui.refreshPage']), tools: [] });
    render(<PageUiCommandBridge pageId="todo" navigate={navigate} refresh={refresh}><Probe /></PageUiCommandBridge>);
    await expect(bridge.applyTask(task([{ ...refreshCommand, pageId: 'messages' }]))).rejects.toThrow();
    expect(navigate).not.toHaveBeenCalled();
    expect(refresh).not.toHaveBeenCalled();
  });
  it('deduplicates concurrent delivery and retains the applied receipt', async () => {
    const response = deferred<typeof navigation>(); api.navigation.mockReturnValue(response.promise);
    render(shell()); const request = task([refreshCommand]);
    const first = bridge.applyTask(request);
    await expect(bridge.applyTask(request)).resolves.toBe(false);
    response.resolve(navigation);
    await expect(first).resolves.toBe(true);
    await expect(bridge.applyTask(request)).resolves.toBe(false);
    expect(refresh).toHaveBeenCalledTimes(1);
  });
  it('fails closed for a different in-flight task and inconsistent terminal states', async () => {
    const response = deferred<typeof navigation>(); api.navigation.mockReturnValue(response.promise);
    render(shell()); const request = task([refreshCommand]);
    const first = bridge.applyTask(request);
    await expect(bridge.applyTask({ ...request, taskId: 'other-task' })).rejects.toThrow();
    response.resolve(navigation); await first;
    await expect(bridge.applyTask({ ...request, taskId: 'bad-task', status: 'PARTIALLY_SUCCEEDED' })).rejects.toThrow();
    expect(refresh).toHaveBeenCalledTimes(1);
  });
  it('allows its own authorized navigation before applying a target-page refresh', async () => {
    function Navigating() {
      const [pageId, setPage] = useState('todo');
      return <PageUiCommandBridge pageId={pageId} navigate={page => { setPage(page); return true; }} refresh={refresh}>
        <output aria-label="current-page">{pageId}</output><Probe /></PageUiCommandBridge>;
    }
    render(<Navigating />);
    let result!: Promise<boolean>;
    act(() => { result = bridge.applyTask(task([
      { pageId: 'todo', code: 'ui.navigate', targetPageId: 'messages', fields: [] },
      { pageId: 'messages', code: 'ui.refreshPage', fields: [] },
    ])); });
    await waitFor(() => expect(screen.getByLabelText('current-page').textContent).toBe('messages'));
    await expect(result).resolves.toBe(true);
    expect(refresh).toHaveBeenCalledTimes(1);
  });
  it('binds open and fill to the newly opened form and refuses another form session', async () => {
    function FormPage() {
      const [session, setSession] = useState<string | null>(null);
      useRegisterPageUiCommands({ 'ui.openCreateForm': () => { setSession('new_form_session_123456'); },
        'ui.fillForm': () => { api.detail(); } }, session ? { sessionId: session } : null);
      return <><Probe /><output aria-label="form-session">{session}</output>
        <button onClick={() => setSession('replacement_session_1234')}>replace-form</button></>;
    }
    render(<PageUiCommandBridge pageId="todo" navigate={() => false} refresh={refresh}>
      <PageAgentContextProvider pageId="todo"><FormPage /></PageAgentContextProvider></PageUiCommandBridge>);
    let result!: Promise<boolean>;
    act(() => { result = bridge.applyTask(task([
      { pageId: 'todo', code: 'ui.openCreateForm', fields: [] },
      { pageId: 'todo', code: 'ui.fillForm', fields: [{ name: 'reason', value: 'synthetic' }] },
    ])); });
    await waitFor(() => expect(screen.getByLabelText('form-session').textContent).toBe('new_form_session_123456'));
    await expect(result).resolves.toBe(true);
    expect(api.detail).toHaveBeenCalledTimes(1);
    fireEvent.click(screen.getByText('replace-form'));
    const stale = task([{ pageId: 'todo', code: 'ui.fillForm', fields: [{ name: 'reason', value: 'stale' }],
      formContext: { sessionId: 'new_form_session_123456' } }]); stale.taskId = 'stale-form-task';
    await expect(bridge.applyTask(stale)).rejects.toThrow();
    expect(api.detail).toHaveBeenCalledTimes(1);
  });
});
