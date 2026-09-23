import { afterEach, describe, expect, it, vi } from 'vitest';
import { platformObservabilityApi } from './platformObservabilityApi';

afterEach(() => vi.restoreAllMocks());

describe('platform observability API', () => {
  it('requests only a bounded range through the authenticated API client', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      json: async () => ({ code: 200, data: { stats: { total: 0 }, timeline: [] } }),
    } as Response);

    await platformObservabilityApi.overview('7d');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/platform-observability/overview?range=7d');
  });
});
