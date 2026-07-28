export interface OrganizationDepartment {
  id: number;
  code: string;
  name: string;
  parentId?: number;
  defaultApproverUserId?: number;
  status: number;
}

export interface OrganizationPosition {
  id: number;
  code: string;
  name: string;
  status: number;
}

export interface OrganizationMember {
  id: number;
  name: string;
  departmentId: number;
  positionId: number;
  approverUserId?: number;
}

export interface OrganizationOverview {
  departments: OrganizationDepartment[];
  positions: OrganizationPosition[];
  members: OrganizationMember[];
  canManage: boolean;
}

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T;
}

export class OrganizationApiError extends Error {
  constructor(message: string, readonly status: number, readonly errorCode?: string) {
    super(message);
    this.name = 'OrganizationApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      credentials: 'include',
      ...init,
      headers: init?.body ? { 'Content-Type': 'application/json', ...init.headers } : init?.headers,
    });
  } catch {
    throw new OrganizationApiError('无法连接组织服务，请确认后端服务可用', 0);
  }
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (response.status === 401) window.dispatchEvent(new Event('oa-auth-expired'));
  if (!response.ok || !result || result.code !== 200) {
    throw new OrganizationApiError(
      result?.message || (response.status === 403 ? '没有组织架构操作权限' : '组织服务请求失败'),
      response.status,
      result?.errorCode,
    );
  }
  return result.data;
}

export const organizationApi = {
  overview: () => request<OrganizationOverview>('/organization'),
  saveDepartment: (payload: {
    code: string; name: string; parentId?: number; defaultApproverUserId?: number;
  }) => request<OrganizationDepartment>('/organization/departments', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  savePosition: (payload: { code: string; name: string }) =>
    request<OrganizationPosition>('/organization/positions', {
      method: 'POST', body: JSON.stringify(payload),
    }),
  updateMember: (id: number, payload: {
    departmentId: number; positionId: number; approverUserId?: number;
  }) => request<void>(`/organization/users/${id}`, {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  deleteDepartment: (id: number) =>
    request<void>(`/organization/departments/${id}`, { method: 'DELETE' }),
  deletePosition: (id: number) =>
    request<void>(`/organization/positions/${id}`, { method: 'DELETE' }),
};
