import { beforeEach, describe, expect, it, vi } from 'vitest';
import { collectPagedRecords } from './collectPagedRecords';
import { bindAuthResponseIdentity, invalidateAuthResponses } from './authEvents';

vi.mock('@/i18n', () => ({ default: { t: () => 'Incomplete catalogue' } }));
beforeEach(() => { invalidateAuthResponses(); bindAuthResponseIdentity('synthetic:reader', 'initial'); });

describe('complete catalogue loading', () => {
  it('includes records beyond the first hundred and the final partial page', async () => {
    const all = Array.from({ length: 205 }, (_, index) => ({ id: index + 1 }));
    const load = vi.fn(async (page: number, size: number) => ({ records: all.slice((page - 1) * size, page * size), total: all.length }));
    await expect(collectPagedRecords(load)).resolves.toEqual(all);
    expect(load.mock.calls).toEqual([[1, 100], [2, 100], [3, 100]]);
  });

  it('accepts an actually empty catalogue without extra requests', async () => {
    const load = vi.fn(async () => ({ records: [], total: 0 }));
    await expect(collectPagedRecords(load)).resolves.toEqual([]);
    expect(load).toHaveBeenCalledTimes(1);
  });

  it('propagates a later page failure instead of returning the first page as complete', async () => {
    const failure = new Error('Synthetic upstream failure');
    const load = vi.fn(async (page: number) => {
      if (page === 2) throw failure;
      return { records: Array.from({ length: 100 }, (_, id) => ({ id })), total: 150 };
    });
    await expect(collectPagedRecords(load)).rejects.toBe(failure);
  });

  it('rejects premature empty pages and invalid totals', async () => {
    await expect(collectPagedRecords(async () => ({ records: [], total: 150 }))).rejects.toThrow('Incomplete catalogue');
    await expect(collectPagedRecords(async () => ({ records: [], total: Number.NaN }))).rejects.toThrow('Incomplete catalogue');
  });

  it('fails visibly when the bounded page budget cannot retrieve the complete catalogue', async () => {
    const load = vi.fn(async () => ({ records: Array.from({ length: 100 }, (_, id) => id), total: 100001 }));
    await expect(collectPagedRecords(load)).rejects.toThrow('Incomplete catalogue');
    expect(load).toHaveBeenCalledTimes(1000);
  });

  it.each([150, 250])('rejects a changing total of %i rather than returning a mixed catalogue', async total => {
    const load = vi.fn(async (page: number) => ({ records: Array.from({ length: 100 }, (_, id) => id), total: page === 1 ? 205 : total }));
    await expect(collectPagedRecords(load)).rejects.toThrow('Incomplete catalogue');
    expect(load).toHaveBeenCalledTimes(2);
  });

  it.each([
    { records: [1], total: 0 },
    { records: Array.from({ length: 101 }, (_, id) => id), total: 101 },
  ])('rejects inconsistent totals or an oversized response page', async result => {
    await expect(collectPagedRecords(async () => result)).rejects.toThrow('Incomplete catalogue');
  });

  it.each(['identity', 'permissions'])('stops the entire catalogue when %s changes during a page read', async change => {
    let resolve!: (result: { records: number[]; total: number }) => void;
    const load = vi.fn(() => new Promise<{ records: number[]; total: number }>(done => { resolve = done; }));
    const pending = collectPagedRecords(load);
    const assertion = expect(pending).rejects.toMatchObject({ code: 'AUTH_SCOPE_CHANGED' });
    bindAuthResponseIdentity(change === 'identity' ? 'synthetic:other-reader' : 'synthetic:reader', 'changed');
    resolve({ records: Array.from({ length: 100 }, (_, id) => id), total: 205 });
    await assertion; expect(load).toHaveBeenCalledTimes(1);
  });
});
