export const AUTH_EXPIRED_EVENT = 'oa-auth-expired';
export const PERMISSIONS_STALE_EVENT = 'oa-permissions-stale';
export const PERMISSIONS_CHANGED_EVENT = 'oa-permissions-changed';

function dispatch(name: string) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(name));
}

export function notifyAuthResponseStatus(status: number) {
  if (status === 401) dispatch(AUTH_EXPIRED_EVENT);
  if (status === 403) dispatch(PERMISSIONS_STALE_EVENT);
}

export function notifyPermissionsChanged() {
  dispatch(PERMISSIONS_CHANGED_EVENT);
}
