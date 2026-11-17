import { useCallback, useEffect, useRef } from 'react';

/** Local stale-response guard. Invalidating a view never cancels or rolls back a server operation. */
export function useAsyncActionGuard(scope: string) {
  const currentScope = useRef(scope);
  const scopeRevision = useRef(0);
  const revision = useRef(0);
  if (currentScope.current !== scope) {
    currentScope.current = scope;
    scopeRevision.current++;
    revision.current++;
  }
  const ownerRevision = scopeRevision.current;
  const mounted = useRef(true);
  useEffect(() => {
    const lifecycleRevision = revision;
    mounted.current = true;
    return () => { mounted.current = false; lifecycleRevision.current++; };
  }, []);
  const invalidate = useCallback(() => { revision.current++; }, []);
  const capture = useCallback(() => {
    const ticket = revision.current;
    return () => mounted.current && currentScope.current === scope
      && scopeRevision.current === ownerRevision && revision.current === ticket;
  }, [scope, ownerRevision]);
  return { capture, invalidate };
}
