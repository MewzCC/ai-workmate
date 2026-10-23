'use client';

import { Button, Card, Descriptions, Drawer, Empty, Input, Select, Space, Tag } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { approvalEngineApi, type ApprovalConfigStatus, type ApprovalForm } from '@/lib/approvalEngineApi';
import { OaIcon } from '@/components/OaIcon';
import ApprovalConfigShell from './ApprovalConfigShell';
import FormDesignerModal from './FormDesignerModal';
import ResponsiveTable from './ResponsiveTable';
import { useApprovalDefinitionPage } from './useApprovalDefinitionPage';

export function StatusTag({ status }: { status: ApprovalConfigStatus }) {
  const { t } = useTranslation();
  return <Tag color={status === 'ENABLED' ? 'success' : 'default'}>{t(`approval.config.common.statusOption.${status}`)}</Tag>;
}

function schemaFieldCount(schemaJson: string): number {
  try { const value: unknown = JSON.parse(schemaJson); return value && typeof value === 'object' && 'fields' in value && Array.isArray(value.fields) ? value.fields.length : 0; }
  catch { return 0; }
}
const api = { list: approvalEngineApi.listForms, get: approvalEngineApi.getForm, create: approvalEngineApi.createForm,
  update: approvalEngineApi.updateForm, remove: approvalEngineApi.deleteForm };

export default function FormEnginePage() {
  const { t } = useTranslation();
  const state = useApprovalDefinitionPage({ pageId: 'form-engine', idField: 'formId', api,
    messages: { publish: 'approval.builder.publishSuccess', create: 'approval.config.common.createSuccess', update: 'approval.config.common.updateSuccess' } });
  const { canManage, data, total, loading, page, setPage, search, setSearch, status, setStatus,
    detail, detailOpen, detailLoading, designerOpen, designerEditing, editorLoading, editorTicket,
    close, showDetail, openEditor, save, remove, refresh, applySearch } = state;
  const columns: ColumnsType<ApprovalForm> = [
    { title: t('approval.config.form.columnKey'), dataIndex: 'formKey', render: (value: string, record) => <Button type="link" onClick={() => showDetail(record.id)}>{value}</Button> },
    { title: t('approval.config.form.columnName'), dataIndex: 'formName' },
    { title: t('approval.config.form.columnDescription'), dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '-' },
    { title: t('approval.config.form.columnFields'), key: 'fields', render: (_, record) => t('approval.config.form.fieldsCount', { count: schemaFieldCount(record.schemaJson) }) },
    { title: t('approval.config.common.status'), dataIndex: 'status', render: (value: ApprovalConfigStatus) => <StatusTag status={value} /> },
    { title: t('approval.config.common.version'), dataIndex: 'version' },
    { title: t('approval.config.common.updatedAt'), dataIndex: 'updatedAt', render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm') },
    { title: t('common.actions'), key: 'actions', fixed: 'right', render: (_, record) => <Space>
      <Button type="link" onClick={() => showDetail(record.id)}>{t('common.view')}</Button>
      {canManage && record.canEdit && <Button type="link" onClick={() => void openEditor(record)}>{t('approval.config.common.edit')}</Button>}
      {canManage && record.canDelete && <Button type="link" danger onClick={() => remove(record)}>{t('approval.config.common.delete')}</Button>}
    </Space> },
  ];
  return <ApprovalConfigShell eyebrow={t('approval.config.eyebrow')} title={t('approval.config.form.title')} description={t('approval.config.form.description')}
    actions={canManage && <Button type="primary" onClick={() => void openEditor()}>{t('approval.config.form.create')}</Button>}>
    <Card className="leave-list-card" variant="borderless"><div className="leave-list-toolbar">
      <Input.Search placeholder={t('approval.config.common.searchPlaceholder')} allowClear value={search} onChange={event => setSearch(event.target.value)} onSearch={applySearch} />
      <Space wrap><Select allowClear placeholder={t('approval.config.common.status')} value={status} onChange={value => { setStatus(value); setPage(1); }}
        options={(['ENABLED', 'DISABLED'] as const).map(value => ({ value, label: t(`approval.config.common.statusOption.${value}`) }))} />
        <Button icon={<OaIcon name="reload" />} onClick={() => void refresh()}>{t('common.refresh')}</Button></Space>
    </div><ResponsiveTable rowKey="id" columns={columns} dataSource={data} loading={loading} size="middle" scroll={{ x: 980 }}
      locale={{ emptyText: <Empty description={t('approval.config.common.noData')} /> }}
      pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} /></Card>
    <Drawer open={detailOpen} loading={detailLoading} onClose={close} width={600} title={t('approval.config.form.detailTitle')}
      extra={detail?.canEdit && canManage && <Button onClick={() => void openEditor(detail)}>{t('approval.config.common.edit')}</Button>}>
      {detail && <Descriptions column={1} items={[
        { key: 'key', label: t('approval.config.form.formKey'), children: detail.formKey },
        { key: 'name', label: t('approval.config.form.formName'), children: detail.formName },
        { key: 'version', label: t('approval.config.common.version'), children: detail.version },
        { key: 'status', label: t('approval.config.common.status'), children: <StatusTag status={detail.status} /> },
        { key: 'fields', label: t('approval.config.form.columnFields'), children: t('approval.config.form.fieldsCount', { count: schemaFieldCount(detail.schemaJson) }) },
        { key: 'description', label: t('approval.config.form.descriptionLabel'), children: detail.description || '-' },
      ]} />}
    </Drawer>
    {designerOpen && <FormDesignerModal key={editorTicket} open editing={designerEditing} loading={editorLoading}
      onClose={close} onSubmit={payload => save(payload, editorTicket)} />}
  </ApprovalConfigShell>;
}
