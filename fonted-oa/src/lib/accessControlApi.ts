import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';

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
  avatarUrl?: string | null;
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
    | 'TODO_LIST' | 'LEAVE_FORM' | 'MY_APPLICATIONS' | 'AUDIT_CENTER'
    | 'ORG_TREE' | 'KNOWLEDGE_BASE' | 'MESSAGE_CENTER';
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
    headers: buildApiHeaders(Boolean(init?.body), init?.headers),
  });
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  const isVoidResponse = init?.method === 'DELETE';
  if (!response.ok || !result || result.code !== 200
      || (!isVoidResponse && result.data === null)) {
    throw new Error(result?.message || i18n.t('errors.access.requestFailed'));
  }
  return (result.data ?? null) as T;
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
  deleteDepartment: (departmentId: number) =>
    request<void>(`/departments/${departmentId}`, { method: 'DELETE' }),
  deletePosition: (positionId: number) =>
    request<void>(`/positions/${positionId}`, { method: 'DELETE' }),
  updateRolePermissions: (roleCode: string, permissionCodes: string[]) =>
    request<AccessRole>(`/roles/${encodeURIComponent(roleCode)}/permissions`, {
      method: 'PUT',
      body: JSON.stringify({ permissionCodes }),
    }),
  updateRoleMembers: (roleCode: string, userIds: number[]) =>
    request<AccessControlOverview>(`/roles/${encodeURIComponent(roleCode)}/members`, {
      method: 'PUT',
      body: JSON.stringify({ userIds }),
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
