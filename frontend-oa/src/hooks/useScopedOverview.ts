import { useCallback, useEffect, useMemo, useRef, useState, type SetStateAction } from 'react';
import { useAuth } from '@/components/auth/AuthProvider';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import { captureAuthDataScope } from '@/lib/authEvents';
import { useAsyncActionGuard } from './useAsyncActionGuard';

/** Reuses authenticated overview loading without retaining another user's or permission version's data. */
export function useScopedOverview<T>(query: () => Promise<T>) {
  const { user } = useAuth();
  const identity = JSON.stringify([user?.tenantId, user?.id, user?.permissionVersion,
    [...(user?.permissions ?? [])].sort(), [...(user?.roles ?? [])].sort(), [...(user?.dataScopes ?? [])].sort()]);
  const queryScope = useRef({ query, identity, revision: 0 });
  if (queryScope.current.query !== query || queryScope.current.identity !== identity)
    queryScope.current = { query, identity, revision: queryScope.current.revision + 1 };
  const scope = `${identity}:${queryScope.current.revision}`;
  const { capture } = useAsyncActionGuard(scope);
  const guard = useMemo(() => ({ capture, authCurrent: captureAuthDataScope() }), [capture]);
  // Scope ownership survives StrictMode's effect replay; each request still has its own ticket.
  const isCurrent = useCallback(() => guard.capture()() && guard.authCurrent(), [guard]);
  const request = useRef(0);
  const [snapshot, setSnapshot] = useState<{ scope: string; value: T | null }>();
  const [loadingState, setLoading] = useState({ scope, busy: true });
  const [failure, setFailure] = useState<{ scope: string; error: unknown }>();
  const setData = useCallback((next: SetStateAction<T | null>) => {
    if (!isCurrent()) return;
    setSnapshot(previous => {
      if (!isCurrent()) return previous;
      const value = previous?.scope === scope ? previous.value : null;
      return { scope, value: typeof next === 'function' ? (next as (previous: T | null) => T | null)(value) : next };
    });
  }, [isCurrent, scope]);
  const load = useCallback(async () => {
    if (!isCurrent()) return null;
    const ticket = ++request.current;
    if (!user?.id) { setData(null); setLoading({ scope, busy: false }); return null; }
    setLoading({ scope, busy: true });
    setFailure(undefined);
    try {
      const value = await query();
      if (ticket !== request.current || !isCurrent()) return null;
      setData(value); return value;
    } catch (error) {
      if (ticket === request.current && isCurrent()) { setData(null); setFailure({ scope, error }); message.error(formatOaApiError(error)); }
      return null;
    } finally { if (ticket === request.current && isCurrent()) setLoading({ scope, busy: false }); }
  }, [query, isCurrent, setData, user?.id, scope]);
  useEffect(() => {
    void load();
    return () => { request.current += 1; };
  }, [load]);
  return { data: snapshot?.scope === scope ? snapshot.value : null, setData,
    loading: loadingState.scope === scope ? loadingState.busy : Boolean(user?.id),
    error: failure?.scope === scope ? failure.error : undefined, load, scope, isCurrent };
}
