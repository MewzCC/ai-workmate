import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';

export interface HrDepartment {
  id: number;
  code: string;
  name: string;
  parentId?: number;
  defaultApproverUserId?: number;
  status: number;
}

export interface HrPosition {
  id: number;
  code: string;
  name: string;
  status: number;
}

export interface HrEmployee {
  id: number;
  name: string;
  email: string;
  role: string;
  status: number;
  departmentId?: number;
  positionId?: number;
  approverUserId?: number;
  approverName?: string;
  avatarUrl?: string | null;
  approverAvatarUrl?: string | null;
}

export interface OrganizationOverview {
  departments: HrDepartment[];
  positions: HrPosition[];
  employees: HrEmployee[];
}

export interface EmployeeAttendanceOverview {
  totalDays: number;
  normalDays: number;
  lateDays: number;
  earlyLeaveDays: number;
  lateAndEarlyDays: number;
  missingClockDays: number;
}

export interface EmployeeActivityRecord {
  id: number;
  type: 'LEAVE' | 'REISSUE';
  title: string;
  status: string;
  startDate: string;
  endDate: string;
  createdAt: string | null;
}

export interface EmployeeDetail {
  id: number;
  name: string;
  email: string;
  role: string;
  status: number;
  avatarUrl: string | null;
  createdAt: string | null;
  departmentId?: number;
  departmentName?: string | null;
  positionId?: number;
  positionName?: string | null;
  approverUserId?: number;
  approverName?: string | null;
  approverAvatarUrl?: string | null;
  attendance: EmployeeAttendanceOverview;
  recentActivities: EmployeeActivityRecord[];
}

interface ApiResult<T> {
  code: number;
  message: string;
  data: T | null;
}

export type EmployeeChangeType = 'ONBOARDING' | 'REGULARIZATION' | 'TRANSFER' | 'OFFBOARDING';
export type EmployeeChangeStatus = 'PENDING' | 'APPROVED' | 'EFFECTIVE' | 'REJECTED' | 'WITHDRAWN';

export interface EmployeeChange {
  id: number;
  employeeUserId: number;
  employeeName: string;
  employeeEmail: string;
  applicantUserId: number;
  applicantName: string;
  reviewApproverUserId: number;
  reviewApproverName: string;
  changeType: EmployeeChangeType;
  effectiveDate: string;
  currentDepartmentId?: number | null;
  currentDepartmentName?: string | null;
  currentPositionId?: number | null;
  currentPositionName?: string | null;
  currentSupervisorUserId?: number | null;
  currentSupervisorName?: string | null;
  targetDepartmentId?: number | null;
  targetDepartmentName?: string | null;
  targetPositionId?: number | null;
  targetPositionName?: string | null;
  targetSupervisorUserId?: number | null;
  targetSupervisorName?: string | null;
  reason: string;
  status: EmployeeChangeStatus;
  decisionComment?: string | null;
  version: number;
  canApprove: boolean;
  canWithdraw: boolean;
  submittedAt: string;
  decidedAt?: string | null;
  withdrawnAt?: string | null;
  appliedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeChangePayload {
  employeeUserId: number;
  changeType: EmployeeChangeType;
  effectiveDate: string;
  targetDepartmentId?: number;
  targetPositionId?: number;
  targetSupervisorUserId?: number;
  reviewApproverUserId: number;
  reason: string;
}

interface PageResponse<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

async function hrRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const hasBody = init?.body != null;
  const res = await fetch(`/api/hr${path}`, {
    ...init,
    credentials: 'include',
    headers: { ...buildApiHeaders(hasBody), ...init?.headers },
  });
  if (res.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  const json = await res.json().catch(() => null) as ApiResult<T> | null;
  if (!res.ok || !json || json.code !== 200 || json.data === null) {
    throw new Error(json?.message || i18n.t('errors.hr.employeeLoadFailed'));
  }
  return json.data;
}

export const hrApi = {
  overview: async (): Promise<OrganizationOverview> => {
    const res = await fetch('/api/hr/organization', { credentials: 'include', headers: buildApiHeaders(false) });
    if (res.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    const json = await res.json().catch(() => null) as ApiResult<OrganizationOverview> | null;
    if (!res.ok || !json || json.code !== 200 || json.data === null) {
      throw new Error(json?.message || i18n.t('errors.hr.organizationLoadFailed'));
    }
    return json.data;
  },
  detail: async (id: number): Promise<EmployeeDetail> => {
    const res = await fetch(`/api/hr/employees/${id}`, {
      credentials: 'include',
      headers: buildApiHeaders(false),
    });
    if (res.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    const json = await res.json().catch(() => null) as ApiResult<EmployeeDetail> | null;
    if (!res.ok || !json || json.code !== 200 || json.data === null) {
      throw new Error(json?.message || i18n.t('errors.hr.employeeLoadFailed'));
    }
    return json.data;
  },
  listEmployeeChanges: (params: {
    status?: EmployeeChangeStatus;
    changeType?: EmployeeChangeType;
    keyword?: string;
    page?: number;
    size?: number;
  } = {}) => {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') query.set(key, String(value));
    });
    return hrRequest<PageResponse<EmployeeChange>>(`/employee-changes?${query.toString()}`);
  },
  employeeChangeDetail: (id: number) =>
    hrRequest<EmployeeChange>(`/employee-changes/${id}`),
  createEmployeeChange: (payload: EmployeeChangePayload) =>
    hrRequest<EmployeeChange>('/employee-changes', {
      method: 'POST', body: JSON.stringify(payload),
    }),
  approveEmployeeChange: (id: number, version: number, comment?: string) =>
    hrRequest<EmployeeChange>(`/employee-changes/${id}/approve`, {
      method: 'POST', body: JSON.stringify({ version, comment }),
    }),
  rejectEmployeeChange: (id: number, version: number, comment: string) =>
    hrRequest<EmployeeChange>(`/employee-changes/${id}/reject`, {
      method: 'POST', body: JSON.stringify({ version, comment }),
    }),
  withdrawEmployeeChange: (id: number, version: number) =>
    hrRequest<EmployeeChange>(`/employee-changes/${id}/withdraw`, {
      method: 'POST', body: JSON.stringify({ version }),
    }),
};
