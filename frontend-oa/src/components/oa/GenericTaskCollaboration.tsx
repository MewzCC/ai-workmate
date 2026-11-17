import { useCallback, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import { genericApprovalTaskApi as api, type GenericApprovalTaskDetail } from '@/lib/genericApprovalTaskApi';
import { usePermission } from '@/hooks/usePermission';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { formatOaApiError, OaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { BusinessUserSelect } from './BusinessUserSelect';
import { useAuth } from '@/components/auth/AuthProvider';

/** The domain owns qualification and assignment. UI candidates never confer authority. */
export default function GenericTaskCollaboration({ data, scope, isCurrent, loading, reload, onTransferred, onUpdated }: {
  data: GenericApprovalTaskDetail; scope: string; isCurrent: () => boolean; loading: boolean;
  reload: () => Promise<unknown>; onTransferred: () => void; onUpdated: () => void;
}) {
  const { t } = useTranslation(), { allowed } = usePermission('approval:act');
  const { user } = useAuth();
  const [purpose, setPurpose] = useState<'TRANSFER' | 'CC' | 'PRE' | 'POST'>();
  const [form] = Form.useForm<{ targetUserId: number; reason: string }>();
  const action = useAsyncFormAction(scope), modal = useAsyncActionGuard(`${scope}:${purpose}`);
  const candidates = useCallback((query: { keyword?: string; page: number; size: number }) =>
    api.participants(data.task.id, purpose === 'PRE' || purpose === 'POST' ? 'ADD_SIGN' : purpose === 'TRANSFER' ? 'TRANSFER' : 'CC', query), [data.task.id, purpose]);
  const canAddSign = data.canAddSign === true && data.task.parentTaskId == null && data.task.addSignMode == null;
  const adding = purpose === 'PRE' || purpose === 'POST';
  const permitted = adding ? canAddSign : purpose === 'TRANSFER' ? data.canTransfer : data.canCopy;
  const titleKey = purpose === 'PRE' ? 'preSignButton' : purpose === 'POST' ? 'postSignButton' : purpose === 'TRANSFER' ? 'transferButton' : 'copyButton';
  const noticeKey = purpose === 'PRE' ? 'preSignNotice' : purpose === 'POST' ? 'postSignNotice' : purpose === 'TRANSFER' ? 'transferNotice' : 'copyNotice';
  const close = () => { modal.invalidate(); setPurpose(undefined); form.resetFields(); };
  const submit = async () => {
    if (!allowed || !purpose || !permitted || loading || !isCurrent()) return;
    const write = action.begin(), current = modal.capture(); if (!write) return;
    const valid = () => write.current() && current() && isCurrent(); let dispatched = false;
    try {
      const values = await form.validateFields(); if (!valid()) return;
      const latest = await api.detail(data.task.id); if (!valid()) return;
      if (latest.task.id !== data.task.id || latest.task.applicationId !== data.task.applicationId || latest.task.version !== data.task.version
          || !(adding ? latest.canAddSign && latest.task.parentTaskId == null && latest.task.addSignMode == null : purpose === 'TRANSFER' ? latest.canTransfer : latest.canCopy)) throw new Error('TASK_CHANGED');
      const command = { targetUserId: values.targetUserId, version: data.task.version, reason: values.reason.trim() };
      dispatched = true;
      if (purpose === 'PRE' || purpose === 'POST') {
        const receipt = await api.addSign(data.task.id, { ...command, mode: purpose }); if (!valid()) return;
        if (receipt.applicationId !== data.task.applicationId || receipt.taskId !== data.task.id || receipt.action !== 'ADD_SIGN' || receipt.mode !== purpose
            || receipt.applicationStatus !== 'PENDING' || receipt.version !== data.task.version + 1 || receipt.targetUserId !== command.targetUserId
            || !Number.isSafeInteger(receipt.addedTaskId) || receipt.addedTaskId < 1 || receipt.addedTaskId === data.task.id || receipt.addedTaskVersion !== 0
            || receipt.status !== (purpose === 'PRE' ? 'WAITING' : 'PENDING') || receipt.addedTaskStatus !== (purpose === 'PRE' ? 'PENDING' : 'WAITING')
            || receipt.activeTaskId !== (purpose === 'PRE' ? receipt.addedTaskId : data.task.id)) throw new Error('INVALID_RECEIPT');
        message.success(t(purpose === 'PRE' ? 'approval.genericTasks.preSignScheduled' : 'approval.genericTasks.postSignScheduled'));
      } else {
        const receipt = await (purpose === 'TRANSFER' ? api.transfer(data.task.id, command) : api.copyTo(data.task.id, command));
        if (!valid()) return;
        if (receipt.applicationId !== data.task.applicationId || receipt.taskId !== data.task.id || receipt.action !== purpose
          || receipt.status !== 'PENDING' || receipt.applicationStatus !== 'PENDING' || receipt.version !== data.task.version + 1
          || receipt.targetUserId !== command.targetUserId || receipt.originalAssigneeUserId !== user?.id
          || receipt.originalAssigneeUserId === receipt.targetUserId
          || receipt.assigneeUserId !== (purpose === 'TRANSFER' ? receipt.targetUserId : receipt.originalAssigneeUserId)) throw new Error('INVALID_RECEIPT');
        message.success(t(purpose === 'TRANSFER' ? 'approval.genericTasks.transferred' : 'approval.genericTasks.copied'));
      }
      close(); onUpdated();
      if (purpose === 'TRANSFER') onTransferred(); else await reload();
    } catch (error) {
      if (valid() && !isFormValidationError(error)) {
        message.error(error instanceof Error && error.message === 'TASK_CHANGED' ? t('approval.approvalDetail.changedBeforeAction')
          : dispatched && (!(error instanceof OaApiError) || error.status >= 500) ? t('approval.approvalDetail.resultUnknown') : formatOaApiError(error));
        close(); await reload();
      }
    } finally { write.finish(); }
  };
  if (!allowed || !data.canTransfer && !data.canCopy && !canAddSign) return null;
  return <><Space wrap>
    <Button disabled={!data.canTransfer || loading || action.busy} onClick={() => { modal.invalidate(); setPurpose('TRANSFER'); }}>{t('approval.genericTasks.transferButton')}</Button>
    <Button disabled={!data.canCopy || loading || action.busy} onClick={() => { modal.invalidate(); setPurpose('CC'); }}>{t('approval.genericTasks.copyButton')}</Button>
    {canAddSign && <><Button disabled={loading || action.busy} onClick={() => { modal.invalidate(); setPurpose('PRE'); }}>{t('approval.genericTasks.preSignButton')}</Button>
      <Button disabled={loading || action.busy} onClick={() => { modal.invalidate(); setPurpose('POST'); }}>{t('approval.genericTasks.postSignButton')}</Button></>}
  </Space>
    <Modal open={Boolean(purpose)} title={t(`approval.genericTasks.${titleKey}`)}
      okText={t('common.confirm')} cancelText={t('common.cancel')} confirmLoading={action.busy} cancelButtonProps={{ disabled: action.busy }}
      closable={!action.busy} maskClosable={!action.busy} keyboard={!action.busy} onCancel={() => { if (!action.busy) close(); }} onOk={() => void submit()}>
      <Alert showIcon type="info" message={t(`approval.genericTasks.${noticeKey}`)} />
      <Form form={form} layout="vertical" disabled={action.busy}>
        <Form.Item name="targetUserId" label={t('approval.genericTasks.recipient')} rules={[{ required: true, message: t('approval.genericTasks.chooseRecipient') }]}>
          <BusinessUserSelect active={Boolean(purpose) && allowed && !loading} disabled={action.busy} scopeKey={`${scope}:${purpose}`} query={candidates} maxSearchLength={64} />
        </Form.Item>
        <Form.Item name="reason" label={t('approval.genericTasks.collaborationReason')} rules={[{ required: true, whitespace: true, message: t('approval.genericTasks.reasonRequired') }, { max: 500 }]}>
          <Input.TextArea rows={3} maxLength={500} showCount />
        </Form.Item>
      </Form>
    </Modal></>;
}
