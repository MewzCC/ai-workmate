import { useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { approvalEngineApi, type ApprovalApplication } from '@/lib/approvalEngineApi';
import { message } from '@/lib/antdMessage';
import { formatOaApiError } from '@/lib/oaApi';
import { usePermission } from './usePermission';
import { useAsyncFormAction } from './useAsyncFormAction';
import { useScopedModalConfirm } from './useScopedModalConfirm';

export type GenericApplicationAction = 'submit' | 'cancel' | 'withdraw' | 'reopen' | 'remind';
/** Shared human lifecycle actions. No write callback is registered as a page UI command. */
export function useGenericApplicationActions({ scope, records, isCurrent, reload, onUpdated, onReopened }: {
  scope: string; records: ApprovalApplication[]; isCurrent: () => boolean; reload: () => Promise<unknown>;
  onUpdated?: (item: ApprovalApplication) => void | Promise<void>; onReopened?: (item: ApprovalApplication) => void;
}) {
  const { t } = useTranslation(), { user } = useAuth();
  const { allowed, hasPermission } = usePermission(['approval:mine:read', 'route:approval-start']);
  const action = useAsyncFormAction(scope), confirm = useScopedModalConfirm(scope);
  const latest = useRef({ records, onUpdated, onReopened }); latest.current = { records, onUpdated, onReopened };
  const can = (item: ApprovalApplication, operation: GenericApplicationAction) => allowed && item.applicantUserId === user?.id
    && Number.isSafeInteger(item.id) && item.id > 0 && Number.isSafeInteger(item.version) && item.version >= 0
    && (operation === 'submit' ? item.status === 'DRAFT' && item.canEditDraft && hasPermission('approval:submit')
      : operation === 'cancel' ? item.status === 'DRAFT' && item.canCancel && hasPermission('approval:cancel')
      : operation === 'withdraw' ? item.status === 'PENDING' && item.canWithdraw && hasPermission('approval:withdraw')
      : operation === 'reopen' ? ['REJECTED', 'WITHDRAWN'].includes(item.status) && hasPermission('approval:reopen')
      : item.status === 'PENDING' && item.canRemind && hasPermission('approval:remind'));
  const perform = (item: ApprovalApplication, operation: GenericApplicationAction) => {
    if (!can(item, operation) || !isCurrent() || action.busy) return;
    confirm({ title: t('approval.genericApplications.confirm', { action: t(`approval.genericApplications.${operation}`), id: item.id }),
      onOk: async () => {
        if (!isCurrent() || !can(item, operation) || !latest.current.records.some(row => row.id === item.id && row.version === item.version)) return;
        const write = action.begin(); if (!write) return;
        try {
          const operations = { submit: approvalEngineApi.submitDraft, cancel: approvalEngineApi.cancelDraft,
            withdraw: approvalEngineApi.withdrawApplication, reopen: approvalEngineApi.reopenApplication, remind: approvalEngineApi.remindApplication };
          const result = await operations[operation](item.id, item.version);
          if (!write.current() || !isCurrent()) return;
          const expected = { submit: 'PENDING', cancel: 'CANCELLED', withdraw: 'WITHDRAWN', reopen: 'DRAFT', remind: 'PENDING' }[operation];
          if (result.id !== item.id || result.applicantUserId !== user?.id || result.formKey !== item.formKey
              || result.status !== expected || !Number.isSafeInteger(result.version) || result.version <= item.version)
            throw new Error(t('approval.genericApplications.invalidResult'));
          message.success(t('approval.genericApplications.actionDone', { action: t(`approval.genericApplications.${operation}`) }));
          await reload();
          if (!write.current() || !isCurrent()) return;
          await latest.current.onUpdated?.(result);
          if (write.current() && isCurrent() && operation === 'reopen' && hasPermission('approval:create') && hasPermission('route:approval-form'))
            latest.current.onReopened?.(result);
        } catch (error) { if (write.current() && isCurrent()) { message.error(formatOaApiError(error)); await reload(); } }
        finally { write.finish(); }
      } });
  };
  return { can, perform, busy: action.busy };
}
