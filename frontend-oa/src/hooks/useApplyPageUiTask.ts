import { useEffect, useRef } from 'react';
import type { AgentTaskDetail } from '@/types/oa';
import { captureAuthDataScope } from '@/lib/authEvents';
import { isAgentTaskTerminal } from '@/lib/agentOperationState';
import { usePageUiCommandBridge } from '@/components/oa/PageUiCommandBridge';
import { message } from '@/lib/antdMessage';
import i18n from '@/i18n';

/** Only a task observed in progress may change the page; restoring completed history is read-only. */
export function useApplyPageUiTask(detail?: AgentTaskDetail) {
  const bridge = usePageUiCommandBridge();
  const pending = useRef<{ taskId: string; current: () => boolean } | null>(null);
  useEffect(() => {
    if (!detail || !bridge) { pending.current = null; return; }
    if (!isAgentTaskTerminal(detail.status)) {
      if (pending.current?.taskId !== detail.taskId)
        pending.current = { taskId: detail.taskId, current: captureAuthDataScope() };
      return;
    }
    const observed = pending.current;
    pending.current = null;
    if (detail.status !== 'SUCCEEDED' || observed?.taskId !== detail.taskId || !observed.current()) return;
    void bridge.applyTask(detail).then(changed => {
      if (changed && observed.current()) message.success(i18n.t('oa.ai.uiApplied'));
    }).catch(error => {
      if (observed.current()) message.error(error instanceof Error ? error.message : i18n.t('oa.ai.uiNotApplied'));
    });
  }, [bridge, detail]);
}
