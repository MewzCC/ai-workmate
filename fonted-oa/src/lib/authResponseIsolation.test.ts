import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import ts from 'typescript';
import { authenticatedFetch, bindAuthResponseIdentity, captureAuthResponseScope, invalidateAuthResponses, notifyAuthResponseStatus, readAuthenticatedBlob, readAuthenticatedJson, AUTH_EXPIRED_EVENT, PERMISSIONS_STALE_EVENT } from './authEvents';
import { request, subscribeAiTaskEvents } from './oaApi';
import { listConversations, loadAttachmentText, uploadAttachment, streamChat } from './chatApi';
import { getNavigation } from './navigationApi';
import { listNotifications } from './notificationApi';
import { getChatPreferences } from './userSettingsApi';
import { hrApi } from './hrApi';
import { knowledgeApi } from './knowledgeApi';
import { profileApi } from './profileApi';
import { accessControlApi } from './accessControlApi';
import { adminAssetsApi } from './adminAssetsApi';

const file = new File(['safe'], 'synthetic.txt');
const clients = [
  ['OA', () => request('/synthetic')], ['chat', () => listConversations()], ['attachment', () => loadAttachmentText(1)],
  ['navigation', () => getNavigation()], ['notifications', () => listNotifications()], ['settings', () => getChatPreferences()],
  ['HR', () => hrApi.overview()], ['knowledge', () => knowledgeApi.listBases()], ['profile', () => profileApi.update('Synthetic name')],
  ['access', () => accessControlApi.overview()], ['seal-upload', () => adminAssetsApi.uploadSealUsageDocument(1, file)],
] as const;
function error(status: number) { return new Response(JSON.stringify({ code: status, message: 'Synthetic rejection', data: null }), { status }); }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
beforeEach(() => { invalidateAuthResponses(); bindAuthResponseIdentity('tenant1:user1', 'synthetic-initial-snapshot'); });
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); vi.useRealTimers(); invalidateAuthResponses(); });

it.each(clients)('%s keeps an old 401/403 out of another authenticated scope', async (_name, invoke) => {
  for (const status of [401, 403]) {
    bindAuthResponseIdentity('tenant1:user1');
    const response = deferred<Response>(); vi.spyOn(globalThis, 'fetch').mockReturnValueOnce(response.promise);
    const dispatch = vi.spyOn(window, 'dispatchEvent'); dispatch.mockClear();
    const pending = invoke(); const rejected = expect(pending).rejects.toBeInstanceOf(Error);
    bindAuthResponseIdentity('tenant2:user2'); response.resolve(error(status)); await rejected;
    expect(dispatch).not.toHaveBeenCalled();
  }
});

it.each(clients)('%s refuses a successful response from a replaced identity before returning its data', async (_name, invoke) => {
  const response = deferred<Response>(); vi.spyOn(globalThis, 'fetch').mockReturnValueOnce(response.promise);
  const pending = invoke(); const rejected = expect(pending).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
  bindAuthResponseIdentity('tenant2:user2');
  response.resolve(new Response(JSON.stringify({ code: 200, data: { privateMarker: 'Synthetic old account data' } })));
  await rejected;
});

it.each(clients)('%s refuses a successful response after its current authorization snapshot changes', async (_name, invoke) => {
  const response = deferred<Response>(); vi.spyOn(globalThis, 'fetch').mockReturnValueOnce(response.promise);
  const pending = invoke(); const rejected = expect(pending).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
  bindAuthResponseIdentity('tenant1:user1', 'synthetic-revoked-snapshot');
  response.resolve(new Response(JSON.stringify({ code: 200, data: { privateMarker: 'Synthetic old authority data' } })));
  await rejected;
});

it('checks authorization again after JSON parsing and does not consume an untracked success', async () => {
  const body = deferred<object>(), response = new Response('{}');
  vi.spyOn(response, 'json').mockReturnValueOnce(body.promise); vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response);
  const fetchResponse = await authenticatedFetch('/synthetic');
  const pending = readAuthenticatedJson(fetchResponse); const rejected = expect(pending).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
  bindAuthResponseIdentity('tenant1:user1', 'synthetic-new-snapshot'); body.resolve({ privateMarker: 'Synthetic old result' }); await rejected;
  await expect(readAuthenticatedJson(new Response('{"privateMarker":"untracked"}'))).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
});

