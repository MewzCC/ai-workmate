import { act, renderHook } from '@testing-library/react';
import { expect, it } from 'vitest';
import { useAsyncActionGuard } from './useAsyncActionGuard';

it('never revives an old request when an identity changes away and back', () => {
  const { result, rerender, unmount } = renderHook(({ scope }) => useAsyncActionGuard(scope), { initialProps: { scope: 'tenant1:user1:version1' } });
  const oldCapture = result.current.capture;
  const original = oldCapture();
  expect(original()).toBe(true);
  rerender({ scope: 'tenant1:user2:version1' });
  expect(original()).toBe(false);
  rerender({ scope: 'tenant1:user1:version1' });
  expect(original()).toBe(false);
  expect(oldCapture()()).toBe(false);
  const current = result.current.capture();
  expect(current()).toBe(true);
  act(() => result.current.invalidate());
  expect(current()).toBe(false);
  const last = result.current.capture();
  unmount(); expect(last()).toBe(false);
});
