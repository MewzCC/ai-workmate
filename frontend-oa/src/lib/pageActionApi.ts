import { request } from '@/lib/oaApi';

export type PageActionRisk = 'L0' | 'L1' | 'L2';

export interface PageActionItem {
  pageId: string;
  toolCode: string;
  name: string;
  description: string;
  riskLevel: PageActionRisk;
  sideEffect: 'NONE' | 'SINGLE_WRITE';
  confirmationPolicy: 'NONE' | 'EXPLICIT' | 'SECONDARY';
  requiredPermissions: string[];
  enabled: boolean;
  explicitlyConfigured: boolean;
  version: number;
}

export interface PageActionOverview {
  pages: Array<{ pageId: string; actions: PageActionItem[] }>;
  total: number;
  enabled: number;
  disabled: number;
  canManage: boolean;
}

export const pageActionApi = {
  overview: () => request<PageActionOverview>('/admin/page-actions'),
  update: (action: PageActionItem, enabled: boolean, reason: string) =>
    request<PageActionOverview>(
      `/admin/page-actions/${encodeURIComponent(action.pageId)}/${encodeURIComponent(action.toolCode)}`,
      {
        method: 'PUT',
        body: JSON.stringify({ enabled, version: action.version, reason }),
      },
    ),
};
