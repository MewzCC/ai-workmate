import { queryString, request } from '@/lib/oaApi';

export type ContractStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'TERMINATED';
export type ContractType = 'PURCHASE' | 'SALES' | 'SERVICE' | 'LEASE' | 'OTHER';
export type FulfillmentStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'FULFILLED' | 'BREACHED';
export type ContractExpiryState = 'NONE' | 'NORMAL' | 'EXPIRING' | 'EXPIRED';
export type ContractCurrency = 'CNY' | 'USD' | 'EUR' | 'HKD';

export interface ContractPayload {
  code: string;
  name: string;
  contractType: ContractType;
  counterpartyName: string;
  supplierId?: number;
  ownerUserId: number;
  amount: number;
  currency: ContractCurrency;
  signedDate?: string;
  startDate: string;
  endDate: string;
  summary?: string;
  version?: number;
}

export interface BusinessContract extends ContractPayload {
  id: number;
  supplierLabel?: string;
  ownerLabel: string;
  paidAmount: number;
  status: ContractStatus;
  fulfillmentStatus: FulfillmentStatus;
  expiryState: ContractExpiryState;
  daysUntilExpiry: number;
  reminderCount: number;
  lastRemindedAt?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  canManage: boolean;
  allowedTransitions: ContractStatus[];
  allowedFulfillmentStatuses: FulfillmentStatus[];
  canRecordPayment: boolean;
  canRemind: boolean;
}

export interface ContractEvent {
  id: number;
  eventType: 'CREATED' | 'UPDATED' | 'STATUS_CHANGED' | 'FULFILLMENT_CHANGED' | 'PAYMENT_RECORDED' | 'EXPIRY_REMINDER';
  fromValue?: string;
  toValue?: string;
  amount?: number;
  detail?: string;
  operatorLabel: string;
  createdAt: string;
}

export interface ContractDetail {
  contract: BusinessContract;
  events: ContractEvent[];
}

export interface ContractOption {
  id: number;
  label: string;
  secondary?: string;
}

export interface ContractPage {
  records: BusinessContract[];
  total: number;
  page: number;
  size: number;
  stats: { total: number; active: number; expiring: number; expired: number; outstandingAmount: number };
  canManage: boolean;
}

export const contractApi = {
  list: (params: Record<string, string | number | undefined>) =>
    request<ContractPage>(`/contracts${queryString(params)}`),
  detail: (id: number) => request<ContractDetail>(`/contracts/${id}`),
  options: () => request<{ owners: ContractOption[]; suppliers: ContractOption[] }>('/contracts/options'),
  create: (payload: ContractPayload) =>
    request<BusinessContract>('/contracts', { method: 'POST', body: JSON.stringify(payload) }),
  update: (id: number, payload: ContractPayload) =>
    request<BusinessContract>(`/contracts/${id}`, { method: 'PUT', body: JSON.stringify(payload) }),
  updateStatus: (id: number, status: ContractStatus, version: number, reason?: string) =>
    request<BusinessContract>(`/contracts/${id}/status`, {
      method: 'POST', body: JSON.stringify({ status, version, reason }),
    }),
  updateFulfillment: (id: number, status: FulfillmentStatus, version: number, reason?: string) =>
    request<BusinessContract>(`/contracts/${id}/fulfillment`, {
      method: 'POST', body: JSON.stringify({ status, version, reason }),
    }),
  recordPayment: (id: number, amount: number, paymentDate: string, reference: string, version: number, note?: string) =>
    request<BusinessContract>(`/contracts/${id}/payments`, {
      method: 'POST', body: JSON.stringify({ amount, paymentDate, reference, version, note }),
    }),
  remind: (id: number, version: number) => request<BusinessContract>(`/contracts/${id}/reminders`, {
    method: 'POST', body: JSON.stringify({ version }),
  }),
};
