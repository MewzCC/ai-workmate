import { useCallback, useMemo } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';

export type PermissionMatchMode = 'ANY' | 'ALL';

export function usePermission(required: string | readonly string[], mode: PermissionMatchMode = 'ALL') {
  const { user, refresh } = useAuth();
  const requiredCodes = useMemo(
    () => (Array.isArray(required) ? [...required] : [required]).filter(Boolean),
    [required],
  );
  const permissionSet = useMemo(() => new Set(user?.permissions ?? []), [user?.permissions]);
  const allowed = requiredCodes.length === 0
    || (mode === 'ANY'
      ? requiredCodes.some((code) => permissionSet.has(code))
      : requiredCodes.every((code) => permissionSet.has(code)));

  const hasPermission = useCallback(
    (code: string) => permissionSet.has(code),
    [permissionSet],
  );

  return {
    allowed: Boolean(user) && allowed,
    hasPermission,
    permissions: permissionSet,
    permissionVersion: user?.permissionVersion,
    refreshPermissions: refresh,
  };
}
