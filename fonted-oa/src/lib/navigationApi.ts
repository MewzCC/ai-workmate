export interface NavigationRoute {
  routeKey: string;
  parentKey?: string;
  name: string;
  path?: string;
  icon?: string;
  routeType: 'GROUP' | 'MENU' | 'PAGE';
  componentKey?: 'DASHBOARD' | 'AI_WORKSPACE' | 'ACCESS_CONTROL';
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
  });
  const result = await response.json().catch(() => null) as ApiResult<NavigationRoute[]> | null;
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  if (!response.ok || !result || result.code !== 200 || !result.data) {
    throw new Error(result?.message || '导航菜单加载失败');
  }
  return result.data;
}
