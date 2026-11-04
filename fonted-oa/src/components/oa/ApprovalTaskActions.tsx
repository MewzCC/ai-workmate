import { useCallback, useRef, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Space, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { BusinessUserSelect } from './BusinessUserSelect';
import type { BusinessUserOption } from '@/lib/adminAssetsApi';
import { formatOaApiError, OaApiError, todoApi, type LeaveApplication } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';

type Mode = 'approve' | 'reject' | 'transfer' | 'copy' | 'preSign' | 'postSign';
type Props = { application: LeaveApplication; scope: string; isCurrent: () => boolean; loading: boolean;
  reload: () => Promise<unknown>; onMoved: () => void; onUpdated?: () => void; compact?: boolean };

/** Human confirmation only; no write callback is registered with the page UI bridge. */
export default function ApprovalTaskActions(props: Props) {
  const { user } = useAuth(), { allowed } = usePermission('approval:act'), item = props.application;
  if (!allowed || !item.canApprove || item.status !== 'PENDING' || item.taskStatus !== 'PENDING'
      || item.approverUserId !== user?.id || item.applicantUserId === user?.id || item.taskId == null
      || !Number.isSafeInteger(item.taskId) || item.taskId < 1 || !Number.isSafeInteger(item.taskVersion) || item.taskVersion! < 0) return null;
  return <TaskActions key={`${props.scope}:${item.id}:${item.version}:${item.taskId}:${item.taskVersion}`} {...props} />;
}

function TaskActions({ application: item, scope, isCurrent, loading, reload, onMoved, onUpdated, compact }: Props) {
  const { t } = useTranslation();
  const [mode, setMode] = useState<Mode>();
  const [form] = Form.useForm<{ comment?: string; targetUserId?: number; reason?: string }>();
  const action = useAsyncFormAction(scope), dialog = useAsyncActionGuard(`${scope}:${mode}`);
  const { capture: captureDialog } = dialog;
  const collaboration = mode && !['approve', 'reject'].includes(mode);
  const knownCandidates = useRef(new Map<number, BusinessUserOption>());
  const selectedId = Form.useWatch('targetUserId', form);
  const query = useCallback(async (params: { keyword?: string; page: number; size: number }) => {
    const current = captureDialog();
    if (!collaboration || !mode || !isCurrent() || !current()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    const result = await todoApi.participantOptions(item.taskId!, { ...params, approversOnly: mode !== 'copy' });
    if (!current() || !isCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (result.page !== params.page || result.size !== params.size || !Number.isSafeInteger(result.total) || result.total < 0
        || result.records.length > params.size || result.records.some(row => !Number.isSafeInteger(row.id) || row.id < 1
          || typeof row.name !== 'string' || row.name.length < 1 || mode !== 'copy' && (!row.canApprove || row.id === item.applicantUserId)))
      throw new Error('PAGE_UI_FIELD_REJECTED');
    result.records.forEach(row => knownCandidates.current.set(row.id, row));
    return result;
  }, [collaboration, captureDialog, isCurrent, item.applicantUserId, item.taskId, mode]);
  const close = () => { if (action.busy) return; dialog.invalidate(); setMode(undefined); knownCandidates.current.clear(); form.resetFields(); };
  const open = (next: Mode) => { if (!isCurrent() || loading || action.busy) return; dialog.invalidate(); form.resetFields(); knownCandidates.current.clear(); setMode(next); };
  const submit = async () => {
    if (!mode || loading || !isCurrent()) return;
    const write = action.begin(), current = dialog.capture(); if (!write) return;
    const valid = () => write.current() && current() && isCurrent();
    let dispatched = false;
    try {
      const values = await form.validateFields();
      if (!valid()) return;
      if (collaboration && (!Number.isSafeInteger(values.targetUserId) || values.targetUserId! < 1
          || !knownCandidates.current.has(values.targetUserId!))) throw new Error(t('approval.approvalDetail.participantRequired'));
      const latest = await todoApi.detail(item.taskId!);
      if (!valid()) return;
      if (latest.id !== item.id || latest.version !== item.version || latest.taskId !== item.taskId || latest.taskVersion !== item.taskVersion
          || !latest.canApprove || latest.taskStatus !== 'PENDING' || latest.status !== 'PENDING'
          || latest.approverUserId !== item.approverUserId) throw new Error(t('approval.approvalDetail.changedBeforeAction'));
      const id = item.taskId!, version = item.taskVersion!, reason = values.reason?.trim() ?? '';
      dispatched = true;
      const result = mode === 'approve' ? await todoApi.approve(id, version, values.comment?.trim())
        : mode === 'reject' ? await todoApi.reject(id, version, values.comment!.trim())
        : mode === 'copy' ? await todoApi.copyTo(id, values.targetUserId!, version, reason)
        : mode === 'transfer' ? await todoApi.transfer(id, values.targetUserId!, version, reason)
        : await todoApi.addSign(id, values.targetUserId!, version, mode === 'preSign' ? 'PRE' : 'POST', reason);
      if (!valid()) return;
      if (result.id !== item.id || result.applicantUserId !== item.applicantUserId
          || !Number.isSafeInteger(result.version) || result.version < item.version
          || !(mode === 'approve' ? ['APPROVED', 'PENDING'] : mode === 'reject' ? ['REJECTED'] : ['PENDING']).includes(result.status))
        throw new Error(t('approval.genericApplications.invalidResult'));
      message.success(t(`approval.approvalDetail.${mode === 'approve' && result.status === 'PENDING' ? 'approveNext' : mode}Success`));
      setMode(undefined); form.resetFields(); onUpdated?.();
      if (mode === 'transfer' || mode === 'preSign') onMoved();
      else await reload();
    } catch (error) {
      if (valid() && !isFormValidationError(error)) {
        const localMessages = ['approval.approvalDetail.participantRequired', 'approval.approvalDetail.changedBeforeAction', 'approval.genericApplications.invalidResult'].map(key => t(key));
        message.error(error instanceof Error && localMessages.includes(error.message) ? error.message
          : dispatched && (!(error instanceof OaApiError) || error.status >= 500) ? t('approval.approvalDetail.resultUnknown') : formatOaApiError(error));
        setMode(undefined); form.resetFields(); await reload();
      }
    } finally { write.finish(); }
  };
  return <>
    <div className="approval-decision-bar">
      <div><Typography.Text strong>{t('approval.approvalDetail.decisionBarTitle')}</Typography.Text>
        <Typography.Text type="secondary">{t('approval.approvalDetail.decisionBarHint')}</Typography.Text></div>
      <Space wrap>
        {!compact && (['preSign', 'postSign', 'copy', 'transfer'] as const).map(value => <Button key={value} disabled={loading || action.busy}
          onClick={() => open(value)}>{t(`approval.approvalDetail.${value}Button`)}</Button>)}
        <Button danger disabled={loading || action.busy} onClick={() => open('reject')}>{t('approval.approvalDetail.rejectButton')}</Button>
        <Button type="primary" icon={<OaIcon name="approval" />} disabled={loading || action.busy} onClick={() => open('approve')}>{t('approval.approvalDetail.approveButton')}</Button>
      </Space>
    </div>
    <Modal className="approval-decision-modal" open={Boolean(mode)}
      title={mode && t(`approval.approvalDetail.${mode}ModalTitle`)}
      cancelText={t('common.cancel')}
      okText={mode && t(`approval.approvalDetail.${mode}ModalOk`)} confirmLoading={action.busy}
      okButtonProps={{ danger: mode === 'reject', disabled: loading }}
      cancelButtonProps={{ disabled: action.busy }} closable={!action.busy} maskClosable={!action.busy} keyboard={!action.busy}
      onCancel={close} onOk={() => void submit()}>
      <Alert showIcon type={mode === 'reject' || mode === 'transfer' || mode === 'preSign' ? 'warning' : 'info'}
        message={mode && t(`approval.approvalDetail.${mode}${collaboration ? 'Alert' : 'AlertMessage'}`)} />
      <Typography.Paragraph type="secondary">{t('approval.approvalDetail.actionSubject', { id: item.id, task: item.taskId, version: item.taskVersion })}</Typography.Paragraph>
      <Form form={form} layout="vertical" disabled={action.busy || loading}>
        {collaboration ? <>
          <Alert type="info" showIcon message={t('approval.approvalDetail.participantSearchHint')} />
          <Form.Item name="targetUserId" label={t('approval.approvalDetail.participantLabel')}
            rules={[{ required: true, message: t('approval.approvalDetail.participantRequired') }]}>
            <BusinessUserSelect query={query} active={Boolean(collaboration)} scopeKey={`${scope}:${mode}`}
              disabled={action.busy || loading} maxSearchLength={64} selectedOption={selectedId == null ? undefined : knownCandidates.current.get(selectedId)}
              placeholder={t('approval.approvalDetail.participantPlaceholder')} />
          </Form.Item>
          <Form.Item name="reason" label={t('approval.approvalDetail.collaborationReasonLabel')}
            rules={[{ required: true, whitespace: true, message: t('approval.approvalDetail.collaborationReasonRequired') }, { max: 500 }]}>
            <Input.TextArea rows={4} showCount maxLength={500} placeholder={t('approval.approvalDetail.collaborationReasonPlaceholder')} />
          </Form.Item>
        </> : <Form.Item name="comment" label={t('approval.approvalDetail.commentLabel')}
          rules={[{ required: mode === 'reject', whitespace: true, message: t('approval.approvalDetail.commentRejectRequired') }, { max: 500 }]}>
          <Input.TextArea rows={5} showCount maxLength={500} placeholder={t(`approval.approvalDetail.comment${mode === 'reject' ? 'Reject' : 'Approve'}Placeholder`)} />
        </Form.Item>}
      </Form>
    </Modal>
  </>;
}
