import { act, renderHook } from '@testing-library/react';
import { expect, it } from 'vitest';
import { useAsyncFormAction } from './useAsyncFormAction';

it('locks synchronously and never lets an old completion unlock a replacement form', () => {
  const { result } = renderHook(() => useAsyncFormAction('actor1'));
  let first!: NonNullable<ReturnType<typeof result.current.begin>>;
  act(() => { first = result.current.begin()!; expect(result.current.begin()).toBeUndefined(); });
  expect(result.current.busy).toBe(true);
  act(() => result.current.reset()); expect(first.current()).toBe(false);
  let second!: typeof first;
  act(() => { second = result.current.begin()!; });
  act(() => first.finish()); expect(result.current.busy).toBe(true);
  act(() => second.finish()); expect(result.current.busy).toBe(false);
});

it('invalidates old actions and captured starters across identity changes and unmount', () => {
  const { result, rerender, unmount } = renderHook(({ scope }) => useAsyncFormAction(scope), { initialProps: { scope: 'actor1' } });
  const oldBegin = result.current.begin;
  let first!: NonNullable<ReturnType<typeof oldBegin>>;
  act(() => { first = oldBegin()!; });
  rerender({ scope: 'actor2' }); expect(first.current()).toBe(false); expect(oldBegin()).toBeUndefined();
  rerender({ scope: 'actor1' }); expect(oldBegin()).toBeUndefined();
  let current!: typeof first;
  act(() => { current = result.current.begin()!; });
  unmount(); expect(current.current()).toBe(false); current.finish();
});
