import { afterEach, describe, expect, it, vi } from 'vitest';
import { supplierApi } from './supplierApi';

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.restoreAllMocks());

describe('供应商接口', () => {
  it('列表查询只使用固定供应商资源路径', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ records: [] }));

    await supplierApi.list({ keyword: 'ACME', status: 'ACTIVE', category: 'SERVICE', page: 2, size: 20 });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/suppliers?keyword=ACME&status=ACTIVE&category=SERVICE&page=2&size=20');
  });

  it('状态流转携带目标状态、原因和乐观锁版本', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ id: 7 }));

    await supplierApi.updateStatus(7, 'BLACKLISTED', 3, '合规复核未通过');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/suppliers/7/status');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ status: 'BLACKLISTED', version: 3, reason: '合规复核未通过' }),
    }));
  });
});
