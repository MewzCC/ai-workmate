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
};
