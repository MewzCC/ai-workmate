import { useCallback, useEffect, useRef, useState } from 'react';
import { useAsyncActionGuard } from './useAsyncActionGuard';

export function isFormValidationError(error: unknown): boolean {
  return (error instanceof Error && error.name === 'ValidationError')
    || (error !== null && typeof error === 'object' && 'errorFields' in error && Array.isArray(error.errorFields));
}

/** A local form instance and submission lock, never a server cancellation or authorization. */
export function useAsyncFormAction(scope: string) {
  const { capture, invalidate } = useAsyncActionGuard(scope);
  const locked = useRef(false);
  const [busy, setBusy] = useState(false);
  const reset = useCallback(() => { invalidate(); locked.current = false; setBusy(false); }, [invalidate]);
  useEffect(reset, [scope, reset]);
  const begin = () => {
    const current = capture();
    if (!current() || locked.current) return;
    locked.current = true; setBusy(true);
    return { current, finish: () => { if (current()) { locked.current = false; setBusy(false); } } };
  };
  return { busy, begin, reset };
}
