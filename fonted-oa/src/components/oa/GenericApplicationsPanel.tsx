import { useCallback, useRef, useState } from 'react';
import { Alert, Button, Card, Descriptions, Empty, Input, Modal, Pagination, Select, Space, Spin, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useTranslation } from 'react-i18next';
import { approvalEngineApi, type ApprovalApplication } from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import { useScopedOverview } from '@/hooks/useScopedOverview';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { useScopedModalConfirm } from '@/hooks/useScopedModalConfirm';
import { parseRecordId } from '@/lib/recordSelection';
import { usePublishPageAgentContext } from './PageAgentContext';
import { useRegisterPageUiCommands } from './PageUiCommandBridge';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { requireUiPagination } from '@/hooks/usePageUiEditorCommands';
import ResponsiveTable from './ResponsiveTable';

const STATUSES: ApprovalApplication['status'][] = ['DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'CANCELLED'];
type Action = 'submit' | 'cancel' | 'withdraw' | 'reopen' | 'remind';

export default function GenericApplicationsPanel() {
  const { t } = useTranslation();
  const { user } = useAuth();
  const { allowed, hasPermission } = usePermission(['approval:mine:read', 'route:approval-start']);
  const router = useRouter(), rawId = useSearchParams().get('id');
  const selectedId = parseRecordId(rawId);
  const [page, setPage] = useState(1), [size, setSize] = useState(20);
  const [status, setStatus] = useState<ApprovalApplication['status']>();
  const [formKey, setFormKey] = useState(''), [searchText, setSearchText] = useState('');
  const [fieldSelection, setFieldSelection] = useState<{ id?: number; page: number }>({ page: 1 });
  const fieldPage = fieldSelection.id === selectedId ? fieldSelection.page : 1;
  const query = useCallback(async () => {
    if (!allowed) return null;
    const result = await approvalEngineApi.listMyApplications({ status, formKey: formKey || undefined, page, size });
    if (result.records.some(item => item.applicantUserId !== user?.id)) throw new Error(t('approval.genericApplications.invalidResult'));
    return result;
  }, [allowed, formKey, page, size, status, t, user?.id]);
  const list = useScopedOverview(query);
  const records = allowed ? list.data?.records ?? [] : [];
  const currentRecords = useRef(records); currentRecords.current = records;
  const detailQuery = useCallback(async () => {
    if (!allowed || !selectedId) return null;
    const result = await approvalEngineApi.ownApplicationFields(selectedId, fieldPage);
    if (result.application.applicationId !== selectedId || result.fieldPage !== fieldPage || result.fields.length > 50)
      throw new Error(t('approval.genericApplications.invalidResult'));
    return result;
  }, [allowed, selectedId, fieldPage, t]);
  const selected = useScopedOverview(detailQuery);
  const detail = allowed ? selected.data : null;
  const action = useAsyncFormAction(list.scope);
  const confirm = useScopedModalConfirm(list.scope);
  const { capture } = useAsyncActionGuard(list.scope);
  const details = useRef(0);
  usePublishPageAgentContext({ applicationId: selectedId?.toString(), status, formKey: formKey || undefined, page, size });

  const open = async (recordId: string | number) => {
    const current = capture();
    if (!allowed || !current() || !list.isCurrent() || typeof recordId !== 'number' || !Number.isSafeInteger(recordId) || recordId < 1)
      throw new Error('PAGE_UI_FIELD_REJECTED');
    const ticket = ++details.current;
    const result = await approvalEngineApi.ownApplicationFields(recordId, 1);
    if (!current() || !list.isCurrent() || ticket !== details.current) throw new Error('PAGE_UI_COMMAND_REJECTED');
    if (result.application.applicationId !== recordId) throw new Error('PAGE_UI_FIELD_REJECTED');
    router.replace(`/oa/my-applications?kind=generic&id=${recordId}`);
  };
  useRegisterPageUiCommands({
    'ui.refreshPage': async () => { if (!allowed || !list.isCurrent() || !await list.load()) throw new Error('PAGE_UI_FIELD_REJECTED'); },
    'ui.openDetail': ({ payload: { recordId } }) => open(recordId),
    'ui.applyFilter': ({ payload: { filters } }) => {
      if (!allowed || !list.isCurrent()) throw new Error('PAGE_UI_FIELD_REJECTED');
      requireUiFieldTypes(filters, { status: ['', ...STATUSES], formKey: 'string', page: 'number', size: 'number' });
      requireUiPagination(filters);
      if (typeof filters.formKey === 'string' && filters.formKey.length > 64) throw new Error('PAGE_UI_FIELD_REJECTED');
      if (filters.status !== undefined) setStatus((filters.status || undefined) as ApprovalApplication['status'] | undefined);
      if (typeof filters.formKey === 'string') { setFormKey(filters.formKey); setSearchText(filters.formKey); }
      setPage(Number(filters.page ?? 1)); if (typeof filters.size === 'number') setSize(filters.size);
    },
  }, null, 'generic-applications');

  const edit = (item: ApprovalApplication) => router.push(`/oa/approval-form?formKey=${encodeURIComponent(item.formKey)}&draftId=${item.id}`);
  const may = (item: ApprovalApplication, operation: Action) => allowed && (operation === 'submit' ? item.canEditDraft && hasPermission('approval:submit')
    : operation === 'cancel' ? item.canCancel && hasPermission('approval:cancel')
    : operation === 'withdraw' ? item.canWithdraw && hasPermission('approval:withdraw')
    : operation === 'reopen' ? ['REJECTED', 'WITHDRAWN'].includes(item.status) && hasPermission('approval:reopen')
    : item.canRemind && hasPermission('approval:remind'));
  const perform = (item: ApprovalApplication, operation: Action) => {
    if (!may(item, operation) || !list.isCurrent()) return;
    confirm({ title: t('approval.genericApplications.confirm', { action: t(`approval.genericApplications.${operation}`), id: item.id }),
      onOk: async () => {
        if (!list.isCurrent() || !may(item, operation) || !currentRecords.current.some(row => row.id === item.id && row.version === item.version)) return;
        const write = action.begin(); if (!write) return;
        try {
          const operations = { submit: approvalEngineApi.submitDraft, cancel: approvalEngineApi.cancelDraft,
            withdraw: approvalEngineApi.withdrawApplication, reopen: approvalEngineApi.reopenApplication, remind: approvalEngineApi.remindApplication };
          const result = await operations[operation](item.id, item.version);
          if (!write.current() || !list.isCurrent()) return;
          const expected = { submit: 'PENDING', cancel: 'CANCELLED', withdraw: 'WITHDRAWN', reopen: 'DRAFT', remind: 'PENDING' }[operation];
          if (result.id !== item.id || result.applicantUserId !== user?.id || result.formKey !== item.formKey
              || result.status !== expected || !Number.isInteger(result.version) || result.version <= item.version)
            throw new Error(t('approval.genericApplications.invalidResult'));
          message.success(t('approval.genericApplications.actionDone', { action: t(`approval.genericApplications.${operation}`) }));
          await list.load();
          if (!write.current()) return;
          if (selectedId === item.id) await selected.load();
          if (operation === 'reopen' && hasPermission('approval:create') && hasPermission('route:approval-form')) edit(result);
        } catch (failure) {
          if (write.current() && list.isCurrent()) { message.error(formatOaApiError(failure)); await list.load(); }
        } finally { write.finish(); }
      } });
  };
  const columns: ColumnsType<ApprovalApplication> = [
    { title: t('approval.genericApplications.application'), key: 'application', render: (_, item) => <Button type="link" onClick={() => { void open(item.id).catch(failure => { if (list.isCurrent()) message.error(formatOaApiError(failure)); }); }}>{item.formName} #{item.id}</Button> },
    { title: t('approval.genericApplications.status'), dataIndex: 'status', render: value => <Tag>{t(`approval.status.${value}`)}</Tag> },
    { title: t('approval.genericApplications.submittedAt'), dataIndex: 'submittedAt', render: value => value || '-' },
    { title: t('approval.genericApplications.version'), dataIndex: 'version' },
    { title: t('common.actions'), key: 'actions', render: (_, item) => <Space wrap>
      {item.canEditDraft && hasPermission('approval:create') && hasPermission('route:approval-form') && <Button type="link" disabled={action.busy} onClick={() => edit(item)}>{t('common.edit')}</Button>}
      {(['submit', 'cancel', 'withdraw', 'reopen', 'remind'] as Action[]).filter(operation => may(item, operation))
        .map(operation => <Button key={operation} type="link" disabled={action.busy} onClick={() => perform(item, operation)}>{t(`approval.genericApplications.${operation}`)}</Button>)}
    </Space> },
  ];
  return <section className="oa-domain-page">
    <div className="oa-domain-heading"><div><Typography.Title level={2}>{t('approval.genericApplications.title')}</Typography.Title><Typography.Paragraph>{t('approval.genericApplications.description')}</Typography.Paragraph></div>
      {allowed && hasPermission('approval:create') && <Button type="primary" onClick={() => router.push('/oa/approval-start')}>{t('approval.genericApplications.create')}</Button>}</div>
    <Card className="oa-admin-assets-card oa-admin-assets-card--fill">
      <Space wrap><Select allowClear disabled={!allowed} value={status} placeholder={t('approval.genericApplications.status')} style={{ width: 160 }} options={STATUSES.map(value => ({ value, label: t(`approval.status.${value}`) }))} onChange={value => { setStatus(value); setPage(1); }} />
        <Input.Search allowClear disabled={!allowed} maxLength={64} value={searchText} onChange={event => setSearchText(event.target.value)} placeholder={t('approval.genericApplications.formKey')} onSearch={value => { setFormKey(value); setPage(1); }} />
        <Button disabled={!allowed} onClick={() => void list.load()}>{t('common.refresh')}</Button></Space>
      {!allowed && <Alert type="warning" showIcon title={t('oa.ai.noPermission')} />}
      {list.error !== undefined && <Alert type="error" showIcon title={formatOaApiError(list.error)} action={<Button onClick={() => void list.load()}>{t('common.retry')}</Button>} />}
      {rawId && !selectedId && <Alert type="error" title={t('approval.genericApplications.invalidResult')} />}
      <ResponsiveTable rowKey="id" columns={columns} dataSource={records} loading={list.loading && allowed}
        pagination={{ current: page, pageSize: size, total: list.data?.total ?? 0, pageSizeOptions: [10, 20, 50, 100], onChange: (value, count) => { setPage(value); setSize(count); } }}
        locale={{ emptyText: <Empty description={t('common.empty')} /> }} />
    </Card>
    <Modal open={allowed && Boolean(selectedId)} title={detail?.application.formName || t('approval.genericApplications.application')}
      onCancel={() => router.replace('/oa/my-applications?kind=generic')} destroyOnHidden
      footer={<Button onClick={() => router.replace('/oa/my-applications?kind=generic')}>{t('common.close')}</Button>} width={720}>
      <Spin spinning={selected.loading}>{selected.error !== undefined ? <Alert showIcon type="error" title={formatOaApiError(selected.error)} /> : detail ? <Space orientation="vertical" style={{ width: '100%' }}>
        <Alert type="info" title={t(detail.schemaSource === 'CURRENT_DRAFT' ? 'approval.genericApplications.currentDraftNotice' : 'approval.start.snapshotNotice')} />
        <Descriptions size="small" column={1} bordered items={detail.fields.map(field => ({ key: field.name, label: field.label, children: field.redacted ? t('approval.genericApplications.redacted') : field.values.join(', ') || '-' }))} />
        <Pagination current={fieldPage} pageSize={50} total={detail.fieldsTotal} showSizeChanger={false} onChange={value => setFieldSelection({ id: selectedId, page: value })} />
      </Space> : null}</Spin>
    </Modal>
  </section>;
}
