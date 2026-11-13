import type { AgentTaskDetail, AgentTaskStatus, AiTaskExecuteResponse, AiTaskPlanResponse } from '@/types/oa';
import i18n from '@/i18n';
import { agentTaskApi } from './oaApi';
import { agentToolTranslationKey } from './agentToolPresentation';
import { AuthResponseScopeError, captureAuthDataScope } from './authEvents';

const TERMINAL: readonly AgentTaskStatus[] = ['SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED'];
export const isAgentTaskTerminal = (status: AgentTaskStatus) => TERMINAL.includes(status);

export class AgentObservationPending extends Error {
  constructor(readonly detail: AgentTaskDetail) { super(i18n.t('chat.operationTimeout')); }
}

/** UI snapshot of the server's accepted enqueue response; contains no invented business result. */
export function acceptedAgentTask(plan: AiTaskPlanResponse, accepted: AiTaskExecuteResponse): AgentTaskDetail {
  if (accepted.taskId !== plan.taskId || !['QUEUED', 'RUNNING'].includes(accepted.status))
    throw new Error(i18n.t('chat.operationStateInvalid'));
  return { taskId: plan.taskId, pageId: 'ai-workspace', input: '', pageContext: {}, plan,
    planHash: plan.planHash, planVersion: plan.planVersion, riskLevel: plan.riskLevel, status: accepted.status,
    steps: plan.steps.map((step) => ({ ...step, riskLevel: plan.riskLevel, status: 'PENDING',
      result: null, resultSummary: null, errorCode: null, startedAt: null, finishedAt: null })),
    timeoutAt: null, createdAt: '', updatedAt: '', finishedAt: null, errorCode: null };
}

export function restoreAgentPlan(detail: AgentTaskDetail): AiTaskPlanResponse {
  if (!detail.planHash || !detail.planVersion || !detail.riskLevel || !detail.steps.length
      || !['PLAN_READY', 'WAITING_CONFIRMATION'].includes(detail.status)) throw new Error(i18n.t('chat.operationPlanUnavailable'));
  const raw = detail.plan && typeof detail.plan === 'object' ? detail.plan as Record<string, unknown> : {};
  return {
    taskId: detail.taskId, status: detail.status, planHash: detail.planHash, planVersion: detail.planVersion,
    riskLevel: detail.riskLevel, confirmationRequired: detail.riskLevel !== 'L0', expiresAt: detail.timeoutAt,
    summary: typeof raw.summary === 'string' ? raw.summary : i18n.t('chat.operationPlanTitle'),
    steps: detail.steps.map((step) => ({ sequence: step.sequence, toolCode: step.toolCode, arguments: step.arguments,
      title: i18n.t(`aiPermission.tools.${agentToolTranslationKey(step.toolCode)}.name`, { defaultValue: step.toolCode }) })),
  };
}

/** Observes an already-owned task only. Expired local waiting never means a failed business write. */
export async function observeAgentTask(taskId: string, signal: AbortSignal,
  onUpdate: (detail: AgentTaskDetail) => void): Promise<AgentTaskDetail> {
  const observationLimit = Date.now() + 135_000;
  const current = captureAuthDataScope();
  for (;;) {
    if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
    if (!current()) throw new AuthResponseScopeError();
    const detail = await agentTaskApi.detail(taskId);
    if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
    if (!current()) throw new AuthResponseScopeError();
    if (detail.taskId !== taskId || !Array.isArray(detail.steps)) throw new Error(i18n.t('chat.operationStateInvalid'));
    onUpdate(detail);
    if (isAgentTaskTerminal(detail.status)) return detail;
    const serverDeadline = detail.timeoutAt ? Date.parse(detail.timeoutAt) : NaN;
    const until = Number.isFinite(serverDeadline) ? Math.min(observationLimit, serverDeadline + 10_000) : observationLimit;
    if (Date.now() >= until) throw new AgentObservationPending(detail);
    await new Promise<void>((resolve) => {
      const finish = () => { window.clearTimeout(timer); signal.removeEventListener('abort', finish); resolve(); };
      const timer = window.setTimeout(finish, 1000);
      signal.addEventListener('abort', finish, { once: true });
      if (signal.aborted) finish();
    });
  }
}
