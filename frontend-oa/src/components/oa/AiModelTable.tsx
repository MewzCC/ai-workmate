import { Checkbox, Button, Input, InputNumber, Space, Switch, Typography } from 'antd';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import type { AiConfiguredModel } from '@/lib/aiConfigurationApi';
import ResponsiveTable from './ResponsiveTable';

export default function AiModelTable({ rows, selected, disabled, busy, onSelection, onChange, onTest }: {
 rows: AiConfiguredModel[]; selected: string[]; disabled: boolean; busy: string;
 onSelection: (ids: string[]) => void; onChange: (row: AiConfiguredModel) => void; onTest: (id: string) => void;
}) {
 const { t } = useTranslation();
 const [search, setSearch] = useState('');
 const filtered = rows.filter(row => row.id.toLowerCase().includes(search.toLowerCase()));
 const columns: ColumnsType<AiConfiguredModel> = [
  { title: t('chat.model'), dataIndex: 'id', width: 330, render: (_, row) => <Checkbox checked={selected.includes(row.id)} disabled={disabled}
    onChange={event => onSelection(event.target.checked ? [...selected, row.id] : selected.filter(id => id !== row.id))}><Typography.Text className="oa-model-id">{row.id}</Typography.Text></Checkbox> },
  { title: t('aiConfiguration.contextWindow'), dataIndex: 'contextWindow', width: 160, render: (_, row) => <InputNumber aria-label={t('aiConfiguration.contextFor', { model: row.id })} min={1024} max={2097152} step={1024} precision={0} disabled={disabled} value={row.contextWindow} onChange={value => { if (value) onChange({ ...row, contextWindow: value }); }} /> },
  { title: t('aiConfiguration.maxOutput'), dataIndex: 'maxOutputTokens', width: 160, render: (_, row) => <InputNumber aria-label={t('aiConfiguration.outputFor', { model: row.id })} min={16} max={65536} step={256} precision={0} disabled={disabled} value={row.maxOutputTokens} onChange={value => { if (value) onChange({ ...row, maxOutputTokens: value }); }} /> },
  { title: t('aiConfiguration.images'), dataIndex: 'supportsImages', width: 110, render: (_, row) => <Switch aria-label={t('aiConfiguration.imagesFor', { model: row.id })} disabled={disabled} checked={row.supportsImages} onChange={value => onChange({ ...row, supportsImages: value })} /> },
  { title: t('common.actions'), key: 'actions', width: 120, fixed: 'right', render: (_, row) => <Button size="small" disabled={disabled} loading={busy === `test:${row.id}`} onClick={() => onTest(row.id)}>{t('aiConfiguration.testModel')}</Button> },
 ];
 return <div className="oa-model-directory">
  <div className="oa-model-toolbar">
   <Input.Search allowClear value={search} onChange={event => setSearch(event.target.value)} placeholder={t('aiConfiguration.searchModels')} aria-label={t('aiConfiguration.searchModels')} />
   <Space wrap>
    <Button disabled={disabled || !filtered.length} onClick={() => onSelection(Array.from(new Set([...selected, ...filtered.map(row => row.id)])))}>{t('aiConfiguration.selectVisible')}</Button>
    <Button disabled={disabled || !selected.length} onClick={() => onSelection([])}>{t('aiConfiguration.clearSelection')}</Button>
    <Typography.Text type="secondary">{t('aiConfiguration.selectedCount', { count: selected.length })}</Typography.Text>
   </Space>
  </div>
  <ResponsiveTable rowKey="id" size="small" columns={columns} dataSource={filtered} pagination={{ pageSize: 8, showSizeChanger: false }} scroll={{ x: 880 }} locale={{ emptyText: t('aiConfiguration.noModels') }} />
 </div>;
}
