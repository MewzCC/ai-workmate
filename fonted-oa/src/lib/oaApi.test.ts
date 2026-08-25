import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  agentTaskApi,
  executeAiTask,
  formatOaApiError,
  issueAiTaskConfirmation,
  OaApiError,
  planAiTask,
  subscribeAiTaskEvents,
} from './oaApi';

function result(data: unknown, status = 200): Response {
  return new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.restoreAllMocks());

describe('OA API error mapping', () => {
  it('preserves stable error code, status and trace id', () => {
    const error = new OaApiError('数据已更新', 409, 'VERSION_CONFLICT', 'request-1', 'trace-1');
    expect(error.status).toBe(409);
    expect(error.errorCode).toBe('VERSION_CONFLICT');
    expect(error.retryable).toBe(false);
    expect(formatOaApiError(error)).toContain('trace-1');
  });

  it('does not expose unknown error details', () => {
    expect(formatOaApiError(new Error('secret'))).toBe('请求失败，请稍后重试');
  });
});

describe('Phase 2 task API contracts', () => {
  it('uses authenticated cookie requests and independent idempotency keys', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ taskId: 'agt_task', status: 'PLAN_READY' }))
      .mockResolvedValueOnce(result({ taskId: 'agt_task', status: 'QUEUED' }, 202));

    await planAiTask({ input: '查询待办', pageId: 'todo-list' }, 'plan-key');
    await executeAiTask('agt_task', { planVersion: 1, planHash: 'sha256:plan' }, 'execute-key');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/ai/tasks/plan');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: 'include', cache: 'no-store' }));
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual(expect.objectContaining({
      'Idempotency-Key': 'plan-key',
      'Accept-Language': 'zh-CN',
    }));
    expect(fetchMock.mock.calls[1][0]).toBe('/api/ai/tasks/agt_task/execute');
    expect(fetchMock.mock.calls[1][1]?.headers).toEqual(expect.objectContaining({ 'Idempotency-Key': 'execute-key' }));
  });

  it('never puts the one-time confirmation credential in the URL', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ token: 'one-time-secret', expiresAt: '2026-08-25T12:10:00+08:00' }))
      .mockResolvedValueOnce(result({ taskId: 'agt_task', status: 'QUEUED' }, 202));

    const credential = await issueAiTaskConfirmation('agt_task', { planVersion: 2, planHash: 'sha256:bound-plan' });
    await executeAiTask('agt_task', {
      planVersion: 2,
      planHash: 'sha256:bound-plan',
      confirmationToken: credential.token,
    }, 'execute-key');

    expect(String(fetchMock.mock.calls[0][0])).not.toContain('one-time-secret');
    expect(String(fetchMock.mock.calls[1][0])).not.toContain('one-time-secret');
    expect(fetchMock.mock.calls[1][1]?.body).toContain('one-time-secret');
  });

  it('reads authenticated SSE events, deduplicates ids and stops on terminal status', async () => {
    const stream = [
      'id: 41\nevent: snapshot\ndata: {"status":"QUEUED"}\n\n',
      'id: 41\nevent: snapshot\ndata: {"status":"QUEUED"}\n\n',
      'id: 42\nevent: task-completed\ndata: {"status":"SUCCEEDED"}\n\n',
    ].join('');
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(stream, {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    }));
    const received: string[] = [];
    const onError = vi.fn();

    const unsubscribe = subscribeAiTaskEvents('agt_task', {
      onEvent: (event) => received.push(event.id),
      onError,
    });
    await vi.waitFor(() => expect(received).toEqual(['41', '42']));
    unsubscribe();

    expect(onError).not.toHaveBeenCalled();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/ai/tasks/agt_task/events');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: 'include', cache: 'no-store' }));
    expect(fetchMock.mock.calls[0][1]?.headers).toEqual(expect.objectContaining({
      Accept: 'text/event-stream',
      'Accept-Language': 'zh-CN',
    }));
    expect(String(fetchMock.mock.calls[0][0])).not.toContain('token');
  });

  it('resumes a disconnected stream with Last-Event-ID and ignores replayed events', async () => {
    const first = 'id: 51\nevent: snapshot\ndata: {"status":"RUNNING"}\n\n';
    const replayAndTerminal = [
      first,
      'id: 52\nevent: task-completed\ndata: {"status":"SUCCEEDED"}\n\n',
    ].join('');
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(new Response(first, { status: 200 }))
      .mockResolvedValueOnce(new Response(replayAndTerminal, { status: 200 }));
    const received: string[] = [];

    const unsubscribe = subscribeAiTaskEvents('agt_resume', {
      onEvent: (event) => received.push(event.id),
      onError: vi.fn(),
    });
    await vi.waitFor(() => expect(received).toEqual(['51', '52']), { timeout: 2_500 });
    unsubscribe();

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][1]?.headers).toEqual(expect.objectContaining({ 'Last-Event-ID': '51' }));
  });

  it('uses owned task list, detail and cancel endpoints with authenticated cookies', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ records: [], total: 0, page: 1, size: 20 }))
      .mockResolvedValueOnce(result({ taskId: 'agt_task', status: 'QUEUED', steps: [] }))
      .mockResolvedValueOnce(result({ taskId: 'agt_task', status: 'CANCELLED', steps: [] }));

    await agentTaskApi.list({ status: 'QUEUED', page: 1, size: 20 });
    await agentTaskApi.detail('agt_task');
    await agentTaskApi.cancel('agt_task');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/ai/tasks?status=QUEUED&page=1&size=20');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/ai/tasks/agt_task');
    expect(fetchMock.mock.calls[2][0]).toBe('/api/ai/tasks/agt_task/cancel');
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({ method: 'POST', credentials: 'include' }));
  });
});
