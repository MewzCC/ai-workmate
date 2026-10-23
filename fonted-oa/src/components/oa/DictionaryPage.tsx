'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  App, Button, Card, Col, Descriptions, Drawer, Empty, Form, Input, InputNumber, Row, Segmented,
  Space, Statistic, Switch, Tag, Tooltip, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import { dictionaryApi, type DictionaryItem, type DictionaryItemPayload, type DictionaryStatus,
  type DictionaryType, type DictionaryTypePayload } from '@/lib/dictionaryApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';
import { useAuth } from '@/components/auth/AuthProvider';
import { usePermission } from '@/hooks/usePermission';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import { parseRecordId } from '@/lib/recordSelection';
import { usePublishPageAgentContext } from './PageAgentContext';

type Editor = { kind: 'type'; record?: DictionaryType } | { kind: 'item'; type: DictionaryType; record?: DictionaryItem };
type Selection = { type: DictionaryType; item?: DictionaryItem };
type Target = { kind: 'type' | 'item'; id: number };

async function readSelection(selection: Target): Promise<Selection> {
  if (selection.kind === 'type') {
    const type = await dictionaryApi.typeDetail(selection.id);
    if (type.id !== selection.id) throw new Error('RESOURCE_MISMATCH');
    return { type };
  }
  const result = await dictionaryApi.itemDetail(selection.id);
  if (result.item.id !== selection.id || result.item.dictionaryTypeId !== result.dictionaryType.id) throw new Error('RESOURCE_MISMATCH');
  return { type: result.dictionaryType, item: result.item };
}