it.each([200, 401])('releases an unread stale %s response without consuming private data', async status => {
  const cancel = vi.fn();
  const response = new Response(new ReadableStream({ cancel }), { status });
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response);
  const owned = await authenticatedFetch('/synthetic');
  const parse = vi.spyOn(owned, 'json');
  bindAuthResponseIdentity('tenant2:user2');
  if (status === 200) await expect(readAuthenticatedJson(owned)).rejects.toMatchObject({ code: 'AUTH_SCOPE_CHANGED' });
  else expect(await readAuthenticatedJson(owned)).toBeNull();
  expect(cancel).toHaveBeenCalledTimes(1); expect(parse).not.toHaveBeenCalled();
});

it('does not release a private blob when identity changes during body delivery', async () => {
  const body = deferred<Blob>(), response = new Response('synthetic');
  vi.spyOn(response, 'blob').mockReturnValueOnce(body.promise); vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response);
  const pending = readAuthenticatedBlob(await authenticatedFetch('/synthetic'));
  const rejected = expect(pending).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
  bindAuthResponseIdentity('tenant2:user2'); body.resolve(new Blob(['Synthetic private file'])); await rejected;
});

it('preserves the original scope while a response body is still being parsed', async () => {
  const body = deferred<object>();
  const response = error(401); vi.spyOn(response, 'json').mockReturnValue(body.promise);
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(response);
  const dispatch = vi.spyOn(window, 'dispatchEvent');
  const pending = request('/synthetic'); const rejected = expect(pending).rejects.toBeInstanceOf(Error);
  await vi.waitFor(() => expect(response.json).toHaveBeenCalled());
  bindAuthResponseIdentity('tenant1:user2'); body.resolve({ code: 401, message: 'Synthetic rejection', data: null });
  await rejected; expect(dispatch).not.toHaveBeenCalled();
});

it.each([401, 403])('still signals a current %s after a same-user permission refresh', async status => {
  const response = deferred<Response>(); vi.spyOn(globalThis, 'fetch').mockReturnValueOnce(response.promise);
  const dispatch = vi.spyOn(window, 'dispatchEvent'); const pending = request('/synthetic');
  const rejected = expect(pending).rejects.toBeInstanceOf(Error);
  bindAuthResponseIdentity('tenant1:user1'); response.resolve(error(status)); await rejected;
  expect(dispatch).toHaveBeenCalledTimes(1);
  expect(dispatch.mock.calls[0][0].type).toBe(status === 401 ? AUTH_EXPIRED_EVENT : PERMISSIONS_STALE_EVENT);
});

it('does not revive a request when an identity changes away and back or a provider is disposed', async () => {
  const response = deferred<Response>(); vi.spyOn(globalThis, 'fetch').mockReturnValueOnce(response.promise);
  const dispatch = vi.spyOn(window, 'dispatchEvent'); const pending = authenticatedFetch('/synthetic');
  bindAuthResponseIdentity('tenant1:user2'); bindAuthResponseIdentity('tenant1:user1');
  response.resolve(error(401)); const result = await pending; notifyAuthResponseStatus(401, result);
  expect(dispatch).not.toHaveBeenCalled();
  const scope = captureAuthResponseScope(); invalidateAuthResponses(); notifyAuthResponseStatus(403, scope);
  expect(dispatch).not.toHaveBeenCalled();
});

class UploadRequest {
  static active: UploadRequest;
  status = 401; responseText = '{"code":401,"message":"Synthetic rejection","data":null}';
  withCredentials = false; upload = { onprogress: null };
  onload?: () => void; onerror?: () => void; ontimeout?: () => void;
  open() {} setRequestHeader() {} send() { UploadRequest.active = this; }
}
it.each(['chat', 'knowledge'] as const)('%s XHR upload captures its scope before completion', async kind => {
  vi.stubGlobal('XMLHttpRequest', UploadRequest); const dispatch = vi.spyOn(window, 'dispatchEvent');
  const pending = kind === 'chat' ? uploadAttachment(1, file) : knowledgeApi.upload(1, file);
  const rejected = expect(pending).rejects.toBeInstanceOf(Error);
  bindAuthResponseIdentity('tenant1:user2'); UploadRequest.active.onload?.(); await rejected;
  expect(dispatch).not.toHaveBeenCalled();
});

