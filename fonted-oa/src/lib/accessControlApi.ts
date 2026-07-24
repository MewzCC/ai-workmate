export interface AccessUser {
  id: number;
  name: string;
  email: string;
  role: string;
  status: number;
  updatedAt: string;
}

export interface AccessRole {
  code: string;
  name: string;
  description: string;
  builtin: boolean;
  permissions: string[];
}

export interface AccessPermission {
  code: string;
  name: string;
  module: string;
  description: string;
}

export interface AccessRoute {
  routeKey: string;
  parentKey?: string;
  name: string;
  path?: string;
  icon?: string;
  routeType: 'GROUP' | 'MENU' | 'PAGE';
  componentKey?: 'DASHBOARD' | 'AI_WORKSPACE' | 'ACCESS_CONTROL';
  permissionCode?: string;
  sortOrder: number;
  enabled: boolean;
}

export interface AccessControlOverview {
  users: AccessUser[];
  roles: AccessRole[];
  permissions: AccessPermission[];
  routes: AccessRoute[];
}

export interface SaveRoutePayload {
  routeKey: string;
  parentKey?: string;
  name: string;
  path?: string;
  icon?: string;
  routeType: AccessRoute['routeType'];
  componentKey?: AccessRoute['componentKey'];
  sortOrder: number;
  enabled: boolean;
}

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
}

async function request<T>(path = '', init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/admin/access-control${path}`, {
    credentials: 'include',
    ...init,
    headers: init?.body ? { 'Content-Type': 'application/json', ...init.headers } : init?.headers,
  });
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  if (!response.ok || !result || result.code !== 200 || result.data === null) {
    throw new Error(result?.message || '权限配置请求失败');
  }
  return result.data;
}

export const accessControlApi = {
  overview: () => request<AccessControlOverview>(),
  assignUserRole: (userId: number, roleCode: string) =>
    request<AccessUser>(`/users/${userId}/role`, {
      method: 'PUT',
      body: JSON.stringify({ roleCode }),
    }),
  updateRolePermissions: (roleCode: string, permissionCodes: string[]) =>
    request<AccessRole>(`/roles/${encodeURIComponent(roleCode)}/permissions`, {
      method: 'PUT',
      body: JSON.stringify({ permissionCodes }),
    }),
  createRole: (payload: { code: string; name: string; description: string }) =>
    request<AccessRole>('/roles', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  saveRoute: (payload: SaveRoutePayload) =>
    request<AccessRoute>(`/routes/${encodeURIComponent(payload.routeKey)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
};
