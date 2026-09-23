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

  it('loads and saves chart preferences through the authenticated API client', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, json: async () => ({ code: 200, data: { charts: [] } }),
    } as Response);
    await platformObservabilityApi.preferences();
    await platformObservabilityApi.updatePreferences([]);
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/platform-observability/preferences');
    expect(fetchMock.mock.calls[1][0]).toBe('/api/admin/platform-observability/preferences');
    expect(fetchMock.mock.calls[1][1]).toMatchObject({ method: 'PUT', body: JSON.stringify({ charts: [] }) });
  });

  it('requests a bounded alternate timeline with encoded range parameters', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, json: async () => ({ code: 200, data: { timeline: [] } }),
    } as Response);
    await platformObservabilityApi.timeline('2026-09-16T00:00:00', '2026-09-23T00:00:00', 'hour');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/platform-observability/timeline?from=2026-09-16T00%3A00%3A00&to=2026-09-23T00%3A00%3A00&interval=hour');
  });

  it('loads an adjacent-period comparison from the authenticated endpoint', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true, json: async () => ({ code: 200, data: { range: '30d' } }),
    } as Response);
    await platformObservabilityApi.comparison('30d');
    expect(fetchMock.mock.calls[0][0]).toBe('/api/admin/platform-observability/comparison?range=30d');
  });
});
