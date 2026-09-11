import { afterEach, describe, expect, it, vi } from 'vitest';
import { runtimeLogApi } from './runtimeLogApi';

const result = (data: unknown) => new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

afterEach(() => vi.restoreAllMocks());

describe('运行日志 API', () => {
  it('将来源、结果和受限时间窗提交到只读查询接口', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ records: [] }));
    await runtimeLogApi.list({
      source: 'AGENT',
      outcome: 'FAILED',
      from: '2026-09-01T00:00:00',
      to: '2026-09-07T23:59:59',
      page: 2,
      size: 20,
    });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/runtime-logs?source=AGENT&outcome=FAILED&from=2026-09-01T00%3A00%3A00&to=2026-09-07T23%3A59%3A59&page=2&size=20');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: 'include' }));
  });

  it('详情路径只包含固定来源和日志编号', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ id: 8 }));
    await runtimeLogApi.detail('INTEGRATION', 8);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/runtime-logs/INTEGRATION/8');
  });
});
