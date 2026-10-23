'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Card, Descriptions, Drawer, Empty, Input, Select, Space } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { approvalEngineApi, type ApprovalConfigStatus, type ApprovalForm, type ApprovalProcess, type ApprovalProcessPayload } from '@/lib/approvalEngineApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { OaIcon } from '@/components/OaIcon';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { parseRecordId } from '@/lib/recordSelection';
import { usePublishPageAgentContext } from './PageAgentContext';
import ApprovalConfigShell from './ApprovalConfigShell';
import ProcessDesignerModal from './ProcessDesignerModal';
import { StatusTag } from './FormEnginePage';
import ResponsiveTable from './ResponsiveTable';

function nodeCount(json: string): number {
  try { const value: unknown = JSON.parse(json); return Array.isArray(value) ? value.length : 0; } catch { return 0; }
}

export default function ProcessConfigPage() {
  const { t } = useTranslation(); const { modal } = App.useApp(); const { user } = useAuth();
  const { hasPermission, permissionVersion } = usePermission('route:process-config');
  const canRead = hasPermission('route:process-config') && hasPermission('approval:read');
  const canManage = canRead && hasPermission('approval:manage');
  const scope = `${user?.id}:${user?.tenantId}:${permissionVersion}:${canRead}:${canManage}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const [dataScope, setDataScope] = useState(scope);
  const [storedData, setData] = useState<ApprovalProcess[]>([]); const [storedTotal, setTotal] = useState(0);
  const data = dataScope === scope ? storedData : []; const total = dataScope === scope ? storedTotal : 0;
  const [loading, setLoading] = useState(false); const [page, setPage] = useState(1);
  const [search, setSearch] = useState(''); const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<ApprovalConfigStatus>();
  const [detail, setDetail] = useState<ApprovalProcess>(); const [detailOpen, setDetailOpen] = useState(false); const [detailLoading, setDetailLoading] = useState(false);
  const [forms, setForms] = useState<ApprovalForm[]>([]); const [designerOpen, setDesignerOpen] = useState(false);
  const [designerEditing, setDesignerEditing] = useState<ApprovalProcess | null>(null); const [editorLoading, setEditorLoading] = useState(false);
  const listRequest = useRef(0); const detailRequest = useRef(0); const editorRequest = useRef(0);
  const selectedId = useRef<number | undefined>(undefined);
  const confirmation = useRef<{ destroy: () => void } | undefined>(undefined); const confirmEpoch = useRef(0);
  const dismissConfirmation = useCallback(() => { confirmEpoch.current += 1; confirmation.current?.destroy(); confirmation.current = undefined; }, []);
  const router = useRouter(); const requestedId = useSearchParams().get('id');
  const selected = dataScope !== scope ? undefined : designerOpen ? designerEditing ?? undefined : detailOpen ? detail : undefined;
  usePublishPageAgentContext({ processId: selected?.id, version: selected?.version, keyword: keyword || undefined, status, page, size: 20 });

  useEffect(() => {
    activeScope.current = scope; dismissConfirmation(); listRequest.current += 1; detailRequest.current += 1; editorRequest.current += 1;
    selectedId.current = undefined; setDataScope(scope); setData([]); setTotal(0); setForms([]);
    setDetail(undefined); setDetailOpen(false); setDetailLoading(false); setDesignerEditing(null); setDesignerOpen(false); setEditorLoading(false);
    return () => { activeScope.current = undefined; dismissConfirmation(); listRequest.current += 1; detailRequest.current += 1; editorRequest.current += 1; selectedId.current = undefined; };
  }, [scope, dismissConfirmation]);
  const load = useCallback(async () => {
    const request = ++listRequest.current; if (!canRead) { setLoading(false); return; } setLoading(true);
    try {
      const result = await approvalEngineApi.listProcesses({ keyword: keyword || undefined, status, page, size: 20 });
      if (request !== listRequest.current || activeScope.current !== scope) return;
      setData(result.records); setTotal(result.total);
    } catch (error) { if (request === listRequest.current && activeScope.current === scope) { setData([]); setTotal(0); message.error(formatOaApiError(error)); } }
    finally { if (request === listRequest.current && activeScope.current === scope) setLoading(false); }
  }, [canRead, keyword, status, page, scope]);
  const latestLoad = useRef(load); latestLoad.current = load;
  useEffect(() => { void load(); }, [load]);
  const loadDetail = useCallback(async (id: number) => {
    if (!canRead) return; dismissConfirmation(); const request = ++detailRequest.current;
    selectedId.current = id; setDetail(undefined); setDetailOpen(true); setDetailLoading(true);
    try {
      const result = await approvalEngineApi.getProcess(id);
      if (request !== detailRequest.current || activeScope.current !== scope) return;
      if (result.id !== id) throw new Error('RESOURCE_MISMATCH'); setDetail(result);
    } catch (error) { if (request === detailRequest.current && activeScope.current === scope) {
      selectedId.current = undefined; setDetail(undefined); setDetailOpen(false); message.error(formatOaApiError(error));
    } } finally { if (request === detailRequest.current && activeScope.current === scope) setDetailLoading(false); }
  }, [canRead, scope, dismissConfirmation]);
  const closeWindows = () => {
    dismissConfirmation(); detailRequest.current += 1; editorRequest.current += 1; selectedId.current = undefined;
    setDetail(undefined); setDetailOpen(false); setDetailLoading(false); setDesignerOpen(false); setDesignerEditing(null); setEditorLoading(false); setForms([]);
  };
  useEffect(() => {
    dismissConfirmation(); detailRequest.current += 1; editorRequest.current += 1; selectedId.current = undefined;
    setDetail(undefined); setDetailOpen(false); setDetailLoading(false); setDesignerOpen(false); setDesignerEditing(null); setEditorLoading(false); setForms([]);
    const id = parseRecordId(requestedId); if (id && canRead) void loadDetail(id);
    return () => { dismissConfirmation(); detailRequest.current += 1; editorRequest.current += 1; selectedId.current = undefined; };
  }, [requestedId, canRead, scope, loadDetail, dismissConfirmation]);
  const close = () => { closeWindows(); if (requestedId !== null) router.replace('/oa/process-config'); };
  const showDetail = (id: number, replace = false) => {
    if (!canRead) return; closeWindows();
    if (requestedId === String(id)) void loadDetail(id);
    else if (replace) router.replace(`/oa/process-config?id=${id}`); else router.push(`/oa/process-config?id=${id}`);
  };
  const openEditor = async (record?: ApprovalProcess) => {
    if (!canManage || (record && !record.canEdit)) return;
    closeWindows(); const request = ++editorRequest.current; setDesignerOpen(true); setEditorLoading(true);
    try {
      const [options, fresh] = await Promise.all([approvalEngineApi.listForms({ page: 1, size: 100 }),
        record ? approvalEngineApi.getProcess(record.id) : Promise.resolve(undefined)]);
      if (request !== editorRequest.current || activeScope.current !== scope) return;
      if (record && (!fresh || fresh.id !== record.id || !fresh.canEdit)) throw new Error('RESOURCE_MISMATCH');
      setForms(options.records); setDesignerEditing(fresh ?? null); selectedId.current = fresh?.id;
    } catch (error) { if (request === editorRequest.current && activeScope.current === scope) { closeWindows(); message.error(formatOaApiError(error)); } }
    finally { if (request === editorRequest.current && activeScope.current === scope) setEditorLoading(false); }
  };
  const save = async (payload: ApprovalProcessPayload, request: number) => {
    if (!canManage || !designerOpen || editorLoading || (designerEditing && !designerEditing.canEdit) || request !== editorRequest.current || activeScope.current !== scope) return;
    const identity = scope; const editing = designerEditing;
    try {
      const result = editing ? await approvalEngineApi.updateProcess(editing.id, { ...payload, version: editing.version }) : await approvalEngineApi.createProcess(payload);
      if (activeScope.current !== identity) return;
      if (request === editorRequest.current) {
        message.success(t(payload.status === 'ENABLED' ? 'approval.designer.publishSuccess' : 'approval.designer.draftSuccess'));
        showDetail(result.id, true);
      }
      await latestLoad.current();
    } catch (error) { if (request === editorRequest.current && activeScope.current === identity) message.error(formatOaApiError(error)); }
  };
  const remove = (record: ApprovalProcess) => {
    if (!canManage || !record.canDelete) return; dismissConfirmation(); const epoch = confirmEpoch.current;
    const request = detailRequest.current; const editor = editorRequest.current;
    const isCurrent = () => activeScope.current === scope && epoch === confirmEpoch.current && request === detailRequest.current && editor === editorRequest.current;
    confirmation.current = modal.confirm({ title: t('approval.config.common.deleteConfirm'), okText: t('common.delete'), cancelText: t('common.cancel'), okButtonProps: { danger: true },
      onCancel: () => { if (epoch === confirmEpoch.current) dismissConfirmation(); }, onOk: async () => {
        if (!isCurrent()) return;
        try { await approvalEngineApi.deleteProcess(record.id); if (!isCurrent()) return;
          message.success(t('approval.config.common.deleteSuccess')); if (selectedId.current === record.id) close(); await latestLoad.current();
        } catch (error) { if (isCurrent()) { message.error(formatOaApiError(error)); throw error; } }
      } });
  };
  const refresh = async () => {
    const identity = scope; const id = selectedId.current; const request = detailRequest.current;
    await latestLoad.current();
    if (activeScope.current === identity && id !== undefined && request === detailRequest.current && !designerOpen) await loadDetail(id);
  };
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
  const editorTicket = editorRequest.current;
  return <ApprovalConfigShell eyebrow={t('approval.config.eyebrow')} title={t('approval.config.process.title')} description={t('approval.config.process.description')}
    actions={canManage && <Button type="primary" onClick={() => void openEditor()}>{t('approval.config.process.create')}</Button>}>
    <Card className="leave-list-card" variant="borderless"><div className="leave-list-toolbar">
      <Input.Search placeholder={t('approval.config.common.searchPlaceholder')} allowClear value={search} onChange={event => setSearch(event.target.value)} onSearch={value => { setKeyword(value.trim()); setPage(1); }} />
      <Space wrap><Select allowClear placeholder={t('approval.config.common.status')} value={status} onChange={value => { setStatus(value); setPage(1); }}
        options={(['ENABLED', 'DISABLED'] as const).map(value => ({ value, label: t(`approval.config.common.statusOption.${value}`) }))} />
        <Button icon={<OaIcon name="reload" />} onClick={() => void refresh()}>{t('common.refresh')}</Button></Space>
    </div><ResponsiveTable rowKey="id" columns={columns} dataSource={data} loading={loading} size="middle" scroll={{ x: 980 }}
      locale={{ emptyText: <Empty description={t('approval.config.common.noData')} /> }}
      pagination={{ current: page, pageSize: 20, total, showSizeChanger: false, onChange: setPage }} /></Card>
    <Drawer open={detailOpen && canRead && dataScope === scope} loading={detailLoading} onClose={close} width={600} title={t('approval.config.process.detailTitle')}
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
    {designerOpen && canManage && dataScope === scope && <ProcessDesignerModal key={editorTicket} open editing={designerEditing} forms={forms} loading={editorLoading}
      onClose={close} onSubmit={payload => save(payload, editorTicket)} />}
  </ApprovalConfigShell>;
}
