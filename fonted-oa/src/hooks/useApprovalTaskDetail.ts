import { useCallback } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { todoApi } from '@/lib/oaApi';
import { useScopedOverview } from './useScopedOverview';

/** Both preview and route details bind their data to the requested node and authenticated scope. */
export function useApprovalTaskDetail(taskId: number | null, active = true) {
  const { user } = useAuth();
  const authenticated = Boolean(user?.id);
  const query = useCallback(async () => {
    if (!active || !authenticated) return null;
    if (typeof taskId !== 'number' || !Number.isSafeInteger(taskId) || taskId < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
    const [application, timeline] = await Promise.all([todoApi.detail(taskId), todoApi.timeline(taskId)]);
    if (application.taskId !== taskId || !Number.isSafeInteger(application.id) || application.id < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
    return { application, timeline };
  }, [active, taskId, authenticated]);
  const overview = useScopedOverview(query);
  return { ...overview, application: active ? overview.data?.application : undefined, timeline: active ? overview.data?.timeline ?? [] : [] };
}
