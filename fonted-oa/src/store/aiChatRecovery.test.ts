import { beforeEach, expect, it, vi } from 'vitest';
import type { AgentTaskDetail, AgentTaskStatus } from '@/types/oa';
const api = vi.hoisted(() => ({ latestConversationAgentTask: vi.fn(), appendAgentResult: vi.fn(), listMessages: vi.fn(),
  listConversations: vi.fn(), detail: vi.fn(), cancel: vi.fn(), executeAiTask: vi.fn(), issueAiTaskConfirmation: vi.fn(), planAiTask: vi.fn() }));
vi.mock('@/lib/chatApi', async original => ({ ...await original<typeof import('@/lib/chatApi')>(),
  latestConversationAgentTask: api.latestConversationAgentTask, appendAgentResult: api.appendAgentResult,
  listMessages: api.listMessages, listConversations: api.listConversations }));
vi.mock('@/lib/oaApi', async original => ({ ...await original<typeof import('@/lib/oaApi')>(),
  agentTaskApi: { detail: api.detail, cancel: api.cancel }, executeAiTask: api.executeAiTask,
  issueAiTaskConfirmation: api.issueAiTaskConfirmation, planAiTask: api.planAiTask }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: vi.fn(), info: vi.fn(), warning: vi.fn() } }));
import { bindChatIdentity, useAiChatStore } from './aiChatStore';
import { restoreAgentPlan } from '@/lib/agentOperationState';

function task(status: AgentTaskStatus): AgentTaskDetail {
  return { taskId: 'synthetic-task', pageId: 'ai-workspace', input: 'Synthetic request', pageContext: {}, plan: {},
    planHash: 'synthetic-hash', planVersion: 1, riskLevel: 'L2', status, timeoutAt: null,
    createdAt: '', updatedAt: '', finishedAt: null, errorCode: null,
    steps: [{ sequence: 1, toolCode: 'leave.apply', arguments: {}, riskLevel: 'L2',
      status: 'PENDING', result: null, resultSummary: null, errorCode: null, startedAt: null, finishedAt: null }] };
}
beforeEach(() => {
  vi.resetAllMocks(); bindChatIdentity(null); bindChatIdentity('synthetic:recovery');
  api.listConversations.mockResolvedValue([]); api.listMessages.mockResolvedValue([]); api.appendAgentResult.mockResolvedValue('Synthetic receipt');
});
function noNewBusinessCommands() {
  expect(api.executeAiTask).not.toHaveBeenCalled(); expect(api.issueAiTaskConfirmation).not.toHaveBeenCalled();
  expect(api.planAiTask).not.toHaveBeenCalled(); expect(api.cancel).not.toHaveBeenCalled();
}
it('restores the original pending confirmation without issuing credentials or submitting it', async () => {
  api.latestConversationAgentTask.mockResolvedValue(task('WAITING_CONFIRMATION'));
  await useAiChatStore.getState().resumeOperation(3);
  expect(useAiChatStore.getState().pendingPlans[3]).toMatchObject({ taskId: 'synthetic-task', confirmationRequired: true });
  expect(api.appendAgentResult).not.toHaveBeenCalled(); noNewBusinessCommands();
});
it.each<AgentTaskStatus>(['SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED'])(
  'restores the real %s state and persists its receipt without re-execution', async status => {
    api.latestConversationAgentTask.mockResolvedValue(task(status));
    useAiChatStore.setState({ pendingPlans: { 3: restoreAgentPlan(task('WAITING_CONFIRMATION')) } });
    await useAiChatStore.getState().resumeOperation(3);
    expect(useAiChatStore.getState().operationTasks[3].status).toBe(status);
    expect(useAiChatStore.getState().pendingPlans[3]).toBeUndefined();
    expect(api.appendAgentResult).toHaveBeenCalledTimes(1);
    expect(api.appendAgentResult).toHaveBeenCalledWith(3, 'synthetic-task'); noNewBusinessCommands();
  });
it('observes an accepted queued task instead of enqueueing another task', async () => {
  api.latestConversationAgentTask.mockResolvedValue(task('QUEUED')); api.detail.mockResolvedValue(task('SUCCEEDED'));
  await useAiChatStore.getState().resumeOperation(3);
  expect(api.detail).toHaveBeenCalledTimes(1); expect(api.detail).toHaveBeenCalledWith('synthetic-task');
  expect(useAiChatStore.getState().operationTasks[3].status).toBe('SUCCEEDED'); noNewBusinessCommands();
});
it('keeps the real terminal state visible when receipt persistence fails', async () => {
  api.latestConversationAgentTask.mockResolvedValue(task('PARTIALLY_SUCCEEDED'));
  api.appendAgentResult.mockRejectedValue(new Error('Synthetic unavailable response'));
  await useAiChatStore.getState().resumeOperation(3);
  expect(useAiChatStore.getState().operationTasks[3].status).toBe('PARTIALLY_SUCCEEDED');
  expect(useAiChatStore.getState().generatingIds).toEqual([]); noNewBusinessCommands();
});
