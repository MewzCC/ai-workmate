import { afterEach, expect, it, vi } from 'vitest';
import { todoApi } from './oaApi';

const item = { id: 42, applicationId: 9, applicantUserId: 8, applicantName: 'Synthetic applicant', title: 'Frozen request', businessType: 'GENERIC_APPROVAL', status: 'PENDING', version: 1, detailAvailable: true, overdue: false, leaveType: null, durationHalfDays: null };
const result = (data: unknown) => new Response(JSON.stringify({ code: 200, data }), { status: 200, headers: { 'Content-Type': 'application/json' } });
afterEach(() => vi.restoreAllMocks());
it('loads the authenticated unified query with its actual scope and bounded page', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result({ records: [item], total: 1, page: 1, size: 20, businessType: 'ALL', status: 'PENDING', from: null, to: null }));
  expect((await todoApi.list()).records[0].businessType).toBe('GENERIC_APPROVAL');
  const url = new URL(String(fetch.mock.calls[0][0]), 'http://localhost'); expect(url.pathname).toBe('/api/todos');
  expect(Object.fromEntries(url.searchParams)).toEqual({ businessType: 'ALL', status: 'PENDING', page: '1', size: '20' });
  expect(fetch.mock.calls[0][1]).toEqual(expect.objectContaining({ credentials: 'include', cache: 'no-store' }));
});
it('preflights only the requested protected summary', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result(item)); expect(await todoApi.summary(42)).toEqual(item);
  expect(fetch.mock.calls[0][0]).toBe('/api/todos/42/summary');
});
it.each([
  { records: [], total: 0, page: 1, size: 20 },
  { records: [item], total: 1, page: 2, size: 20, businessType: 'ALL', status: 'PENDING' },
  { records: [{ ...item, businessType: 'HR' }], total: 1, page: 1, size: 20, businessType: 'ALL', status: 'PENDING' },
  { records: [{ ...item, applicantAvatarUrl: 'https://untrusted.invalid/private' }], total: 1, page: 1, size: 20, businessType: 'ALL', status: 'PENDING' },
])('rejects incomplete, stale or invalid summaries instead of a fake empty inbox', async value => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result(value)); await expect(todoApi.list()).rejects.toThrow();
});
it('rejects a mismatched requested summary rather than selecting a different record', async () => {
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(result({ ...item, id: 43 })); await expect(todoApi.summary(42)).rejects.toThrow();
});
