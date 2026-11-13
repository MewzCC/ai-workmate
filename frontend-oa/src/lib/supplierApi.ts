import { queryString, request } from '@/lib/oaApi';

export type SupplierStatus = 'DRAFT' | 'ACTIVE' | 'SUSPENDED' | 'BLACKLISTED';
export type SupplierCategory = 'MATERIAL' | 'SERVICE' | 'LOGISTICS' | 'CONSULTING' | 'OTHER';
export type SupplierLevel = 'STRATEGIC' | 'PREFERRED' | 'STANDARD' | 'RESTRICTED';

export interface SupplierPayload {
  code: string;
  name: string;
  shortName?: string;
  unifiedSocialCreditCode?: string;
  category: SupplierCategory;
  supplierLevel: SupplierLevel;
  contactName?: string;
  contactPhone?: string;
  contactEmail?: string;
  address?: string;
  paymentTerms?: string;
  riskNote?: string;
  version?: number;
}

export interface Supplier extends SupplierPayload {
  id: number;
  status: SupplierStatus;
  version: number;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
  allowedTransitions: SupplierStatus[];
}

export interface SupplierStatusHistory {
  id: number;
  fromStatus?: SupplierStatus;
  toStatus: SupplierStatus;
  reason?: string;
  operatorLabel: string;
  createdAt: string;
}

export interface SupplierDetail {
  supplier: Supplier;
  statusHistory: SupplierStatusHistory[];
}

export interface SupplierPage {
  records: Supplier[];
  total: number;
  page: number;
  size: number;
  stats: { total: number; active: number; suspended: number; blacklisted: number };
  canManage: boolean;
}

export const supplierApi = {
  list: (params: { keyword?: string; status?: string; category?: string; page?: number; size?: number }) =>
    request<SupplierPage>(`/suppliers${queryString(params)}`),
  detail: (id: number) => request<SupplierDetail>(`/suppliers/${id}`),
  create: (payload: SupplierPayload) =>
    request<Supplier>('/suppliers', { method: 'POST', body: JSON.stringify(payload) }),
  update: (id: number, payload: SupplierPayload) =>
    request<Supplier>(`/suppliers/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  updateStatus: (id: number, status: SupplierStatus, version: number, reason?: string) =>
    request<Supplier>(`/suppliers/${id}/status`, {
      method: 'POST', body: JSON.stringify({ status, version, reason }),
    }),
};
