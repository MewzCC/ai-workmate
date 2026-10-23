'use client';

import { Button, Card, Descriptions, Drawer, Empty, Input, Select, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { approvalEngineApi, type ApprovalConfigStatus, type ApprovalProcess } from '@/lib/approvalEngineApi';
import { OaIcon } from '@/components/OaIcon';
import ApprovalConfigShell from './ApprovalConfigShell';
import ProcessDesignerModal from './ProcessDesignerModal';
import { StatusTag } from './FormEnginePage';
import ResponsiveTable from './ResponsiveTable';
import { useApprovalDefinitionPage } from './useApprovalDefinitionPage';

function nodeCount(json: string): number {
  try { const value: unknown = JSON.parse(json); return Array.isArray(value) ? value.length : 0; } catch { return 0; }
}
const api = { list: approvalEngineApi.listProcesses, get: approvalEngineApi.getProcess, create: approvalEngineApi.createProcess,
  update: approvalEngineApi.updateProcess, remove: approvalEngineApi.deleteProcess };
const loadForms = async () => (await approvalEngineApi.listForms({ page: 1, size: 100 })).records;

export default function ProcessConfigPage() {
  const { t } = useTranslation();
  const state = useApprovalDefinitionPage({ pageId: 'process-config', idField: 'processId', api, loadForms,
    messages: { publish: 'approval.designer.publishSuccess', create: 'approval.designer.draftSuccess', update: 'approval.designer.draftSuccess' } });
  const { canManage, data, total, loading, page, setPage, search, setSearch, status, setStatus,
    detail, detailOpen, detailLoading, designerOpen, designerEditing, editorLoading, forms, editorTicket,
    close, showDetail, openEditor, save, remove, refresh, applySearch } = state;
  const columns: ColumnsType<ApprovalProcess> = [
    { title: t('approval.config.process.columnKey'), dataIndex: 'processKey', render: (value: string, record) => <Button type="link" onClick={() => showDetail(record.id)}>{value}</Button> },
    { title: t('approval.config.process.columnName'), dataIndex: 'processName' },
    { title: t('approval.config.process.columnForm'), dataIndex: 'formName', render: (value?: string) => value || t('approval.config.process.unbound') },
    { title: t('approval.config.process.columnNodes'), key: 'nodes', render: (_, record) => t('approval.config.process.nodesCount', { count: nodeCount(record.nodeJson) }) },
    { title: t('approval.config.common.status'), dataIndex: 'status', render: (value: ApprovalConfigStatus) => <StatusTag status={value} /> },
    { title: t('approval.config.common.version'), dataIndex: 'version' },
    { title: t('approval.config.common.updatedAt'), dataIndex: 'updatedAt', render: (value: string) => dayjs(value).format('YYYY-MM-DD HH:mm') },
    { title: t('common.actions'), key: 'actions', fixed: 'right', render: (_, record) => <Space>
      <Button type="link" onClick={() => showDetail(record.id)}>{t('common.view')}</Button>
      {canManage && record.canEdit && <Button type="link" onClick={() => void openEditor(record)}>{t('approval.config.common.edit')}</Button>}
      {canManage && record.canDelete && <Button type="link" danger onClick={() => remove(record)}>{t('approval.config.common.delete')}</Button>}
    </Space> },
  ];
  return <ApprovalConfigShell eyebrow={t('approval.config.eyebrow')} title={t('approval.config.process.title')} description={t('approval.config.process.description')}
    actions={canManage && <Button type="primary" onClick={() => void openEditor()}>{t('approval.config.process.create')}</Button>}>
    <Card className="leave-list-card" variant="borderless"><div className="leave-list-toolbar">
      <Input.Search placeholder={t('approval.config.common.searchPlaceholder')} allowClear value={search} onChange={event => setSearch(event.target.value)} onSearch={applySearch} />
      <Space wrap><Select allowClear placeholder={t('approval.config.common.status')} value={status} onChange={value => { setStatus(value); setPage(1); }}
        options={(['ENABLED', 'DISABLED'] as const).map(value => ({ value, label: t(`approval.config.common.statusOption.${value}`) }))} />
        <Button icon={<OaIcon name="reload" />} onClick={() => void refresh()}>{t('common.refresh')}</Button></Space>
    </div><ResponsiveTable rowKey="id" columns={columns} dataSource={data} loading={loading} size="middle" scroll={{ x: 980 }}
      locale={{ emptyText: <Empty description={t('approval.config.common.noData')} /> }}
      pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} /></Card>
    <Drawer open={detailOpen} loading={detailLoading} onClose={close} width={600} title={t('approval.config.process.detailTitle')}
      extra={detail?.canEdit && canManage && <Button onClick={() => void openEditor(detail)}>{t('approval.config.common.edit')}</Button>}>
      {detail && <Descriptions column={1} items={[
        { key: 'key', label: t('approval.config.process.processKey'), children: detail.processKey },
        { key: 'name', label: t('approval.config.process.processName'), children: detail.processName },
        { key: 'form', label: t('approval.config.process.columnForm'), children: detail.formName || t('approval.config.process.unbound') },
        { key: 'version', label: t('approval.config.common.version'), children: detail.version },
        { key: 'status', label: t('approval.config.common.status'), children: <StatusTag status={detail.status} /> },
        { key: 'nodes', label: t('approval.config.process.columnNodes'), children: t('approval.config.process.nodesCount', { count: nodeCount(detail.nodeJson) }) },
        { key: 'description', label: t('approval.config.process.descriptionLabel'), children: detail.description || '-' },
      ]} />}
    </Drawer>
    {designerOpen && <ProcessDesignerModal key={editorTicket} open editing={designerEditing} forms={forms} loading={editorLoading}
      onClose={close} onSubmit={payload => save(payload, editorTicket)} />}
  </ApprovalConfigShell>;
}
