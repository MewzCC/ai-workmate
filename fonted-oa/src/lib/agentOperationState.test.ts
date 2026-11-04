import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { AgentTaskDetail, AgentTaskStatus } from '@/types/oa';
const api = vi.hoisted(() => ({ detail: vi.fn() }));
vi.mock('./oaApi', async original => ({ ...await original<typeof import('./oaApi')>(), agentTaskApi: api }));
import { AgentObservationPending, observeAgentTask } from './agentOperationState';
import { bindAuthResponseIdentity, invalidateAuthResponses } from './authEvents';

const task: AgentTaskDetail = {
  taskId: 'synthetic-task', pageId: 'todo', input: 'Synthetic query', pageContext: {}, plan: {},
  planHash: null, planVersion: 1, riskLevel: 'L0', status: 'RUNNING', steps: [], timeoutAt: null,
  createdAt: '', updatedAt: '', finishedAt: null, errorCode: null,
};
beforeEach(() => {
  vi.resetAllMocks(); vi.useFakeTimers(); vi.setSystemTime(new Date('2026-11-02T00:00:00Z'));
  invalidateAuthResponses(); bindAuthResponseIdentity('synthetic:actor-a', 'initial');
});
afterEach(() => { vi.useRealTimers(); invalidateAuthResponses(); });

it.each<AgentTaskStatus>(['SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED'])(
'returns the real %s terminal state without another operation', async status => {
  const detail = { ...task, status }; api.detail.mockResolvedValueOnce(detail);
  const update = vi.fn();
  expect(await observeAgentTask(task.taskId, new AbortController().signal, update)).toBe(detail);
  expect(api.detail).toHaveBeenCalledTimes(1); expect(update).toHaveBeenCalledWith(detail);
});

it.each(['identity', 'permissions'])('rejects a delayed observation after %s changes without publishing it', async change => {
  let resolve!: (detail: AgentTaskDetail) => void;
  api.detail.mockReturnValueOnce(new Promise(done => { resolve = done; }));
  const update = vi.fn(), pending = observeAgentTask(task.taskId, new AbortController().signal, update);
  const assertion = expect(pending).rejects.toMatchObject({ code: 'AUTH_SCOPE_CHANGED' });
  bindAuthResponseIdentity(change === 'identity' ? 'synthetic:actor-b' : 'synthetic:actor-a', 'changed');
  resolve(task); await assertion;
  expect(update).not.toHaveBeenCalled(); expect(api.detail).toHaveBeenCalledTimes(1);
});

it('does not poll the previous task again when identity changes between observations', async () => {
  api.detail.mockResolvedValue(task);
  const update = vi.fn(() => bindAuthResponseIdentity('synthetic:actor-b'));
  const pending = observeAgentTask(task.taskId, new AbortController().signal, update);
  const assertion = expect(pending).rejects.toMatchObject({ code: 'AUTH_SCOPE_CHANGED' });
  await vi.advanceTimersByTimeAsync(1000); await assertion;
  expect(api.detail).toHaveBeenCalledTimes(1); expect(update).toHaveBeenCalledTimes(1);
});

it('preserves an active task as pending when the server deadline plus observation grace elapses', async () => {
  const detail = { ...task, timeoutAt: new Date(Date.now() - 10_000).toISOString() };
  api.detail.mockResolvedValueOnce(detail);
  const pending = observeAgentTask(task.taskId, new AbortController().signal, vi.fn());
  await expect(pending).rejects.toBeInstanceOf(AgentObservationPending);
  await expect(pending).rejects.toMatchObject({ detail }); expect(api.detail).toHaveBeenCalledTimes(1);
});

it('stops observing at the local budget without inventing a failed task or retrying execution', async () => {
  api.detail.mockResolvedValue(task);
  const pending = observeAgentTask(task.taskId, new AbortController().signal, vi.fn());
  const assertion = expect(pending).rejects.toMatchObject({ detail: task });
  await vi.advanceTimersByTimeAsync(135_000); await assertion;
  expect(api.detail).toHaveBeenCalledTimes(136);
});

it('distinguishes user observation abort from a changed authorization scope', async () => {
  api.detail.mockResolvedValue(task);
  const controller = new AbortController(); controller.abort();
  await expect(observeAgentTask(task.taskId, controller.signal, vi.fn())).rejects.toMatchObject({ name: 'AbortError' });
  expect(api.detail).not.toHaveBeenCalled();
});

it('rejects another task instead of presenting its state', async () => {
  api.detail.mockResolvedValue({ ...task, taskId: 'other-task' });
  const update = vi.fn();
  await expect(observeAgentTask(task.taskId, new AbortController().signal, update)).rejects.toThrow();
  expect(update).not.toHaveBeenCalled(); expect(api.detail).toHaveBeenCalledTimes(1);
});
