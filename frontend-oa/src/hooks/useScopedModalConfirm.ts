import { useCallback, useEffect, useRef } from 'react';
import { App, type ModalFuncProps } from 'antd';
import { useAsyncActionGuard } from './useAsyncActionGuard';

/** Destroy only this page's confirmation dialogs when its authenticated scope changes. */
export function useScopedModalConfirm(scope: string) {
  const { modal } = App.useApp();
  const { capture } = useAsyncActionGuard(scope);
  const owned = useRef(new Set<ReturnType<typeof modal.confirm>>());
  useEffect(() => {
    const dialogs = owned.current;
    return () => { dialogs.forEach(dialog => dialog.destroy()); dialogs.clear(); };
  }, [scope]);
  return useCallback((config: ModalFuncProps) => {
    const current = capture();
    if (!current()) return;
    const dialog = modal.confirm({ ...config,
      onOk: (...args) => current() ? config.onOk?.(...args) : undefined,
      afterClose: () => { owned.current.delete(dialog); config.afterClose?.(); },
    });
    owned.current.add(dialog);
    return dialog;
  }, [modal, capture]);
}
