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
};
