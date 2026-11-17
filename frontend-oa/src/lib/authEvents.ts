import i18n from '@/i18n';

export class AuthResponseScopeError extends Error {
  readonly code = 'AUTH_SCOPE_CHANGED';
  constructor() {
    super(i18n.t('errors.authResponseStale'));
    this.name = 'AuthResponseScopeError';
  }
}

export const AUTH_EXPIRED_EVENT = 'oa-auth-expired';
export const PERMISSIONS_STALE_EVENT = 'oa-permissions-stale';
export const PERMISSIONS_CHANGED_EVENT = 'oa-permissions-changed';

// Browser correlation only. Cookies and server-side authorization remain authoritative.
let identity: string | null = null;
let revision = 0;
let dataRevision = 0;
let authorization: string | undefined;
const responses = new WeakMap<Response, { identity: number; data: number }>();

export function bindAuthResponseIdentity(next: string | null, snapshot?: string) {
  if (identity !== next) { identity = next; revision++; dataRevision++; authorization = snapshot; }
  else if (snapshot !== undefined && authorization !== snapshot) { authorization = snapshot; dataRevision++; }
}
export function invalidateAuthResponses() { revision++; dataRevision++; }
export function captureAuthResponseScope() { return revision; }
export function isAuthResponseCurrent(scope: number) { return scope === revision; }
export function isAuthResponseIdentity(next: string | null) { return identity === next; }
export function captureAuthDataScope() {
  const owner = revision, data = dataRevision;
  return () => owner === revision && data === dataRevision;
}

/** Preserve the request's scope through delayed headers and body parsing. */
export async function authenticatedFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const scope = { identity: revision, data: dataRevision };
  const response = await globalThis.fetch(input, init);
  responses.set(response, scope);
  return response;
}

function current(source: Response | number) {
  const scope = typeof source === 'number' ? source : responses.get(source)?.identity;
  return scope !== undefined && isAuthResponseCurrent(scope);
}

function currentBody(response: Response) {
  const scope = responses.get(response);
  return Boolean(scope && scope.identity === revision && scope.data === dataRevision);
}
function discardBody(response: Response) {
  // Release an unread response, never send cancellation to a business task endpoint.
  if (response.body && !response.body.locked) void response.body.cancel().catch(() => undefined);
}
export function assertAuthResponseCurrent(response: Response) {
  if (!currentBody(response)) { discardBody(response); throw new AuthResponseScopeError(); }
}

/** Keep HTTP rejections typed by their status, but never return another scope's response body. */
export async function readAuthenticatedJson(response: Response): Promise<unknown> {
  if (!response.ok && !currentBody(response)) { discardBody(response); return null; }
  assertAuthResponseCurrent(response);
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok && !currentBody(response)) { discardBody(response); return null; }
  assertAuthResponseCurrent(response);
  return body;
}

async function readCurrentBody<T>(response: Response, read: () => Promise<T>): Promise<T> {
  assertAuthResponseCurrent(response);
  try { return await read(); }
  finally { assertAuthResponseCurrent(response); }
}
export function readAuthenticatedBlob(response: Response) { return readCurrentBody(response, () => response.blob()); }
export function readAuthenticatedText(response: Response) { return readCurrentBody(response, () => response.text()); }
export async function readAuthenticatedChunk(response: Response, reader: ReadableStreamDefaultReader<Uint8Array>) {
  try { return await readCurrentBody(response, () => reader.read()); }
  catch (error) {
    if (error instanceof AuthResponseScopeError) void reader.cancel().catch(() => undefined);
    throw error;
  }
}

export function isCurrentAuthEvent(event: Event) {
  // Manual local permission-change signals remain supported; network signals always carry a scope.
  return !(event instanceof CustomEvent) || event.detail === undefined || event.detail === null
    || (typeof event.detail?.revision === 'number' && isAuthResponseCurrent(event.detail.revision));
}

function dispatch(name: string) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(name, { detail: { revision } }));
}

export function notifyAuthResponseStatus(status: number, source: Response | number = revision) {
  if (!current(source)) return;
  if (status === 401) dispatch(AUTH_EXPIRED_EVENT);
  if (status === 403) dispatch(PERMISSIONS_STALE_EVENT);
}

export function notifyPermissionsChanged(source: Response | number = revision) {
  if (!current(source)) return;
  dispatch(PERMISSIONS_CHANGED_EVENT);
}
