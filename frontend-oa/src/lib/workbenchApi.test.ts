import { afterEach, describe, expect, it, vi } from 'vitest';
import { workbenchApi } from './workbenchApi';

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.restoreAllMocks());

describe('剩余业务台账接口', () => {
  it('按模块隔离查询并携带筛选条件', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result({
      records: [], total: 0, page: 2, size: 20, canManage: true,
    }));

    await workbenchApi.list('dictionary', { keyword: '状态', status: 'ACTIVE', page: 2, size: 20 });

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/workbench/modules/dictionary/records?keyword=%E7%8A%B6%E6%80%81&status=ACTIVE&page=2&size=20',
    );
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      credentials: 'include', cache: 'no-store',
    }));
  });

  it('更新和删除时都携带乐观锁版本', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ id: 7, version: 4 }))
      .mockResolvedValueOnce(result(null));

    await workbenchApi.update('expense', 7, {
      code: 'EXP-007', title: '差旅报销', status: 'PENDING', version: 3,
    });
    await workbenchApi.delete('expense', 7, 4);

    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'PUT', body: JSON.stringify({
        code: 'EXP-007', title: '差旅报销', status: 'PENDING', version: 3,
      }),
    }));
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({
      method: 'DELETE', body: JSON.stringify({ version: 4 }),
    }));
  });
});
