import type { AgentTaskDetail } from '@/types/oa';
import i18n from '@/i18n';
import { agentTaskApi, OaApiError } from './oaApi';

export function canCancelAgentTask(status?: string | null): boolean {
  return status === 'PLAN_READY' || status === 'WAITING_CONFIRMATION' || status === 'QUEUED';
}

function requireSameTask(detail: AgentTaskDetail, taskId: string): void {
  const knownStatus = detail && ['RECEIVED', 'PLANNING', 'PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED', 'RUNNING',
    'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED'].includes(detail.status);
  if (!detail || detail.taskId !== taskId || !knownStatus || !Array.isArray(detail.steps)) {
    throw new Error(i18n.t('oa.ai.cancelUnconfirmed'));
  }
}

/** One cancellation command, optionally reconciled by one safe read; never retries the command. */
export async function cancelAgentTask(taskId: string): Promise<{ cancelled: boolean; detail: AgentTaskDetail }> {
  let detail: AgentTaskDetail;
  try {
    detail = await agentTaskApi.cancel(taskId);
  } catch (error) {
    if (error instanceof OaApiError && [401, 403, 404].includes(error.status)) throw error;
    const current = await agentTaskApi.detail(taskId);
    requireSameTask(current, taskId);
    if (canCancelAgentTask(current.status)) throw error;
    return { cancelled: current.status === 'CANCELLED', detail: current };
  }
  requireSameTask(detail, taskId);
  if (detail.status !== 'CANCELLED') throw new Error(i18n.t('oa.ai.cancelUnconfirmed'));
  return { cancelled: true, detail };
}
