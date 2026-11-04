import type { AgentTaskDetail } from '@/types/oa';
import i18n from '@/i18n';
import { agentTaskApi, OaApiError } from './oaApi';
import { AuthResponseScopeError, captureAuthDataScope } from './authEvents';

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
  const current = captureAuthDataScope();
  let detail: AgentTaskDetail;
  try {
    detail = await agentTaskApi.cancel(taskId);
  } catch (error) {
    if (!current()) throw new AuthResponseScopeError();
    if (error instanceof OaApiError && [401, 403, 404].includes(error.status)) throw error;
    const reconciled = await agentTaskApi.detail(taskId);
    if (!current()) throw new AuthResponseScopeError();
    requireSameTask(reconciled, taskId);
    if (canCancelAgentTask(reconciled.status)) throw error;
    return { cancelled: reconciled.status === 'CANCELLED', detail: reconciled };
  }
  if (!current()) throw new AuthResponseScopeError();
  requireSameTask(detail, taskId);
  if (detail.status !== 'CANCELLED') throw new Error(i18n.t('oa.ai.cancelUnconfirmed'));
  return { cancelled: true, detail };
}
