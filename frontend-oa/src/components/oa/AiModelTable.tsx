import { Checkbox, Button, Input, InputNumber, Select, Space, Switch, Typography } from 'antd';
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
 const contextPresets = [32768, 65536, 131072, 262144, 524288, 1048576, 2097152];
 const contextOptions = contextPresets.map(value => ({ value, label: t('aiConfiguration.contextPresetSize', { size: value >= 1048576 ? `${value / 1048576}M` : `${value / 1024}K` }) }));
 const outputPresets = [4096, 8192, 16384, 32768, 65536, 131072, 262144, 393216];
 const outputOptions = outputPresets.map(value => ({ value, label: t('aiConfiguration.contextPresetSize', { size: `${value / 1024}K` }) }));
 const columns: ColumnsType<AiConfiguredModel> = [
  { title: t('chat.model'), dataIndex: 'id', width: 330, render: (_, row) => <Checkbox checked={selected.includes(row.id)} disabled={disabled}
    onChange={event => onSelection(event.target.checked ? [...selected, row.id] : selected.filter(id => id !== row.id))}><Typography.Text className="oa-model-id">{row.id}</Typography.Text></Checkbox> },
  { title: t('aiConfiguration.contextWindow'), dataIndex: 'contextWindow', width: 260, render: (_, row) => <Space size={8} wrap>
   <InputNumber style={{ width: 116 }} aria-label={t('aiConfiguration.contextFor', { model: row.id })} min={1024} max={2097152} step={1024} precision={0} disabled={disabled} value={row.contextWindow} onChange={value => { if (value) onChange({ ...row, contextWindow: value }); }} />
   <Select style={{ width: 104 }} aria-label={t('aiConfiguration.contextPresetFor', { model: row.id })} placeholder={t('aiConfiguration.contextPreset')} options={contextOptions} value={contextPresets.includes(row.contextWindow) ? row.contextWindow : undefined} disabled={disabled} onChange={value => onChange({ ...row, contextWindow: value })} />
  </Space> },
  { title: t('aiConfiguration.maxOutput'), dataIndex: 'maxOutputTokens', width: 260, render: (_, row) => <Space size={8} wrap>
   <InputNumber style={{ width: 116 }} aria-label={t('aiConfiguration.outputFor', { model: row.id })} min={16} max={393216} step={256} precision={0} status={row.maxOutputTokens >= row.contextWindow ? 'error' : undefined} disabled={disabled} value={row.maxOutputTokens} onChange={value => { if (value) onChange({ ...row, maxOutputTokens: value }); }} />
   <Select style={{ width: 104 }} aria-label={t('aiConfiguration.outputPresetFor', { model: row.id })} placeholder={t('aiConfiguration.contextPreset')} options={outputOptions} value={outputPresets.includes(row.maxOutputTokens) ? row.maxOutputTokens : undefined} disabled={disabled} onChange={value => onChange({ ...row, maxOutputTokens: value })} />
  </Space> },
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
  <ResponsiveTable rowKey="id" size="small" columns={columns} dataSource={filtered} pagination={{ pageSize: 8, showSizeChanger: false }} scroll={{ x: 1080 }} locale={{ emptyText: t('aiConfiguration.noModels') }} />
 </div>;
}