it.each(['chat', 'knowledge'] as const)('%s XHR rejects an old successful upload after a permission change', async kind => {
  vi.stubGlobal('XMLHttpRequest', UploadRequest);
  const pending = kind === 'chat' ? uploadAttachment(1, file) : knowledgeApi.upload(1, file);
  const rejected = expect(pending).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
  UploadRequest.active.status = 200; UploadRequest.active.responseText = '{"code":200,"data":{"id":1}}';
  bindAuthResponseIdentity('tenant1:user1', 'synthetic-new-snapshot'); UploadRequest.active.onload?.(); await rejected;
});

it('stops a delayed task stream after account replacement without events, errors or reconnection', async () => {
  vi.useFakeTimers();
  let deliver!: ReadableStreamDefaultController<Uint8Array>;
  const cancel = vi.fn(), connected = vi.fn(), onEvent = vi.fn(), onError = vi.fn();
  const response = new Response(new ReadableStream<Uint8Array>({ start(controller) { deliver = controller; }, cancel }));
  const fetcher = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
  const stop = subscribeAiTaskEvents('synthetic-task', { onConnected: connected, onEvent, onError });
  await vi.waitFor(() => expect(connected).toHaveBeenCalledTimes(1));
  bindAuthResponseIdentity('tenant2:user2'); deliver.enqueue(new TextEncoder().encode('id: 1\nevent: snapshot\ndata: {"status":"RUNNING"}\n\n'));
  await vi.waitFor(() => expect(cancel).toHaveBeenCalled()); await vi.advanceTimersByTimeAsync(2000); stop();
  expect(onEvent).not.toHaveBeenCalled(); expect(onError).not.toHaveBeenCalled(); expect(fetcher).toHaveBeenCalledTimes(1);
});

it('does not emit another buffered chat frame after the callback changes the authenticated identity', async () => {
  const encoder = new TextEncoder(), cancel = vi.fn();
  const response = new Response(new ReadableStream<Uint8Array>({
    start(controller) { controller.enqueue(encoder.encode('data: {"type":"delta","data":"Synthetic first"}\n\ndata: {"type":"delta","data":"Synthetic second"}\n\n')); }, cancel,
  }));
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(response);
  const onEvent = vi.fn(() => bindAuthResponseIdentity('tenant2:user2'));
  await expect(streamChat({ conversationId: 1, message: 'Synthetic request', model: 'synthetic-model', attachmentIds: [], maxContextRounds: 3 }, new AbortController().signal, onEvent)).rejects.toMatchObject({ name: 'AuthResponseScopeError', code: 'AUTH_SCOPE_CHANGED' });
  expect(onEvent).toHaveBeenCalledTimes(1); expect(cancel).toHaveBeenCalled();
});

it('requires all API authentication signals to carry their request scope and fetches to use the shared transport', () => {
  const directory = path.resolve(process.cwd(), 'src/lib');
  for (const name of readdirSync(directory).filter(name => name.endsWith('.ts') && !name.includes('.test.'))) {
    const source = ts.createSourceFile(name, readFileSync(path.join(directory, name), 'utf8'), ts.ScriptTarget.Latest, true);
    const wrapped = source.statements.some(statement => ts.isImportDeclaration(statement) && statement.importClause?.namedBindings
      && ts.isNamedImports(statement.importClause.namedBindings) && statement.importClause.namedBindings.elements.some(item => item.name.text === 'fetch' && item.propertyName?.text === 'authenticatedFetch'));
    const inspect = (node: ts.Node) => {
      if (ts.isCallExpression(node) && ts.isIdentifier(node.expression)) {
        if (node.expression.text === 'notifyAuthResponseStatus') expect(node.arguments.length, name).toBe(2);
        if (node.expression.text === 'fetch' && name !== 'authApi.ts') expect(wrapped, name).toBe(true);
      }
      if (ts.isCallExpression(node) && ts.isPropertyAccessExpression(node.expression) && ['json', 'blob', 'text', 'getReader'].includes(node.expression.name.text)
          && !['authEvents.ts', 'authApi.ts'].includes(name)) {
        expect(['getReader'], `Unscoped response body read in ${name}`).toContain(node.expression.name.text);
      }
      ts.forEachChild(node, inspect);
    };
    inspect(source);
  }
});
