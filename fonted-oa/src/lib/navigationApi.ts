import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';

export interface NavigationRoute {
  routeKey: string;
  parentKey?: string;
  name: string;
  path?: string;
  icon?: string;
  routeType: 'GROUP' | 'MENU' | 'PAGE';
  componentKey?: 'DASHBOARD' | 'AI_WORKSPACE' | 'ACCESS_CONTROL'
    | 'AI_TASK_CENTER'
    | 'TODO_LIST' | 'LEAVE_FORM' | 'MY_APPLICATIONS' | 'AUDIT_CENTER'
    | 'APPROVAL_LIST' | 'APPROVAL_START' | 'APPROVAL_FORM' | 'FORM_ENGINE' | 'PROCESS_CONFIG' | 'APPROVAL_RULES'
    | 'ORG_TREE' | 'KNOWLEDGE_BASE' | 'MESSAGE_CENTER' | 'SYSTEM_CONFIG'
    | 'ATTENDANCE_CLOCK' | 'ATTENDANCE_EXCEPTION' | 'ATTENDANCE_REISSUE'
    | 'ATTENDANCE_STATISTICS' | 'ATTENDANCE_SETTINGS'
    | 'EMPLOYEE_FILES' | 'EMPLOYEE_CHANGE'
    | 'ASSET_LEDGER' | 'MEETING_ROOM' | 'VISITOR_BOOKING' | 'SEAL_USAGE';
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
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  if (!response.ok || !result || result.code !== 200 || !result.data) {
    throw new Error(result?.message || i18n.t('errors.navigation.loadFailed'));
  }
  return result.data;
}
