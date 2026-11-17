import { App, Descriptions, Modal } from 'antd';
import i18n from '@/i18n';
import { useCallback, useEffect, useRef } from 'react';

/** Acknowledgement only: this modal has no business submission callback. */
export function showUiFormPreview<T extends object>(values: T, labels: Record<string, string>, info = Modal.info) {
  const fields = values as Record<string, unknown>;
  return info({ title: i18n.t('oa.ai.uiPreviewTitle'), width: 560,
    content: <Descriptions column={1} size="small" items={Object.entries(labels)
      .filter(([name]) => fields[name] !== undefined && fields[name] !== '')
      .map(([name, label]) => ({ key: name, label, children: String(fields[name] ?? '-') }))} /> });
}

/** Keep a preview tied to the form instance that supplied its private values. */
export function useUiFormPreview(scope: string) {
  const { modal } = App.useApp();
  const active = useRef<ReturnType<typeof showUiFormPreview> | undefined>(undefined);
  useEffect(() => () => { active.current?.destroy(); active.current = undefined; }, [scope]);
  return useCallback((values: object, labels: Record<string, string>) => {
    active.current?.destroy(); active.current = showUiFormPreview(values, labels, modal.info);
  }, [modal]);
}
