import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { formatOaApiError } from '@/lib/oaApi';
import { useAsyncActionGuard } from './useAsyncActionGuard';

/** Fixed page route and authenticated loader; stale responses cannot cross account or permission changes. */
export function useBusinessRecordDetail<T extends { id: number }>({ scope, allowed, pageId, loadRecord }: {
  scope: string; allowed: boolean; pageId: string; loadRecord: (id: number) => Promise<T>;
}) {
  const router = useRouter();
  const requestedId = useSearchParams().get('id');
  const request = useRef(0);
  const { capture } = useAsyncActionGuard(scope);
  const [state, setState] = useState<{ scope: string; id: number; loading: boolean; data?: T; error?: string }>();
  const open = useCallback(async (id: number) => {
    const current = capture();
    if (!allowed || !current() || !Number.isSafeInteger(id) || id < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
    const ticket = ++request.current;
    setState({ scope, id, loading: true });
    try {
      const data = await loadRecord(id);
      if (ticket === request.current && current()) {
        if (data.id !== id) throw new Error('PAGE_UI_FIELD_REJECTED');
        setState({ scope, id, loading: false, data });
      }
    } catch (cause) {
      if (ticket === request.current && current()) setState({ scope, id, loading: false, error: formatOaApiError(cause) });
    }
  }, [allowed, capture, loadRecord, scope]);
  useEffect(() => {
    const activeRequest = request;
    if (requestedId && /^[1-9]\d*$/.test(requestedId) && Number.isSafeInteger(Number(requestedId)) && allowed) {
      void open(Number(requestedId));
    }
    return () => { activeRequest.current++; };
  }, [requestedId, allowed, open]);
  const close = () => {
    request.current++;
    setState(undefined);
    if (requestedId !== null) router.replace(`/oa/${pageId}`);
  };
  const visible = allowed && state?.scope === scope;
  return { open, close, visible, loading: visible && Boolean(state?.loading), data: visible ? state?.data : undefined,
    error: visible ? state?.error : undefined, retry: () => state ? open(state.id) : Promise.resolve() };
}
