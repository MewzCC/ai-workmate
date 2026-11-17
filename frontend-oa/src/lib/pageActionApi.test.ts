import { afterEach, describe, expect, it, vi } from 'vitest';
import { pageActionApi, type PageActionItem } from './pageActionApi';

const response = (data: unknown) => new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
  status: 200,
  headers: { 'Content-Type': 'application/json' },
});

afterEach(() => vi.restoreAllMocks());

describe('页面操作配置 API', () => {
  it('读取服务端受控目录', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response({ pages: [] }));
    await pageActionApi.overview();
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/page-actions');
  });

  it('更新时只提交开关、版本和理由', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(response({ pages: [] }));
    const action = { pageId: 'dashboard', toolCode: 'todo.query', version: 3 } as PageActionItem;
    await pageActionApi.update(action, false, '暂时关闭');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/page-actions/dashboard/todo.query');
    expect(JSON.parse(String(fetchMock.mock.calls[0][1]?.body))).toEqual({
      enabled: false,
      version: 3,
      reason: '暂时关闭',
    });
  });
});
