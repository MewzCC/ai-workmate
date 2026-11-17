import { act, renderHook } from '@testing-library/react';
import type { ModalFuncProps } from 'antd';
import { beforeEach, expect, it, vi } from 'vitest';
import { useScopedModalConfirm } from './useScopedModalConfirm';

const modal = vi.hoisted(() => ({ confirm: vi.fn() }));
vi.mock('antd', () => ({ App: { useApp: () => ({ modal }) } }));
const destroy = vi.fn();
beforeEach(() => { vi.resetAllMocks(); modal.confirm.mockImplementation(() => ({ destroy })); });

it('destroys only owned dialogs on authority changes and does not revive old confirmation callbacks', async () => {
  const submit = vi.fn();
  const { result, rerender } = renderHook(({ scope }) => useScopedModalConfirm(scope), { initialProps: { scope: 'user7:v1' } });
  const oldConfirm = result.current;
  act(() => { result.current({ onOk: submit }); });
  const config = modal.confirm.mock.calls[0][0] as ModalFuncProps;
  rerender({ scope: 'user8:v1' });
  expect(destroy).toHaveBeenCalledTimes(1);
  rerender({ scope: 'user7:v1' });
  await act(async () => { await config.onOk?.(); oldConfirm({ onOk: submit }); });
  expect(submit).not.toHaveBeenCalled();
  expect(modal.confirm).toHaveBeenCalledTimes(1);
});

it('releases a closed dialog and forwards its close callback exactly once', () => {
  const afterClose = vi.fn();
  const { result, unmount } = renderHook(() => useScopedModalConfirm('user7:v1'));
  act(() => { result.current({ afterClose }); });
  const config = modal.confirm.mock.calls[0][0] as ModalFuncProps;
  act(() => { config.afterClose?.(); });
  unmount();
  expect(afterClose).toHaveBeenCalledTimes(1);
  expect(destroy).not.toHaveBeenCalled();
});

it('forwards an authorized confirmation and destroys remaining owned dialogs on unmount', async () => {
  const submit = vi.fn();
  const { result, unmount } = renderHook(() => useScopedModalConfirm('user7:v1'));
  act(() => { result.current({ onOk: submit }); });
  const config = modal.confirm.mock.calls[0][0] as ModalFuncProps;
  await act(async () => { await config.onOk?.(); });
  expect(submit).toHaveBeenCalledTimes(1);
  unmount();
  expect(destroy).toHaveBeenCalledTimes(1);
});
