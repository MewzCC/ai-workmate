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
  const [purpose, setPurpose] = useState<'TRANSFER' | 'CC'>();
  const [form] = Form.useForm<{ targetUserId: number; reason: string }>();
  const action = useAsyncFormAction(scope), modal = useAsyncActionGuard(`${scope}:${purpose}`);
  const candidates = useCallback((query: { keyword?: string; page: number; size: number }) =>
    api.participants(data.task.id, purpose!, query), [data.task.id, purpose]);
  const permitted = purpose === 'TRANSFER' ? data.canTransfer : data.canCopy;
  const close = () => { modal.invalidate(); setPurpose(undefined); form.resetFields(); };
  const submit = async () => {
    if (!allowed || !purpose || !permitted || loading || !isCurrent()) return;
    const write = action.begin(), current = modal.capture(); if (!write) return;
    const valid = () => write.current() && current() && isCurrent(); let dispatched = false;
    try {
      const values = await form.validateFields(); if (!valid()) return;
      const latest = await api.detail(data.task.id); if (!valid()) return;
      if (latest.task.id !== data.task.id || latest.task.applicationId !== data.task.applicationId || latest.task.version !== data.task.version
          || !(purpose === 'TRANSFER' ? latest.canTransfer : latest.canCopy)) throw new Error('TASK_CHANGED');
      const command = { targetUserId: values.targetUserId, version: data.task.version, reason: values.reason.trim() };
      dispatched = true;
      const receipt = await (purpose === 'TRANSFER' ? api.transfer(data.task.id, command) : api.copyTo(data.task.id, command));
      if (!valid()) return;
      if (receipt.applicationId !== data.task.applicationId || receipt.taskId !== data.task.id || receipt.action !== purpose
          || receipt.status !== 'PENDING' || receipt.applicationStatus !== 'PENDING' || receipt.version !== data.task.version + 1
          || receipt.targetUserId !== command.targetUserId || receipt.originalAssigneeUserId !== user?.id
          || receipt.originalAssigneeUserId === receipt.targetUserId
          || receipt.assigneeUserId !== (purpose === 'TRANSFER' ? receipt.targetUserId : receipt.originalAssigneeUserId)) throw new Error('INVALID_RECEIPT');
      message.success(t(purpose === 'TRANSFER' ? 'approval.genericTasks.transferred' : 'approval.genericTasks.copied'));
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
  if (!allowed || !data.canTransfer && !data.canCopy) return null;
  return <><Space wrap>
    <Button disabled={!data.canTransfer || loading || action.busy} onClick={() => { modal.invalidate(); setPurpose('TRANSFER'); }}>{t('approval.genericTasks.transferButton')}</Button>
    <Button disabled={!data.canCopy || loading || action.busy} onClick={() => { modal.invalidate(); setPurpose('CC'); }}>{t('approval.genericTasks.copyButton')}</Button>
  </Space>
    <Modal open={Boolean(purpose)} title={t(purpose === 'TRANSFER' ? 'approval.genericTasks.transferButton' : 'approval.genericTasks.copyButton')}
      okText={t('common.confirm')} cancelText={t('common.cancel')} confirmLoading={action.busy} cancelButtonProps={{ disabled: action.busy }}
      closable={!action.busy} maskClosable={!action.busy} keyboard={!action.busy} onCancel={() => { if (!action.busy) close(); }} onOk={() => void submit()}>
      <Alert showIcon type="info" message={t(purpose === 'TRANSFER' ? 'approval.genericTasks.transferNotice' : 'approval.genericTasks.copyNotice')} />
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
