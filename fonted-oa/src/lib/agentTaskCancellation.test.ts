import { beforeEach, expect, it, vi } from 'vitest';
import type { AgentTaskDetail } from '@/types/oa';
const api = vi.hoisted(() => ({ cancel: vi.fn(), detail: vi.fn() }));
vi.mock('./oaApi', async (original) => ({ ...await original<typeof import('./oaApi')>(), agentTaskApi: api }));
import { OaApiError } from './oaApi';
import { canCancelAgentTask, cancelAgentTask } from './agentTaskCancellation';
const taskId = 'isolated-task';
const detail: AgentTaskDetail = {
  taskId, pageId: 'todo', input: 'synthetic read', pageContext: {}, plan: {}, planHash: null, planVersion: 1,
  riskLevel: 'L0', status: 'CANCELLED', steps: [], timeoutAt: null,
  createdAt: '2026-10-25T09:00:00', updatedAt: '2026-10-25T09:01:00', finishedAt: '2026-10-25T09:01:00', errorCode: null,
};
beforeEach(() => { vi.resetAllMocks(); });

it('only permits cancellation before execution', () => {
  expect(['PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED'].map(canCancelAgentTask)).toEqual([true, true, true]);
  expect(['RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED', undefined].map(canCancelAgentTask)).toEqual([false, false, false, false, false]);
});
it('accepts a matching confirmed cancellation without another read', async () => {
  api.cancel.mockResolvedValue(detail);
  expect(await cancelAgentTask(taskId)).toEqual({ cancelled: true, detail });
  expect(api.cancel).toHaveBeenCalledOnce(); expect(api.detail).not.toHaveBeenCalled();
});
it.each(['RUNNING', 'SUCCEEDED'])('reconciles a cancellation race with the actual %s state', async (status) => {
  api.cancel.mockRejectedValue(new OaApiError('state changed', 409, 'INVALID_TASK_STATE'));
  api.detail.mockResolvedValue({ ...detail, status });
  expect(await cancelAgentTask(taskId)).toEqual({ cancelled: false, detail: { ...detail, status } });
  expect(api.cancel).toHaveBeenCalledOnce(); expect(api.detail).toHaveBeenCalledOnce();
});
it('confirms a cancelled response lost in transit through one safe read', async () => {
  api.cancel.mockRejectedValue(new Error('network interrupted')); api.detail.mockResolvedValue(detail);
  expect((await cancelAgentTask(taskId)).cancelled).toBe(true);
  expect(api.cancel).toHaveBeenCalledOnce();
});
it('keeps a cancellable task unchanged when a failed command has not applied', async () => {
  const failure = new Error('network interrupted');
  api.cancel.mockRejectedValue(failure); api.detail.mockResolvedValue({ ...detail, status: 'PLAN_READY' });
  await expect(cancelAgentTask(taskId)).rejects.toBe(failure);
  expect(api.cancel).toHaveBeenCalledOnce();
});
it.each([401, 403, 404])('does not follow an authorization or ownership denial with another request: %i', async (status) => {
  const failure = new OaApiError('denied', status, 'DENIED'); api.cancel.mockRejectedValue(failure);
  await expect(cancelAgentTask(taskId)).rejects.toBe(failure);
  expect(api.detail).not.toHaveBeenCalled();
});
it.each([
  { ...detail, taskId: 'other-task' },
  { ...detail, status: 'RUNNING' },
])('does not treat an invalid success response as cancellation', async (response) => {
  api.cancel.mockResolvedValue(response);
  await expect(cancelAgentTask(taskId)).rejects.toThrow();
});
it('rejects another task returned during reconciliation', async () => {
  api.cancel.mockRejectedValue(new Error('network')); api.detail.mockResolvedValue({ ...detail, taskId: 'other-task' });
  await expect(cancelAgentTask(taskId)).rejects.toThrow();
});
it.each([{ ...detail, status: 'UNKNOWN' }, { ...detail, steps: undefined }])('rejects malformed reconciliation detail', async (current) => {
  api.cancel.mockRejectedValue(new Error('network')); api.detail.mockResolvedValue(current);
  await expect(cancelAgentTask(taskId)).rejects.toThrow();
});
