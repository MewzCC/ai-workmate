import { afterEach, describe, expect, it, vi } from 'vitest';
import { dictionaryApi } from './dictionaryApi';

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.restoreAllMocks());

describe('数据字典接口', () => {
  it('按真实记录编号读取类型和字典项所属类型，不依赖列表分页或编码推断', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ id: 18, code: 'FIXTURE', version: 3, canManage: false }))
      .mockResolvedValueOnce(result({ dictionaryType: { id: 18, code: 'FIXTURE', version: 3 }, item: { id: 7, dictionaryTypeId: 18, version: 4 } }));
    expect(await dictionaryApi.typeDetail(18)).toMatchObject({ id: 18, version: 3, canManage: false });
    expect(await dictionaryApi.itemDetail(7)).toMatchObject({ dictionaryType: { id: 18 }, item: { id: 7, version: 4 } });
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/dictionaries/18');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/dictionaries/items/7');
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({ credentials: 'include' }));
  });

  it('按字典类型分页查询字典项', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result({
      records: [], total: 0, page: 2, size: 20, canManage: true,
    }));

    await dictionaryApi.listItems(18, { keyword: '启用', status: 'ACTIVE', page: 2, size: 20 });

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/admin/dictionaries/18/items?keyword=%E5%90%AF%E7%94%A8&status=ACTIVE&page=2&size=20',
    );
  });

  it('启停与删除都携带乐观锁版本', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(result({ id: 7, version: 4 }))
      .mockResolvedValueOnce(result(null));

    await dictionaryApi.setItemStatus(18, 7, 'DISABLED', 3);
    await dictionaryApi.deleteItem(18, 7, 4);

    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ status: 'DISABLED', version: 3 }),
    }));
    expect(fetchMock.mock.calls[1][1]).toEqual(expect.objectContaining({
      method: 'DELETE', body: JSON.stringify({ version: 4 }),
    }));
  });

  it('业务表单读取接口使用字典编码而非管理端接口', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result([]));

    await dictionaryApi.options('EMPLOYEE_STATUS');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/dictionaries/EMPLOYEE_STATUS/items');
  });
});
