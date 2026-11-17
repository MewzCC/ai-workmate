import { act, renderHook, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { expect, it, vi } from 'vitest';
import { useBusinessRecordDetail } from './useBusinessRecordDetail';

const wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter initialEntries={['/oa/visitor-booking?id=9']}>{children}</MemoryRouter>;

it('loads a route record and exposes its authorized detail', async () => {
  const loadRecord = vi.fn<(id: number) => Promise<{ id: number; name: string }>>().mockResolvedValue({ id: 9, name: 'Owned record' });
  const { result } = renderHook(() => useBusinessRecordDetail({ scope: 'tenant:owner', allowed: true, pageId: 'visitor-booking', loadRecord }), { wrapper });
  await waitFor(() => expect(result.current.data?.name).toBe('Owned record'));
  expect(result.current.visible).toBe(true);
  expect(loadRecord).toHaveBeenCalledWith(9);
});

it('ignores an obsolete response after the actor scope changes', async () => {
  let resolve!: (value: { id: number }) => void;
  const loadRecord = vi.fn().mockReturnValue(new Promise<{ id: number }>(done => { resolve = done; }));
  const { result, rerender } = renderHook(({ scope, allowed }) => useBusinessRecordDetail({ scope, allowed, pageId: 'visitor-booking', loadRecord }),
    { wrapper, initialProps: { scope: 'tenant:owner', allowed: true } });
  rerender({ scope: 'other-tenant:other-owner', allowed: false });
  await act(async () => resolve({ id: 9 }));
  expect(result.current.visible).toBe(false);
  expect(result.current.data).toBeUndefined();
});
