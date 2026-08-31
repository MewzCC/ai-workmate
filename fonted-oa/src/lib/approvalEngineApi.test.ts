import { afterEach, describe, expect, it, vi } from 'vitest';
import { approvalEngineApi } from './approvalEngineApi';

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.restoreAllMocks());

describe('通用审批草稿接口', () => {
  it('使用认证请求完成创建、更新、提交和取消草稿', async () => {
    const draft = { id: 9, status: 'DRAFT', version: 0 };
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result(draft))
      .mockResolvedValueOnce(result({ ...draft, version: 1 }))
      .mockResolvedValueOnce(result({ ...draft, status: 'PENDING', version: 2 }))
      .mockResolvedValueOnce(result({ ...draft, status: 'CANCELLED', version: 2 }));

    await approvalEngineApi.createDraft({ formKey: 'expense', formData: {} });
    await approvalEngineApi.updateDraft(9, { formData: { reason: '拜访' }, version: 0 });
    await approvalEngineApi.submitDraft(9, 1);
    await approvalEngineApi.cancelDraft(9, 1);

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/approval-applications/drafts',
      '/api/approval-applications/9/draft',
      '/api/approval-applications/9/submit',
      '/api/approval-applications/9/cancel',
    ]);
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({
      method: 'PUT',
      credentials: 'include',
      cache: 'no-store',
    }));
    expect(fetchMock.mock.calls[2][1]?.body).toBe(JSON.stringify({ version: 1 }));
  });

  it('携带乐观锁版本撤回并恢复申请', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ id: 9, status: 'WITHDRAWN', version: 3 }))
      .mockResolvedValueOnce(result({ id: 9, status: 'DRAFT', version: 4 }));

    await approvalEngineApi.withdrawApplication(9, 2);
    await approvalEngineApi.reopenApplication(9, 3);

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/api/approval-applications/9/withdraw',
      '/api/approval-applications/9/reopen',
    ]);
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify({ version: 2 }));
    expect(fetchMock.mock.calls[1][1]?.body).toBe(JSON.stringify({ version: 3 }));
  });

  it('携带申请版本发送通用审批催办', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ id: 9, status: 'PENDING', version: 3 }));

    await approvalEngineApi.remindApplication(9, 2);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/approval-applications/9/remind');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'POST',
      credentials: 'include',
      body: JSON.stringify({ version: 2 }),
    }));
  });
});
