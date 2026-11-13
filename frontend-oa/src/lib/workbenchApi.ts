import { queryString, request } from '@/lib/oaApi';

export type WorkbenchStatus = 'DRAFT' | 'ACTIVE' | 'PENDING' | 'COMPLETED' | 'DISABLED' | 'FAILED';

export interface WorkbenchRecordPayload {
  code: string;
  title: string;
  category?: string;
  status?: WorkbenchStatus;
  amount?: number | null;
  owner?: string;
  details?: string;
  version?: number;
}

export interface WorkbenchRecord extends WorkbenchRecordPayload {
  id: number;
  moduleKey: string;
  status: WorkbenchStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
}

export interface WorkbenchPage {
  records: WorkbenchRecord[];
  total: number;
  page: number;
  size: number;
  canManage: boolean;
}

const path = (moduleKey: string) => `/workbench/modules/${encodeURIComponent(moduleKey)}/records`;

export const workbenchApi = {
  list: (moduleKey: string, params: { keyword?: string; status?: string; page?: number; size?: number }) =>
    request<WorkbenchPage>(`${path(moduleKey)}${queryString(params)}`),
  create: (moduleKey: string, payload: WorkbenchRecordPayload) =>
    request<WorkbenchRecord>(path(moduleKey), { method: 'POST', body: JSON.stringify(payload) }),
  update: (moduleKey: string, id: number, payload: WorkbenchRecordPayload) =>
    request<WorkbenchRecord>(`${path(moduleKey)}/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  delete: (moduleKey: string, id: number, version: number) =>
    request<void>(`${path(moduleKey)}/${id}`, {
      method: 'DELETE',
      body: JSON.stringify({ version }),
    }),
};
