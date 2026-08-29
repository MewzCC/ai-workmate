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
});
