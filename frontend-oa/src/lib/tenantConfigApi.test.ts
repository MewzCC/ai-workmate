import { afterEach, describe, expect, it, vi } from 'vitest';
import { tenantConfigApi } from './tenantConfigApi';

function result(data: unknown): Response {
  return new Response(JSON.stringify({ code: 200, message: 'ok', data }), {
    status: 200, headers: { 'Content-Type': 'application/json' },
  });
}

afterEach(() => vi.restoreAllMocks());

describe('租户配置接口', () => {
  it('按配置分类调用固定白名单接口并携带版本', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ version: 4 }));

    await tenantConfigApi.updateSecurity({ passwordMinLength: 12, sessionTimeoutMinutes: 60, version: 3 });

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/tenant-config/security');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'PUT', body: JSON.stringify({ passwordMinLength: 12, sessionTimeoutMinutes: 60, version: 3 }),
    }));
  });

  it('变更历史使用当前登录租户接口且不传租户标识', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(result({ records: [], total: 0, page: 2, size: 20 }));

    await tenantConfigApi.history(2, 20);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/tenant-config/history?page=2&size=20');
  });
});
