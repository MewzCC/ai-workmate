import { useCallback, useState } from 'react';
import { Alert, Button, Card, DatePicker, Descriptions, Drawer, Empty, Form, Input, Modal, Pagination, Segmented, Space, Spin, Tag, Timeline, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import dayjs from 'dayjs';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { usePermission } from '@/hooks/usePermission';
import { useIsMobile } from '@/hooks/useIsMobile';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { isFormValidationError, useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { requireUiPagination, uiDateRange } from '@/hooks/usePageUiEditorCommands';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { message } from '@/lib/antdMessage';
import { formatOaApiError, OaApiError } from '@/lib/oaApi';
import { genericApprovalTaskApi as api, type GenericApprovalTaskDetail, type GenericAddSignItem } from '@/lib/genericApprovalTaskApi';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';
import { ApprovalAttachmentList } from './ApprovalAttachmentField';
import GenericTaskCollaboration from './GenericTaskCollaboration';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';

const STATUSES = ['', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'];
export default function GenericApprovalInbox() {
  const { t } = useTranslation(), router = useRouter(), params = useSearchParams(), { allowed } = usePermission('todo:read');
  const [status, setStatus] = useState('PENDING'), [page, setPage] = useState(1), [size, setSize] = useState(20), [range, setRange] = useState<[string, string]>();
  const query = useCallback(() => allowed ? api.mine({ status: status || undefined, from: range?.[0], to: range?.[1], page, size }) : Promise.resolve(null), [allowed, status, range, page, size]);
  const overview = useScopedOverview(query), { capture } = useAsyncActionGuard(overview.scope);
  const raw = params.get('taskId'), id = raw && /^[1-9]\d*$/.test(raw) && Number.isSafeInteger(Number(raw)) ? Number(raw) : undefined;
  const open = async (value: string | number) => {
    const current = capture();
    if (!allowed || !current() || !overview.isCurrent() || typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1) throw new Error('PAGE_UI_FIELD_REJECTED');
    const detail = await api.detail(value);
    if (!current() || !overview.isCurrent()) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (detail.task.id !== value) throw new Error('PAGE_UI_FIELD_REJECTED');
    router.push(`/oa/todo?kind=generic&taskId=${value}`);
  };
  usePublishPageAgentContext({ businessType: 'GENERIC_APPROVAL', status, from: range?.[0], to: range?.[1], page, size });
  useRegisterPageUiCommands({
    'ui.refreshPage': async () => { if (!allowed || !overview.isCurrent() || !await overview.load()) throw new Error('PAGE_UI_FIELD_REJECTED'); },
    'ui.applyFilter': ({ payload: { filters } }) => {
      if (!allowed || !overview.isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(filters, { status: STATUSES, from: 'string', to: 'string', page: 'number', size: 'number' }); requireUiPagination(filters);
      if (typeof filters.size === 'number' && filters.size > 50) throw new Error('PAGE_UI_FIELD_REJECTED');
      const next = uiDateRange(filters, range); if (filters.status !== undefined) setStatus(String(filters.status));
      setRange(next); setPage(Number(filters.page ?? 1)); if (typeof filters.size === 'number') setSize(filters.size);
    },
    'ui.openDetail': ({ payload: { recordId } }) => open(recordId),
  }, null, 'generic');
  const tryOpen = (value: number) => { const current = capture(); void open(value).catch(error => { if (current() && overview.isCurrent()) message.error(formatOaApiError(error)); }); };
  return <section className="leave-list-workbench">
    <header className="leave-list-hero"><div><Typography.Title level={2}>{t('approval.genericTasks.title')}</Typography.Title>
      <Typography.Paragraph>{t('approval.genericTasks.description')}</Typography.Paragraph></div></header>
    <Card className="leave-list-card" variant="borderless">
      <Alert type="info" showIcon message={t('approval.genericTasks.agentPending')} />
      {!allowed && <Alert type="warning" showIcon message={t('oa.ai.noPermission')} />}
      {raw && id === undefined && <Alert type="error" showIcon message={t('approval.genericTasks.invalidId')} />}
      {overview.error != null && <Alert type="error" showIcon message={formatOaApiError(overview.error)} action={<Button onClick={() => void overview.load()}>{t('common.retry')}</Button>} />}
      <div className="todo-toolbar"><Segmented disabled={!allowed} value={status} options={STATUSES.map(value => ({ value, label: value ? t(`pages.todo.status${value === 'PENDING' ? 'Pending' : value === 'APPROVED' ? 'Approved' : value === 'REJECTED' ? 'Rejected' : 'Cancelled'}`) : t('common.all') }))}
        onChange={value => { setStatus(String(value)); setPage(1); }} />
        <Space wrap><DatePicker.RangePicker showTime disabled={!allowed} value={range ? [dayjs(range[0]), dayjs(range[1])] : null}
          onChange={dates => { setRange(dates?.[0] && dates[1] ? [dates[0].format('YYYY-MM-DDTHH:mm:ss'), dates[1].format('YYYY-MM-DDTHH:mm:ss')] : undefined); setPage(1); }} />
          <Button disabled={!allowed} icon={<OaIcon name="reload" />} onClick={() => void overview.load()}>{t('common.refresh')}</Button></Space></div>
      <ResponsiveTable rowKey="id" loading={overview.loading && allowed} dataSource={allowed ? overview.data?.records ?? [] : []} scroll={{ x: 900 }}
        locale={{ emptyText: <Empty description={t('approval.genericTasks.empty')} /> }} columns={[
          { title: t('approval.genericTasks.request'), render: (_, row) => <Space direction="vertical" size={0}><Typography.Text strong>{row.title || t('approval.genericTasks.legacyTitle')}</Typography.Text><Typography.Text type="secondary">#{row.applicationId} · {row.formName}</Typography.Text></Space> },
          { title: t('approval.approvalDetail.applicantLabel'), dataIndex: 'applicantName' },
          { title: t('approval.genericTasks.node'), render: (_, row) => row.nodeName || t('approval.genericTasks.legacyNode') },
          { title: t('approval.approvalDetail.descDueAt'), render: (_, row) => <Space>{row.dueAt ? dayjs(row.dueAt).format('YYYY-MM-DD HH:mm') : '-'}{row.overdue && <Tag color="error">{t('approval.approvalDetail.overdue')}</Tag>}</Space> },
          { title: t('common.status'), render: (_, row) => <Tag>{t(`approval.status.${row.status}`)}</Tag> },
          { title: t('common.actions'), render: (_, row) => <Button type="link" disabled={!allowed || !row.hasSnapshot} onClick={() => tryOpen(row.id)}>{row.hasSnapshot ? t('common.view') : t('approval.genericTasks.snapshotMissing')}</Button> },
        ]} pagination={{ current: page, pageSize: size, total: allowed ? overview.data?.total ?? 0 : 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50], onChange: (next, count) => { setPage(next); setSize(count); } }} />
    </Card>
    {allowed && id !== undefined && <GenericTaskDrawer key={`${overview.scope}:${id}`} id={id} onClose={() => router.push('/oa/todo?kind=generic')} onUpdated={() => void overview.load()} />}
  </section>;
}

function GenericTaskDrawer({ id, onClose, onUpdated }: { id: number; onClose: () => void; onUpdated: () => void }) {
  const { t } = useTranslation(), [page, setPage] = useState(1), mobile = useIsMobile();
  const query = useCallback(async () => { const value = await api.detail(id, page); if (value.task.id !== id || value.fieldPage !== page) throw new Error('PAGE_UI_FIELD_REJECTED'); return value; }, [id, page]);
  const overview = useScopedOverview(query), data = overview.data;
  const currentTask = !overview.loading && overview.error == null && overview.isCurrent() && data
    && Number.isSafeInteger(data.task.version) && data.task.version >= 0 ? data.task : undefined;
  usePublishPageAgentContext({ taskId: currentTask?.id, taskVersion: currentTask?.version });
  return <Drawer open width={mobile ? '100%' : 680} title={t('approval.genericTasks.detailTitle', { id })} onClose={onClose}>
    {overview.error != null && <Alert type="error" showIcon message={formatOaApiError(overview.error)} action={<Button onClick={() => void overview.load()}>{t('common.retry')}</Button>} />}
    <Spin spinning={overview.loading}>{data ? <>
      <Typography.Title level={4}>{data.task.title}</Typography.Title>
      <Space wrap><Tag>{t(`approval.status.${data.applicationStatus}`)}</Tag><Typography.Text>{data.task.applicantName}</Typography.Text>
        <Typography.Text>{data.task.nodeName || t('approval.genericTasks.legacyNode')}</Typography.Text>
        {data.task.addSignMode && <Tag color="purple">{t(`approval.genericTasks.signMode.${data.task.addSignMode}`)}</Tag>}
        {data.task.parentTaskId != null && <Typography.Text type="secondary">{t('approval.genericTasks.parentTask', { id: data.task.parentTaskId })}</Typography.Text>}</Space>
      {data.blockedReason && <Alert type="warning" showIcon message={data.blockedReason} />}
      <Descriptions column={1} bordered size="small" items={data.fields.map(field => ({ key: field.name, label: field.label,
        children: field.redacted ? <Tag>{t('approval.genericApplications.redacted')}</Tag>
          : field.type === 'file' || field.type === 'image'
            ? <ApprovalAttachmentList applicationId={data.task.applicationId} fieldName={field.name} ids={field.values}
              taskId={data.task.id} active={true} scope={`task:${data.task.id}:${data.task.version}`} />
            : field.values.join(' / ') || '-' }))} />
      <Pagination size="small" current={page} pageSize={50} total={data.totalFields} showSizeChanger={false} onChange={setPage} />
      {Boolean(data.addSignTasks?.length) && <Card size="small" title={t('approval.genericTasks.addSignQueueTitle')}>
        <ResponsiveTable<GenericAddSignItem> rowKey="taskId" dataSource={data.addSignTasks} pagination={false} scroll={{ x: 540 }} columns={[
          { title: t('approval.genericTasks.recipient'), dataIndex: 'assigneeName' },
          { title: t('approval.genericTasks.signModeLabel'), render: (_, row) => <Tag>{t(`approval.genericTasks.signMode.${row.mode}`)}</Tag> },
          { title: t('common.status'), render: (_, row) => <Tag>{t(`approval.status.${row.status}`)}</Tag> },
          { title: t('approval.approvalDetail.descDueAt'), render: (_, row) => row.dueAt ? dayjs(row.dueAt).format('YYYY-MM-DD HH:mm') : '-' },
        ]} />
      </Card>}
      <Timeline items={data.timeline.map(item => ({ children: <Space direction="vertical" size={0}><Typography.Text>{item.actorName} · {t(`approval.action.${item.action}`)}</Typography.Text>
        {item.originalAssigneeName && <Typography.Text type="secondary">{t('approval.genericTasks.originalAssignee')}: {item.originalAssigneeName}</Typography.Text>}
        {item.targetUserName && <Typography.Text type="secondary">{t('approval.genericTasks.recipient')}: {item.targetUserName}</Typography.Text>}
        {item.comment && <Typography.Text type="secondary">{item.comment}</Typography.Text>}
        <Typography.Text type="secondary">{dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}</Typography.Text></Space> }))} />
      <GenericTaskDecision key={overview.scope} data={data} scope={overview.scope} isCurrent={overview.isCurrent} loading={overview.loading} reload={overview.load} onUpdated={onUpdated} />
      <GenericTaskCollaboration key={`collaboration:${overview.scope}:${data.task.version}`} data={data} scope={overview.scope} isCurrent={overview.isCurrent} loading={overview.loading}
        reload={overview.load} onUpdated={onUpdated} onTransferred={onClose} />
    </> : <Empty description={t('approval.approvalDetail.empty')} />}</Spin>
  </Drawer>;
}

function GenericTaskDecision({ data, scope, isCurrent, loading, reload, onUpdated }: { data: GenericApprovalTaskDetail; scope: string;
  isCurrent: () => boolean; loading: boolean; reload: () => Promise<unknown>; onUpdated: () => void }) {
  const { t } = useTranslation(), { allowed } = usePermission('approval:act');
  const [decision, setDecision] = useState<'approve' | 'reject'>(), [form] = Form.useForm<{ comment?: string }>();
  const action = useAsyncFormAction(scope), modal = useAsyncActionGuard(`${scope}:${decision}`);
  const submit = async () => {
    if (!allowed || !decision || loading || !isCurrent()) return;
    const write = action.begin(), current = modal.capture(); if (!write) return;
    const valid = () => write.current() && current() && isCurrent(); let dispatched = false;
    try {
      const values = await form.validateFields(); if (!valid()) return;
      const latest = await api.detail(data.task.id); if (!valid()) return;
      if (latest.task.id !== data.task.id || latest.task.applicationId !== data.task.applicationId || latest.task.version !== data.task.version || !(decision === 'approve' ? latest.canApprove : latest.canReject)) throw new Error('TASK_CHANGED');
      dispatched = true;
      const result = decision === 'approve' ? await api.approve(data.task.id, data.task.version, values.comment?.trim()) : await api.reject(data.task.id, data.task.version, values.comment!.trim());
      if (!valid()) return;
      if (result.task.id !== data.task.id || result.task.applicationId !== data.task.applicationId || !Number.isSafeInteger(result.task.version) || result.task.version <= data.task.version
          || result.task.status !== (decision === 'approve' ? 'APPROVED' : 'REJECTED')
          || !(decision === 'approve' ? ['PENDING', 'APPROVED'] : ['REJECTED']).includes(result.applicationStatus)) throw new Error('INVALID_RECEIPT');
      message.success(t(decision === 'reject' ? 'approval.approvalDetail.rejectSuccess' : result.applicationStatus === 'PENDING' ? 'approval.approvalDetail.approveNextSuccess' : 'approval.approvalDetail.approveSuccess'));
      setDecision(undefined); form.resetFields(); onUpdated(); await reload();
    } catch (error) {
      if (valid() && !isFormValidationError(error)) {
        message.error(error instanceof Error && error.message === 'TASK_CHANGED' ? t('approval.approvalDetail.changedBeforeAction')
          : error instanceof Error && error.message === 'INVALID_RECEIPT' ? t('approval.genericApplications.invalidResult')
          : dispatched && (!(error instanceof OaApiError) || error.status >= 500) ? t('approval.approvalDetail.resultUnknown') : formatOaApiError(error));
        setDecision(undefined); form.resetFields(); await reload();
      }
    } finally { write.finish(); }
  };
  if (!allowed || !data.canReject && !data.canApprove) return null;
  return <><Space wrap><Button danger disabled={!data.canReject || loading || action.busy} onClick={() => { modal.invalidate(); setDecision('reject'); }}>{t('approval.approvalDetail.rejectButton')}</Button>
    <Button type="primary" disabled={!data.canApprove || loading || action.busy} onClick={() => { modal.invalidate(); setDecision('approve'); }}>{t('approval.approvalDetail.approveButton')}</Button></Space>
    <Modal open={Boolean(decision)} title={t('approval.genericTasks.confirmTitle')} okText={t('common.confirm')} cancelText={t('common.cancel')}
      confirmLoading={action.busy} okButtonProps={{ danger: decision === 'reject' }} cancelButtonProps={{ disabled: action.busy }} closable={!action.busy} maskClosable={!action.busy} keyboard={!action.busy}
      onCancel={() => { if (!action.busy) { modal.invalidate(); setDecision(undefined); form.resetFields(); } }} onOk={() => void submit()}>
      <Alert type="info" showIcon message={t('approval.approvalDetail.actionSubject', { id: data.task.applicationId, task: data.task.id, version: data.task.version })} />
      <Form form={form} layout="vertical" disabled={action.busy}><Form.Item name="comment" label={t('approval.approvalDetail.commentLabel')}
        rules={[{ required: decision === 'reject', whitespace: true, message: t('approval.approvalDetail.commentRejectRequired') }, { max: 500 }]}><Input.TextArea rows={4} maxLength={500} showCount /></Form.Item></Form>
    </Modal></>;
}
