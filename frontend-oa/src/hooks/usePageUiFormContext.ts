import { useCallback, useMemo, useState } from 'react';
import { uuid } from '@/lib/uuid';
import type { UiFormContext } from '@/lib/pageUiCommands';
import { usePublishPageAgentContext } from '@/components/oa/PageAgentContext';

/** A local form revision is a stale-action guard, never a credential or business authorization. */
export function usePageUiFormContext(open: boolean, recordId?: number, version?: number, scopeKey = '') {
  const [revision, setRevision] = useState(0);
  const renew = useCallback(() => setRevision((value) => value + 1), []);
  const context = useMemo<UiFormContext | null>(() => open ? {
    sessionId: uuid(), ...(recordId !== undefined ? { recordId, version } : {}),
  // Human edits and scope changes must renew the session even when the record/version is unchanged.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  } : null, [open, recordId, version, revision, scopeKey]);
  usePublishPageAgentContext({ uiFormSession: context?.sessionId, uiRecordId: context?.recordId, uiRecordVersion: context?.version });
  return { context, renew };
}
