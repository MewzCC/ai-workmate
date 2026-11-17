import { queryString, request } from '@/lib/oaApi';

export interface TenantConfiguration {
  id: number;
  tenantName: string;
  tenantShortName?: string;
  locale: 'zh-CN' | 'en-US';
  timezone: string;
  fiscalYearStartMonth: number;
  approvalEnabled: boolean;
  attendanceEnabled: boolean;
  assetEnabled: boolean;
  meetingEnabled: boolean;
  visitorEnabled: boolean;
  sealEnabled: boolean;
  defaultApprovalDays: number;
  expenseCurrency: 'CNY' | 'USD' | 'EUR' | 'HKD';
  passwordMinLength: number;
  sessionTimeoutMinutes: number;
  version: number;
  updatedAt: string;
  canManage: boolean;
}

export type TenantProfilePayload = Pick<TenantConfiguration,
  'tenantName' | 'tenantShortName' | 'locale' | 'timezone' | 'version'>;
export type TenantFeaturesPayload = Pick<TenantConfiguration,
  'approvalEnabled' | 'attendanceEnabled' | 'assetEnabled' | 'meetingEnabled' |
  'visitorEnabled' | 'sealEnabled' | 'version'>;
export type TenantBusinessPayload = Pick<TenantConfiguration,
  'fiscalYearStartMonth' | 'defaultApprovalDays' | 'expenseCurrency' | 'version'>;
export type TenantSecurityPayload = Pick<TenantConfiguration,
  'passwordMinLength' | 'sessionTimeoutMinutes' | 'version'>;

export interface TenantConfigurationHistory {
  id: number;
  category: 'PROFILE' | 'FEATURES' | 'BUSINESS' | 'SECURITY';
  version: number;
  changedBy: number;
  createdAt: string;
}

export const tenantConfigApi = {
  get: () => request<TenantConfiguration>('/admin/tenant-config'),
  updateProfile: (payload: TenantProfilePayload) => request<TenantConfiguration>('/admin/tenant-config/profile', {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  updateFeatures: (payload: TenantFeaturesPayload) => request<TenantConfiguration>('/admin/tenant-config/features', {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  updateBusiness: (payload: TenantBusinessPayload) => request<TenantConfiguration>('/admin/tenant-config/business', {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  updateSecurity: (payload: TenantSecurityPayload) => request<TenantConfiguration>('/admin/tenant-config/security', {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  history: (page = 1, size = 20) => request<{ records: TenantConfigurationHistory[]; total: number; page: number; size: number }>(
    `/admin/tenant-config/history${queryString({ page, size })}`,
  ),
};
