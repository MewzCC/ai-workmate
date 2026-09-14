'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  App, Button, Card, Col, Drawer, Empty, Form, Input, InputNumber, Row, Segmented,
  Space, Statistic, Switch, Tag, Tooltip, Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from '@/components/oa/ResponsiveTable';
import { dictionaryApi, type DictionaryItem, type DictionaryItemPayload, type DictionaryStatus,
  type DictionaryType, type DictionaryTypePayload } from '@/lib/dictionaryApi';
import { formatOaApiError } from '@/lib/oaApi';
import { message } from '@/lib/antdMessage';

type Editor = { kind: 'type'; record?: DictionaryType } | { kind: 'item'; record?: DictionaryItem };

export default function DictionaryPage() {
  const { t, i18n } = useTranslation();
  const { modal } = App.useApp();
  const [typeForm] = Form.useForm<DictionaryTypePayload>();
  const [itemForm] = Form.useForm<DictionaryItemPayload>();
  const [types, setTypes] = useState<DictionaryType[]>([]);
  const [items, setItems] = useState<DictionaryItem[]>([]);
  const [selectedId, setSelectedId] = useState<number>();
  const [canManage, setCanManage] = useState(false);
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

  const selected = types.find((entry) => entry.id === selectedId);

  const loadTypes = useCallback(async (preferredId?: number) => {
    setLoadingTypes(true);
    try {
      const result = await dictionaryApi.listTypes({ keyword: typeKeyword });
      setTypes(result.records);
      setCanManage(result.canManage);
      setSelectedId((current) => {
        const candidate = preferredId ?? current;
        return result.records.some((entry) => entry.id === candidate) ? candidate : result.records[0]?.id;
      });
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoadingTypes(false);
    }
  }, [typeKeyword]);

  const loadItems = useCallback(async () => {
    if (!selectedId) { setItems([]); setTotal(0); return; }
    setLoadingItems(true);
    try {
      const result = await dictionaryApi.listItems(selectedId, {
        keyword: itemKeyword, status: itemStatus, page, size,
      });
      setItems(result.records);
      setTotal(result.total);
      setCanManage(result.canManage);
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setLoadingItems(false);
    }
  }, [itemKeyword, itemStatus, page, selectedId, size]);

  useEffect(() => { void loadTypes(); }, [loadTypes]);
  useEffect(() => { void loadItems(); }, [loadItems]);

  const openType = (record?: DictionaryType) => {
    typeForm.resetFields();
    typeForm.setFieldsValue(record ? { ...record } : { sortOrder: 0 });
    setEditor({ kind: 'type', record });
  };
  const openItem = (record?: DictionaryItem) => {
    itemForm.resetFields();
    itemForm.setFieldsValue(record ? { ...record } : { sortOrder: 0 });
    setEditor({ kind: 'item', record });
  };

  const save = async () => {
    if (!editor) return;
    setSaving(true);
    try {
      if (editor.kind === 'type') {
        const values = await typeForm.validateFields();
        const result = editor.record
          ? await dictionaryApi.updateType(editor.record.id, { ...values, version: editor.record.version })
          : await dictionaryApi.createType(values);
        await loadTypes(result.id);
      } else if (selectedId) {
        const values = await itemForm.validateFields();
        if (editor.record) await dictionaryApi.updateItem(selectedId, editor.record.id, { ...values, version: editor.record.version });
        else await dictionaryApi.createItem(selectedId, values);
        await Promise.all([loadItems(), loadTypes(selectedId)]);
      }
      setEditor(undefined);
      message.success(t('dictionary.saveSuccess'));
    } catch (error) {
      message.error(formatOaApiError(error));
    } finally {
      setSaving(false);
    }
  };

  const confirmStatus = (kind: 'type' | 'item', record: DictionaryType | DictionaryItem, checked: boolean) => {
    const status: DictionaryStatus = checked ? 'ACTIVE' : 'DISABLED';
    const execute = async () => {
      try {
        if (kind === 'type') {
          await dictionaryApi.setTypeStatus(record.id, status, record.version);
          await loadTypes(record.id);
        } else if (selectedId) {
          await dictionaryApi.setItemStatus(selectedId, record.id, status, record.version);
          await Promise.all([loadItems(), loadTypes(selectedId)]);
        }
        message.success(t('dictionary.statusSuccess'));
      } catch (error) { message.error(formatOaApiError(error)); }
    };
    if (checked) { void execute(); return; }
    modal.confirm({
      title: t('dictionary.disableTitle'),
      content: t(kind === 'type' ? 'dictionary.disableTypeContent' : 'dictionary.disableItemContent'),
      okText: t('common.confirm'), cancelText: t('common.cancel'), onOk: execute,
    });
  };

  const removeType = (record: DictionaryType) => modal.confirm({
    title: t('dictionary.deleteTypeTitle'), content: t('dictionary.deleteTypeContent'),
    okText: t('common.delete'), cancelText: t('common.cancel'), okButtonProps: { danger: true },
    onOk: async () => {
      try { await dictionaryApi.deleteType(record.id, record.version); message.success(t('dictionary.deleteSuccess')); await loadTypes(); }
      catch (error) { message.error(formatOaApiError(error)); }
    },
  });
  const removeItem = (record: DictionaryItem) => modal.confirm({
    title: t('dictionary.deleteItemTitle'), content: t('dictionary.deleteItemContent'),
    okText: t('common.delete'), cancelText: t('common.cancel'), okButtonProps: { danger: true },
    onOk: async () => {
      if (!selectedId) return;
      try { await dictionaryApi.deleteItem(selectedId, record.id, record.version); message.success(t('dictionary.deleteSuccess')); await Promise.all([loadItems(), loadTypes(selectedId)]); }
      catch (error) { message.error(formatOaApiError(error)); }
    },
  });

  const columns: ColumnsType<DictionaryItem> = [
    { title: t('dictionary.value'), dataIndex: 'value', width: 180, render: (value: string) => <Typography.Text code>{value}</Typography.Text> },
    { title: t('dictionary.label'), dataIndex: 'label', minWidth: 180 },
    { title: t('dictionary.descriptionField'), dataIndex: 'description', ellipsis: true, render: (value?: string) => value || '-' },
    { title: t('dictionary.sortOrder'), dataIndex: 'sortOrder', width: 90, align: 'center' },
    { title: t('dictionary.usageCount'), dataIndex: 'usageCount', width: 105, align: 'center', render: (value: number) => value ? <Tag>{value}</Tag> : '0' },
    { title: t('dictionary.status'), dataIndex: 'status', width: 105, render: (value: DictionaryStatus, record) => canManage
      ? <Switch size="small" checked={value === 'ACTIVE'} checkedChildren={t('dictionary.active')} unCheckedChildren={t('dictionary.disabled')} onChange={(checked) => confirmStatus('item', record, checked)} />
      : <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{t(value === 'ACTIVE' ? 'dictionary.active' : 'dictionary.disabled')}</Tag> },
    { title: t('dictionary.updatedAt'), dataIndex: 'updatedAt', width: 180, render: (value: string) => new Intl.DateTimeFormat(i18n.language, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) },
    ...(canManage ? [{ title: t('dictionary.actions'), key: 'actions', width: 130, fixed: 'right' as const, render: (_: unknown, record: DictionaryItem) => <Space size={2}>
      <Button type="link" size="small" onClick={() => openItem(record)}>{t('common.edit')}</Button>
      <Tooltip title={!record.canDelete ? t('dictionary.referenced') : undefined}><span><Button type="link" size="small" danger disabled={!record.canDelete} onClick={() => removeItem(record)}>{t('common.delete')}</Button></span></Tooltip>
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
      <Space><Button icon={<OaIcon name="reload" />} onClick={() => void Promise.all([loadTypes(selectedId), loadItems()])}>{t('workbench.refresh')}</Button>
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
            onClick={() => { setSelectedId(entry.id); setPage(1); }}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { setSelectedId(entry.id); setPage(1); } }}>
            <span className="oa-dictionary-type__top"><strong>{entry.name}</strong><Tag color={entry.status === 'ACTIVE' ? 'green' : 'default'}>{t(entry.status === 'ACTIVE' ? 'dictionary.active' : 'dictionary.disabled')}</Tag></span>
            <Typography.Text code>{entry.code}</Typography.Text>
            <span className="oa-dictionary-type__bottom"><small>{t('dictionary.itemsSummary', { active: entry.activeItemCount, total: entry.itemCount })}</small>
              {canManage && <Space size={0}><Button type="text" size="small" icon={<OaIcon name="edit" />} onClick={(event) => { event.stopPropagation(); openType(entry); }} />
                <Switch size="small" checked={entry.status === 'ACTIVE'} onClick={(_, event) => event.stopPropagation()} onChange={(checked) => confirmStatus('type', entry, checked)} />
                <Button type="text" danger size="small" disabled={entry.itemCount > 0} icon={<OaIcon name="delete" />} onClick={(event) => { event.stopPropagation(); removeType(entry); }} /></Space>}</span>
          </div>)}
        </div>
      </Card>

      <Card className="oa-dictionary-items" title={<span>{t('dictionary.itemList')}{selected && <Typography.Text type="secondary"> · {selected.name}</Typography.Text>}</span>}
        extra={canManage && selected && <Button type="primary" icon={<OaIcon name="add" />} onClick={() => openItem()}>{t('dictionary.addItem')}</Button>}>
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

    <Drawer width={520} open={Boolean(editor)} title={editor?.kind === 'type' ? t(editor.record ? 'dictionary.editType' : 'dictionary.addType') : t(editor?.record ? 'dictionary.editItem' : 'dictionary.addItem')}
      onClose={() => setEditor(undefined)} extra={<Button type="primary" loading={saving} onClick={() => void save()}>{t('common.save')}</Button>}>
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
  </section>;
}
