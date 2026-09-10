import { beforeEach, describe, expect, it, vi } from 'vitest';
import { contractApi } from '@/lib/contractApi';

describe('contractApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ code: 200, data: {} }),
    }));
  });

  it('queries contract filters without trusted identity fields', async () => {
    await contractApi.list({ keyword: '采购', status: 'ACTIVE', expiryState: 'EXPIRING', page: 2, size: 20 });
    const [url, options] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toContain('/api/contracts?');
    expect(String(url)).toContain('expiryState=EXPIRING');
    expect(options).toMatchObject({ credentials: 'include' });
    expect(String(url)).not.toContain('tenantId');
    expect(String(url)).not.toContain('userId');
  });

  it('uses dedicated endpoints for payment and expiry reminders', async () => {
    await contractApi.recordPayment(8, 1200, '2026-09-10', 'PAY-2026-1', 3, '首付款');
    await contractApi.remind(8, 4);
    const calls = vi.mocked(fetch).mock.calls;
    expect(String(calls[0][0])).toContain('/api/contracts/8/payments');
    expect(calls[0][1]).toMatchObject({ method: 'POST', body: expect.stringContaining('PAY-2026-1') });
    expect(String(calls[1][0])).toContain('/api/contracts/8/reminders');
    expect(calls[1][1]).toMatchObject({ method: 'POST', body: JSON.stringify({ version: 4 }) });
  });
});