export default function DictionaryPage() {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [typeForm] = Form.useForm<DictionaryTypePayload>();
  const [itemForm] = Form.useForm<DictionaryItemPayload>();
  const { user } = useAuth();
  const { hasPermission, permissionVersion } = usePermission('route:dictionary');
  const canRead = hasPermission('route:dictionary');
  const liveManage = hasPermission('dictionary:manage');
  const scope = `${user?.id}:${user?.tenantId}:${permissionVersion}:${canRead}:${liveManage}`;
  const activeScope = useRef<string | undefined>(scope); activeScope.current = scope;
  const [dataScope, setDataScope] = useState(scope);
  const [storedTypes, setTypes] = useState<DictionaryType[]>([]);
  const [storedItems, setItems] = useState<DictionaryItem[]>([]);
  const [parent, setParent] = useState<DictionaryType>();
  const [itemsParent, setItemsParent] = useState<number>();
  const [serverCanManage, setCanManage] = useState(false);
  const [serverItemsCanManage, setItemsCanManage] = useState(false);
  const types = useMemo(() => dataScope === scope ? storedTypes : [], [dataScope, scope, storedTypes]);
  const selected = dataScope === scope ? parent : undefined;
  const selectedId = selected?.id;
  const parentId = useRef(selectedId); parentId.current = selectedId;
  const items = dataScope === scope && itemsParent === selectedId ? storedItems : [];
  const canManage = canRead && liveManage && serverCanManage && dataScope === scope;
  const canManageItems = canManage && serverItemsCanManage && itemsParent === selectedId;
  const [typeKeyword, setTypeKeyword] = useState('');
  const [itemSearch, setItemSearch] = useState('');
  const [itemKeyword, setItemKeyword] = useState('');
  const [itemStatus, setItemStatus] = useState<string>('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [loadingTypes, setLoadingTypes] = useState(false);
  const [loadingItems, setLoadingItems] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editor, setEditor] = useState<Editor>();
  const [detail, setDetail] = useState<Selection>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [editorLoading, setEditorLoading] = useState(false);
  const typeRequest = useRef(0); const itemRequest = useRef(0);
  const selectionRequest = useRef(0); const editorRequest = useRef(0);
  const target = useRef<Target | undefined>(undefined);
  const confirmation = useRef<{ destroy: () => void } | undefined>(undefined);
  const confirmationEpoch = useRef(0);
  const dismissConfirmation = useCallback(() => { confirmationEpoch.current += 1; confirmation.current?.destroy(); confirmation.current = undefined; }, []);
  const router = useRouter(); const params = useSearchParams();
  const requestedType = params.get('typeId'); const requestedItem = params.get('itemId');
  const activeSelection = dataScope !== scope ? undefined : editor
    ? editor.kind === 'item' ? { type: editor.type, item: editor.record } : editor.record ? { type: editor.record } : undefined
    : detailOpen ? detail : undefined;
  usePublishPageAgentContext({ dictionaryTypeId: activeSelection?.type.id, typeCode: activeSelection?.type.code,
    typeVersion: activeSelection?.type.version, dictionaryItemId: activeSelection?.item?.id, itemValue: activeSelection?.item?.value,
    version: activeSelection?.item?.version ?? activeSelection?.type.version,
    typeKeyword: typeKeyword || undefined, keyword: itemKeyword || undefined, status: itemStatus || undefined, page, size });

  useEffect(() => {
    activeScope.current = scope;
    dismissConfirmation(); typeRequest.current += 1; itemRequest.current += 1; selectionRequest.current += 1; editorRequest.current += 1;
    target.current = undefined; setTypes([]); setItems([]); setParent(undefined); setTotal(0); setCanManage(false); setItemsCanManage(false);
    setDetail(undefined); setDetailOpen(false); setDetailLoading(false); setEditor(undefined); setEditorLoading(false); setSaving(false);
    setDataScope(scope);
    return () => { dismissConfirmation(); activeScope.current = undefined; typeRequest.current += 1; itemRequest.current += 1;
      selectionRequest.current += 1; editorRequest.current += 1; target.current = undefined; };
  }, [scope, dismissConfirmation]);

  const loadTypes = useCallback(async () => {
    const request = ++typeRequest.current;
    if (!canRead) { setLoadingTypes(false); return; }
    setLoadingTypes(true);
    try {
      const result = await dictionaryApi.listTypes({ keyword: typeKeyword });
      if (request === typeRequest.current && activeScope.current === scope) { setTypes(result.records); setCanManage(result.canManage); }
    } catch (error) { if (request === typeRequest.current && activeScope.current === scope) {
      setTypes([]); setCanManage(false); message.error(formatOaApiError(error));
    } } finally { if (request === typeRequest.current && activeScope.current === scope) setLoadingTypes(false); }
  }, [typeKeyword, scope, canRead]);

  const loadItems = useCallback(async () => {
    const request = ++itemRequest.current;
    if (!canRead || !selectedId || selectedId !== parentId.current) { setItems([]); setTotal(0); setItemsCanManage(false); setLoadingItems(false); return; }
    setLoadingItems(true);
    try {
      const result = await dictionaryApi.listItems(selectedId, {
        keyword: itemKeyword, status: itemStatus, page, size,
      });
      if (request !== itemRequest.current || activeScope.current !== scope || parentId.current !== selectedId) return;
      if (result.records.some((record) => record.dictionaryTypeId !== selectedId)) throw new Error('RESOURCE_MISMATCH');
      setItems(result.records); setItemsParent(selectedId); setTotal(result.total); setItemsCanManage(result.canManage);
    } catch (error) { if (request === itemRequest.current && activeScope.current === scope) {
      setItems([]); setTotal(0); setItemsCanManage(false); message.error(formatOaApiError(error));
    } } finally { if (request === itemRequest.current && activeScope.current === scope) setLoadingItems(false); }
  }, [itemKeyword, itemStatus, page, selectedId, size, canRead, scope]);
  const latestTypes = useRef(loadTypes); latestTypes.current = loadTypes;
  const latestItems = useRef(loadItems); latestItems.current = loadItems;

  useEffect(() => { void loadTypes(); }, [loadTypes]);
  useEffect(() => { void loadItems(); }, [loadItems]);
  useEffect(() => {
    if (!editor || editorLoading) return;
    if (editor.kind === 'type') { typeForm.resetFields(); typeForm.setFieldsValue(editor.record ? { ...editor.record } : { sortOrder: 0 }); }
    else { itemForm.resetFields(); itemForm.setFieldsValue(editor.record ? { ...editor.record } : { sortOrder: 0 }); }
  }, [editor, editorLoading, typeForm, itemForm]);

  const loadSelection = useCallback(async (selection: Target) => {
    if (!canRead) return;
    dismissConfirmation(); const request = ++selectionRequest.current; target.current = selection;
    setDetail(undefined); setDetailOpen(true); setDetailLoading(true);
    try {
      const result = await readSelection(selection);
      if (request !== selectionRequest.current || activeScope.current !== scope) return;
      setDetail(result); setParent(result.type);
    } catch (error) { if (request === selectionRequest.current && activeScope.current === scope) {
      target.current = undefined; setDetailOpen(false); setDetail(undefined); message.error(formatOaApiError(error));
    } } finally { if (request === selectionRequest.current && activeScope.current === scope) setDetailLoading(false); }
  }, [canRead, scope, dismissConfirmation]);
  const closeWindows = () => {
    dismissConfirmation(); selectionRequest.current += 1; editorRequest.current += 1; target.current = undefined;
    setDetail(undefined); setDetailOpen(false); setDetailLoading(false); setEditor(undefined); setEditorLoading(false); setSaving(false);
  };
  useEffect(() => {
    dismissConfirmation(); selectionRequest.current += 1; editorRequest.current += 1; target.current = undefined;
    setDetail(undefined); setDetailOpen(false); setEditor(undefined); setEditorLoading(false); setSaving(false); setDetailLoading(false);
    const typeId = parseRecordId(requestedType); const itemId = parseRecordId(requestedItem);
    if (canRead && !(requestedType !== null && requestedItem !== null)) {
      if (typeId) void loadSelection({ kind: 'type', id: typeId });
      else if (itemId) void loadSelection({ kind: 'item', id: itemId });
    }
    return () => { dismissConfirmation(); selectionRequest.current += 1; editorRequest.current += 1; target.current = undefined; };
  }, [requestedType, requestedItem, canRead, scope, loadSelection, dismissConfirmation]);
  const close = () => { closeWindows(); if (requestedType !== null || requestedItem !== null) router.replace('/oa/dictionary'); };
  const showSelection = (selection: Target, replace = false) => {
    if (!canRead) return;
    closeWindows(); setPage(1);
    const path = `/oa/dictionary?${selection.kind === 'type' ? 'typeId' : 'itemId'}=${selection.id}`;
    if (params.toString() === path.split('?')[1]) void loadSelection(selection);
    else if (replace) router.replace(path); else router.push(path);
  };
  const openEditor = async (kind: 'type' | 'item', record?: DictionaryType | DictionaryItem) => {
    if (!canManage || (record && !record.canManage) || (kind === 'item' && (!canManageItems || !selected?.canManage))) return;
    closeWindows(); const request = ++editorRequest.current;
    setEditor(kind === 'type' ? { kind } : { kind, type: selected! }); setEditorLoading(Boolean(record));
    try {
      const result = record ? await readSelection({ kind, id: record.id }) : undefined;
      if (request !== editorRequest.current || activeScope.current !== scope) return;
      if (result && (!result.type.canManage || (result.item && !result.item.canManage))) throw new Error('RESOURCE_MISMATCH');
      if (kind === 'type') {
        setEditor({ kind, record: result?.type });
      } else {
        setEditor({ kind, type: result?.type ?? selected!, record: result?.item });
      }
    } catch (error) { if (request === editorRequest.current && activeScope.current === scope) { closeWindows(); message.error(formatOaApiError(error)); } }
    finally { if (request === editorRequest.current && activeScope.current === scope) setEditorLoading(false); }
  };
  const openType = (record?: DictionaryType) => void openEditor('type', record);
  const openItem = (record?: DictionaryItem) => void openEditor('item', record);
  const refresh = async () => {
    const identity = scope; const current = target.current; const request = selectionRequest.current;
    await Promise.all([latestTypes.current(), latestItems.current()]);
    if (activeScope.current === identity && request === selectionRequest.current && current) await loadSelection(current);
  };

  const save = async () => {
    if (!editor || !canManage || (editor.kind === 'item' && !canManageItems) || editorLoading || saving) return;
    const request = editorRequest.current; const identity = scope; const current = editor;
    try {
      let result: DictionaryType | DictionaryItem;
      if (editor.kind === 'type') {
        const values = await typeForm.validateFields();
        if (request !== editorRequest.current || activeScope.current !== identity) return;
        setSaving(true); result = editor.record
          ? await dictionaryApi.updateType(editor.record.id, { ...values, version: editor.record.version })
          : await dictionaryApi.createType(values);
      } else {
        const values = await itemForm.validateFields();
        if (request !== editorRequest.current || activeScope.current !== identity) return;
        setSaving(true); result = editor.record
          ? await dictionaryApi.updateItem(editor.type.id, editor.record.id, { ...values, version: editor.record.version })
          : await dictionaryApi.createItem(editor.type.id, values);
      }
      if (activeScope.current !== identity) return;
      if (request === editorRequest.current) { message.success(t('dictionary.saveSuccess')); showSelection({ kind: current.kind, id: result.id }, true); }
      await Promise.all([latestTypes.current(), latestItems.current()]);
    } catch (error) {
      if (request === editorRequest.current && activeScope.current === identity && !(error && typeof error === 'object' && 'errorFields' in error)) message.error(formatOaApiError(error));
    } finally {
      if (request === editorRequest.current && activeScope.current === identity) setSaving(false);
    }
  };

  const confirm = (title: string, content: string, operation: () => Promise<unknown>, success: string, immediate = false, afterSuccess?: () => void) => {
    if (!canManage) return;
    dismissConfirmation(); const epoch = confirmationEpoch.current; const identity = scope; const request = selectionRequest.current; const edit = editorRequest.current;
    const isCurrent = () => epoch === confirmationEpoch.current && activeScope.current === identity && request === selectionRequest.current && edit === editorRequest.current;
    const execute = async () => {
      if (!isCurrent()) return;
      try { await operation(); if (!isCurrent()) return; message.success(t(success)); afterSuccess?.(); await refresh(); }
      catch (error) { if (isCurrent()) { message.error(formatOaApiError(error)); throw error; } }
    };
    if (immediate) { void execute().catch(() => undefined); return; }
    confirmation.current = modal.confirm({ title, content, okText: t('common.confirm'), cancelText: t('common.cancel'),
      onCancel: () => { if (epoch === confirmationEpoch.current) dismissConfirmation(); }, onOk: execute });
  };

  const confirmStatus = (kind: 'type' | 'item', record: DictionaryType | DictionaryItem, checked: boolean) => {
    const status: DictionaryStatus = checked ? 'ACTIVE' : 'DISABLED';
    if (!record.canManage || (kind === 'item' && !canManageItems)) return;
    confirm(t('dictionary.disableTitle'), t(kind === 'type' ? 'dictionary.disableTypeContent' : 'dictionary.disableItemContent'),
      () => kind === 'type' ? dictionaryApi.setTypeStatus(record.id, status, record.version)
        : dictionaryApi.setItemStatus((record as DictionaryItem).dictionaryTypeId, record.id, status, record.version), 'dictionary.statusSuccess', checked);
  };

  const removeType = (record: DictionaryType) => { if (record.canManage && record.itemCount === 0) confirm(t('dictionary.deleteTypeTitle'),
    t('dictionary.deleteTypeContent'), () => dictionaryApi.deleteType(record.id, record.version), 'dictionary.deleteSuccess', false, () => {
      if (parentId.current === record.id) { itemRequest.current += 1; parentId.current = undefined; setParent(undefined); setItems([]); setTotal(0); setItemsCanManage(false); close(); }
    }); };
  const removeItem = (record: DictionaryItem) => { if (canManageItems && record.canManage && record.canDelete) confirm(t('dictionary.deleteItemTitle'),
    t('dictionary.deleteItemContent'), () => dictionaryApi.deleteItem(record.dictionaryTypeId, record.id, record.version), 'dictionary.deleteSuccess', false,
    () => { if (target.current?.kind === 'item' && target.current.id === record.id) close(); }); };

  const columns: ColumnsType<DictionaryItem> = [
    { title: t('dictionary.value'), dataIndex: 'value', width: 180, render: (value: string, record) => <Button type="link" onClick={() => showSelection({ kind: 'item', id: record.id })}>{value}</Button> },
    { title: t('dictionary.label'), dataIndex: 'label', minWidth: 180 },
    { title: t('dictionary.descriptionField'), dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '-' },
    { title: t('dictionary.sortOrder'), dataIndex: 'sortOrder', width: 90, align: 'center' },
    { title: t('dictionary.usageCount'), dataIndex: 'usageCount', width: 105, align: 'center', render: (value: number) => value ? <Tag>{value}</Tag> : '0' },
    { title: t('dictionary.status'), dataIndex: 'status', width: 105, render: (value: DictionaryStatus, record) => canManageItems && record.canManage
      ? <Switch size="small" checked={value === 'ACTIVE'} checkedChildren={t('dictionary.active')} unCheckedChildren={t('dictionary.disabled')} onChange={(checked) => confirmStatus('item', record, checked)} />
      : <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{t(value === 'ACTIVE' ? 'dictionary.active' : 'dictionary.disabled')}</Tag> },
    { title: t('dictionary.updatedAt'), dataIndex: 'updatedAt', width: 180, render: (value: string) => new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) },
    ...(canManageItems ? [{ title: t('dictionary.actions'), key: 'actions', width: 130, fixed: 'right' as const, render: (_: unknown, record: DictionaryItem) => <Space size={2}>
      <Button type="link" size="small" disabled={!record.canManage} onClick={() => openItem(record)}>{t('common.edit')}</Button>
      <Tooltip title={!record.canDelete ? t('dictionary.referenced') : undefined}><span><Button type="link" size="small" danger disabled={!record.canManage || !record.canDelete} onClick={() => removeItem(record)}>{t('common.delete')}</Button></span></Tooltip>
    </Space> }] : []),
  ];

  const stats = useMemo(() => ({
    activeTypes: types.filter((entry) => entry.status === 'ACTIVE').length,
    totalItems: types.reduce((sum, entry) => sum + entry.itemCount, 0),
    activeItems: types.reduce((sum, entry) => sum + entry.activeItemCount, 0),
  }), [types]);

  return <section className="oa-dictionary-page">
    <header className="oa-dictionary-hero">
      <div className="oa-dictionary-heading"><span className="oa-dictionary-icon"><OaIcon name="dictionary" size={23} /></span><div>
        <Typography.Text className="oa-dictionary-eyebrow">{t('dictionary.eyebrow')}</Typography.Text>
        <Typography.Title level={3}>{t('dictionary.title')}</Typography.Title>
        <Typography.Paragraph>{t('dictionary.description')}</Typography.Paragraph>
      </div></div>
      <Space><Button icon={<OaIcon name="reload" />} onClick={() => void refresh()}>{t('workbench.refresh')}</Button>
        {canManage && <Button type="primary" icon={<OaIcon name="add" />} onClick={() => openType()}>{t('dictionary.addType')}</Button>}</Space>
    </header>

    <Row gutter={[12, 12]} className="oa-dictionary-stats">
      <Col xs={12} lg={6}><Card><Statistic title={t('dictionary.totalTypes')} value={types.length} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title={t('dictionary.activeTypes')} value={stats.activeTypes} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title={t('dictionary.totalItems')} value={stats.totalItems} /></Card></Col>
      <Col xs={12} lg={6}><Card><Statistic title={t('dictionary.activeItems')} value={stats.activeItems} /></Card></Col>
    </Row>

    <div className="oa-dictionary-workspace">
      <Card className="oa-dictionary-types" title={t('dictionary.typeList')} loading={loadingTypes}>
        <Input.Search allowClear placeholder={t('dictionary.searchType')} onSearch={(value) => setTypeKeyword(value.trim())} />
        <div className="oa-dictionary-type-list">
          {types.length === 0 ? <Empty description={t('dictionary.emptyTypes')} /> : types.map((entry) => <div
            key={entry.id} role="button" tabIndex={0} className={`oa-dictionary-type${entry.id === selectedId ? ' is-selected' : ''}`}
            onClick={() => showSelection({ kind: 'type', id: entry.id })}
            onKeyDown={(event) => { if (event.target === event.currentTarget && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); showSelection({ kind: 'type', id: entry.id }); } }}>
            <span className="oa-dictionary-type__top"><strong>{entry.name}</strong><Tag color={entry.status === 'ACTIVE' ? 'green' : 'default'}>{t(entry.status === 'ACTIVE' ? 'dictionary.active' : 'dictionary.disabled')}</Tag></span>
            <Typography.Text code>{entry.code}</Typography.Text>
            <span className="oa-dictionary-type__bottom"><small>{t('dictionary.itemsSummary', { active: entry.activeItemCount, total: entry.itemCount })}</small>
              {canManage && entry.canManage && <Space size={0}><Button type="text" size="small" aria-label={`${t('common.edit')} ${entry.name}`} icon={<OaIcon name="edit" />} onClick={(event) => { event.stopPropagation(); openType(entry); }} />
                <Switch size="small" checked={entry.status === 'ACTIVE'} onClick={(_, event) => event.stopPropagation()} onChange={(checked) => confirmStatus('type', entry, checked)} />
                <Button type="text" danger size="small" aria-label={`${t('common.delete')} ${entry.name}`} disabled={entry.itemCount > 0} icon={<OaIcon name="delete" />} onClick={(event) => { event.stopPropagation(); removeType(entry); }} /></Space>}</span>
          </div>)}
        </div>
      </Card>

      <Card className="oa-dictionary-items" title={<span>{t('dictionary.itemList')}{selected && <Typography.Text type="secondary"> · {selected.name}</Typography.Text>}</span>}
        extra={canManageItems && selected?.canManage && <Button type="primary" icon={<OaIcon name="add" />} onClick={() => openItem()}>{t('dictionary.addItem')}</Button>}>
        {!canManage && <Typography.Text type="secondary" className="oa-dictionary-readonly">{t('dictionary.readOnlyTip')}</Typography.Text>}
        <div className="oa-dictionary-toolbar"><Input.Search allowClear value={itemSearch} placeholder={t('dictionary.searchItem')}
          onChange={(event) => { setItemSearch(event.target.value); if (!event.target.value) { setItemKeyword(''); setPage(1); } }}
          onSearch={(value) => { setItemKeyword(value.trim()); setPage(1); }} />
          <Segmented value={itemStatus} options={[{ value: '', label: t('dictionary.allStatuses') }, { value: 'ACTIVE', label: t('dictionary.active') }, { value: 'DISABLED', label: t('dictionary.disabled') }]}
            onChange={(value) => { setItemStatus(String(value)); setPage(1); }} /></div>
        <ResponsiveTable rowKey="id" loading={loadingItems} columns={columns} dataSource={items} scroll={{ x: 1050 }}
          locale={{ emptyText: <Empty description={t('dictionary.emptyItems')} /> }}
          pagination={{ current: page, pageSize: size, total, showSizeChanger: true, onChange: (nextPage, nextSize) => { setPage(nextPage); setSize(nextSize); } }} />
      </Card>
    </div>

    <Drawer width={520} open={Boolean(editor) && canRead && dataScope === scope} loading={editorLoading} title={editor?.kind === 'type' ? t(editor.record ? 'dictionary.editType' : 'dictionary.addType') : t(editor?.record ? 'dictionary.editItem' : 'dictionary.addItem')}
      onClose={close} extra={<Button type="primary" loading={saving} disabled={!canManage || editorLoading} onClick={() => void save()}>{t('common.save')}</Button>}>
      {editor?.kind === 'type' ? <Form form={typeForm} layout="vertical">
        <Form.Item name="code" label={t('dictionary.code')} extra={t('dictionary.codeHint')} rules={[{ required: true }, { pattern: /^[A-Z][A-Z0-9_]{1,63}$/ }]}><Input disabled={Boolean(editor.record)} maxLength={64} /></Form.Item>
        <Form.Item name="name" label={t('dictionary.name')} rules={[{ required: true }]}><Input maxLength={120} placeholder={t('dictionary.nameHint')} /></Form.Item>
        <Form.Item name="sortOrder" label={t('dictionary.sortOrder')}><InputNumber min={0} max={9999} className="oa-dictionary-number" /></Form.Item>
        <Form.Item name="description" label={t('dictionary.descriptionField')}><Input.TextArea rows={5} maxLength={500} showCount placeholder={t('dictionary.descriptionHint')} /></Form.Item>
      </Form> : <Form form={itemForm} layout="vertical">
        <Form.Item name="value" label={t('dictionary.value')} extra={t('dictionary.valueHint')} rules={[{ required: true }, { pattern: /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/ }]}><Input disabled={Boolean(editor?.record)} maxLength={128} /></Form.Item>
        <Form.Item name="label" label={t('dictionary.label')} rules={[{ required: true }]}><Input maxLength={160} placeholder={t('dictionary.labelHint')} /></Form.Item>
        <Form.Item name="sortOrder" label={t('dictionary.sortOrder')}><InputNumber min={0} max={9999} className="oa-dictionary-number" /></Form.Item>
        <Form.Item name="description" label={t('dictionary.descriptionField')}><Input.TextArea rows={5} maxLength={500} showCount placeholder={t('dictionary.descriptionHint')} /></Form.Item>
      </Form>}
    </Drawer>
    <Drawer width={520} open={detailOpen && canRead && dataScope === scope} loading={detailLoading} onClose={close}
      title={t('dictionary.detailTitle')} extra={detail && canManage && detail.type.canManage && (!detail.item || (canManageItems && detail.item.canManage))
        ? <Button onClick={() => detail.item ? openItem(detail.item) : openType(detail.type)}>{t('common.edit')}</Button> : undefined}>
      {detail && <Descriptions column={1} items={[
        { key: 'type', label: t('dictionary.code'), children: detail.type.code },
        { key: 'name', label: t('dictionary.name'), children: detail.type.name },
        ...(detail.item ? [
          { key: 'value', label: t('dictionary.value'), children: detail.item.value },
          { key: 'label', label: t('dictionary.label'), children: detail.item.label },
          { key: 'usage', label: t('dictionary.usageCount'), children: detail.item.usageCount },
        ] : []),
        { key: 'status', label: t('dictionary.status'), children: t((detail.item ?? detail.type).status === 'ACTIVE' ? 'dictionary.active' : 'dictionary.disabled') },
        { key: 'description', label: t('dictionary.descriptionField'), children: (detail.item ?? detail.type).description || '-' },
      ]} />}
    </Drawer>
  </section>;
}
