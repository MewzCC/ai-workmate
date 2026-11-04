import { afterEach, expect, it, vi } from 'vitest';
import { genericApprovalTaskApi } from './genericApprovalTaskApi';
afterEach(() => vi.restoreAllMocks());
it('uses authenticated generic task endpoints, field pagination and optimistic decision versions', async () => {
  const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify({ code: 200, data: {} }), { status: 200, headers: { 'Content-Type': 'application/json' } }));
  await genericApprovalTaskApi.mine({ status: 'PENDING', page: 2, size: 20 }); await genericApprovalTaskApi.detail(42, 2);
  await genericApprovalTaskApi.approve(42, 3, 'Reviewed'); await genericApprovalTaskApi.reject(42, 4, 'Needs correction');
  expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/generic-approval-tasks?status=PENDING&page=2&size=20', '/api/generic-approval-tasks/42?fieldPage=2', '/api/generic-approval-tasks/42/approve', '/api/generic-approval-tasks/42/reject']);
  for (const [, options] of fetch.mock.calls) expect(options).toEqual(expect.objectContaining({ credentials: 'include', cache: 'no-store' }));
  expect(fetch.mock.calls[2][1]?.body).toBe(JSON.stringify({ version: 3, comment: 'Reviewed' }));
  expect(fetch.mock.calls[3][1]?.body).toBe(JSON.stringify({ version: 4, comment: 'Needs correction' }));
});
