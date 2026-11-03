import { afterEach, expect, it, vi } from 'vitest';
import { leaveApi } from './oaApi';
afterEach(() => vi.unstubAllGlobals());
it('sends actual candidate search and pagination while preserving the existing keyword call', async () => {
  const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ code: 200, data: { records: [], total: 0 } }), { status: 200, headers: { 'Content-Type': 'application/json' } })));
  vi.stubGlobal('fetch', fetch);
  await leaveApi.approverCandidates({ keyword: 'Synthetic', page: 3, size: 20 });
  expect(fetch.mock.calls[0][0]).toContain('/leave-applications/approver-candidates?keyword=Synthetic&page=3&size=20');
  await leaveApi.approverCandidates('Legacy');
  expect(fetch.mock.calls[1][0]).toContain('/leave-applications/approver-candidates?keyword=Legacy&page=1&size=100');
});
