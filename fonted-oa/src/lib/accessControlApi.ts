export interface AccessUser {
  id: number;
  name: string;
  email: string;
  role: string;
  roles: string[];
  status: number;
  departmentId?: number;
  positionId?: number;
  approverUserId?: number;
  permissionVersion: number;
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
  componentKey?: 'DASHBOARD' | 'AI_WORKSPACE' | 'ACCESS_CONTROL'
    | 'TODO_LIST' | 'LEAVE_FORM' | 'MY_APPLICATIONS' | 'AUDIT_CENTER' | 'ORG_TREE';
  permissionCode?: string;
  sortOrder: number;
  enabled: boolean;
}

export interface AccessControlOverview {
  users: AccessUser[];
  roles: AccessRole[];
  permissions: AccessPermission[];
  routes: AccessRoute[];
  departments: Array<{
    id: number;
    code: string;
    name: string;
    parentId?: number;
    defaultApproverUserId?: number;
    status: number;
  }>;
  positions: Array<{ id: number; code: string; name: string; status: number }>;
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
  assignUserRoles: (userId: number, roleCodes: string[]) =>
    request<AccessUser>(`/users/${userId}/roles`, {
      method: 'PUT',
      body: JSON.stringify({ roleCodes }),
    }),
  updateUserOrganization: (
    userId: number,
    payload: { departmentId: number; positionId: number; approverUserId?: number },
  ) => request<AccessUser>(`/users/${userId}/organization`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  }),
  updateUserStatus: (userId: number, status: number) =>
    request<AccessUser>(`/users/${userId}/status`, {
      method: 'PUT',
      body: JSON.stringify({ status }),
    }),
  saveDepartment: (payload: { code: string; name: string; parentId?: number; defaultApproverUserId?: number }) =>
    request<AccessControlOverview['departments'][number]>('/departments', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  savePosition: (payload: { code: string; name: string }) =>
    request<AccessControlOverview['positions'][number]>('/positions', {
      method: 'POST',
      body: JSON.stringify(payload),
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
  deleteRole: (roleCode: string) =>
    request<boolean>(`/roles/${encodeURIComponent(roleCode)}`, { method: 'DELETE' }),
  saveRoute: (payload: SaveRoutePayload) =>
    request<AccessRoute>(`/routes/${encodeURIComponent(payload.routeKey)}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
};
