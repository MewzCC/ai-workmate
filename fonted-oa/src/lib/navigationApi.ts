import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';
import type { ComponentKey } from '@/types/oa';
import { notifyAuthResponseStatus } from '@/lib/authEvents';

export interface NavigationRoute {
  routeKey: string;
  parentKey?: string;
  name: string;
  path?: string;
  icon?: string;
  routeType: 'GROUP' | 'MENU' | 'PAGE';
  componentKey?: ComponentKey;
  permissionCode?: string;
  sortOrder: number;
  children: NavigationRoute[];
}

interface ApiResult<T> {
  code: number;
  message: string;
  data: T | null;
}

export async function getNavigation(): Promise<NavigationRoute[]> {
  const response = await fetch('/api/navigation', {
    credentials: 'include',
    cache: 'no-store',
    headers: buildApiHeaders(false),
  });
  const result = await response.json().catch(() => null) as ApiResult<NavigationRoute[]> | null;
  notifyAuthResponseStatus(response.status);
  if (!response.ok || !result || result.code !== 200 || !result.data) {
    throw new Error(result?.message || i18n.t('errors.navigation.loadFailed'));
  }
  return result.data;
}
