import { afterEach, describe, expect, it, vi } from 'vitest';
import { sandboxReplayApi } from './sandboxReplayApi';

const result = (data: unknown) => new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

afterEach(() => vi.restoreAllMocks());

describe('沙箱回放 API', () => {
  it('只向固定回放列表接口提交查询条件', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ records: [] }));
    await sandboxReplayApi.list({ keyword: 'ORDER', status: 'FAILED', page: 2, size: 20 });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/integration/replays?keyword=ORDER&status=FAILED&page=2&size=20');
  });

  it('基线查询不接受上游地址或请求体', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result([]));
    await sandboxReplayApi.baselines('CHECK', 25);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/integration/replays/baselines?keyword=CHECK&limit=25');
  });

  it('执行时只提交历史调用、原因和幂等键', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ id: 8 }));
    await sandboxReplayApi.execute({
      sourceInvocationId: 12,
      reason: '验证发布前兼容性',
      idempotencyKey: '12345678-1234-1234-1234-123456789abc',
    });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/integration/replays');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        sourceInvocationId: 12,
        reason: '验证发布前兼容性',
        idempotencyKey: '12345678-1234-1234-1234-123456789abc',
      }),
    }));
  });
});
