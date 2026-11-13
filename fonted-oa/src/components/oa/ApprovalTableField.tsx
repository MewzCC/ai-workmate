import { Button, DatePicker, Input, InputNumber, Radio, Select, Space, TimePicker } from 'antd';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import type { ColumnsType } from 'antd/es/table';
import { APPROVAL_TABLE_MAX_ROWS, validateTableColumns, type ApprovalTableColumn } from '@/lib/approvalTable';
import { OaIcon } from '@/components/OaIcon';
import ResponsiveTable from './ResponsiveTable';
import type { ApprovalApplicationAgentField } from '@/lib/approvalEngineApi';

export function ApprovalTableDetail({ table }: { table: NonNullable<ApprovalApplicationAgentField['table']> }) {
  return <ResponsiveTable<{ index: number; cells: string[] }> rowKey="index" size="small" pagination={false}
    dataSource={table.rows.map((cells, index) => ({ index, cells }))}
    columns={table.columns.map((column, index) => ({ key: column.name, title: column.label,
      render: (_: unknown, row: { cells: string[] }) => row.cells[index] || '-' }))}
    scroll={{ x: table.columns.length * 150 }} />;
}

function TableCell({ column, value, disabled, onChange }: {
  column: ApprovalTableColumn; value: unknown; disabled: boolean; onChange: (value: unknown) => void;
}) {
  const label = column.label;
  if (column.type === 'money' || column.type === 'number') return <InputNumber aria-label={label} style={{ width: '100%' }}
    stringMode={column.type === 'money'} disabled={disabled} min={column.min ?? undefined}
    value={typeof value === 'string' || typeof value === 'number' ? value : undefined} onChange={onChange} />;
  if (column.type === 'date') return <DatePicker aria-label={label} style={{ width: '100%' }} disabled={disabled}
    value={dayjs.isDayjs(value) ? value : null} onChange={onChange} />;
  if (column.type === 'time') return <TimePicker aria-label={label} style={{ width: '100%' }} disabled={disabled}
    format="HH:mm" value={dayjs.isDayjs(value) ? value : null} onChange={onChange} />;
  if (column.type === 'select') return <Select aria-label={label} style={{ width: '100%' }} allowClear disabled={disabled}
    value={typeof value === 'string' ? value : undefined} options={column.options.map(option => ({ value: option, label: option }))} onChange={onChange} />;
  if (column.type === 'radio') return <Radio.Group aria-label={label} disabled={disabled} value={value}
    options={column.options} onChange={event => onChange(event.target.value)} />;
  if (column.type === 'textarea') return <Input.TextArea aria-label={label} disabled={disabled} maxLength={500}
    value={value == null ? '' : String(value)} onChange={event => onChange(event.target.value)} />;
  return <Input aria-label={label} disabled={disabled} maxLength={500}
    value={value == null ? '' : String(value)} onChange={event => onChange(event.target.value)} />;
}

export function ApprovalTableField({ columns: definition, value: providedValue = [], onChange, disabled = false }: {
  columns: ApprovalTableColumn[]; value?: Record<string, unknown>[]; onChange?: (rows: Record<string, unknown>[]) => void; disabled?: boolean;
}) {
  const { t } = useTranslation();
  const value = Array.isArray(providedValue) ? providedValue : [];
  const definitionColumns = validateTableColumns(definition);
  type Row = { index: number; cells: Record<string, unknown> };
  const columns: ColumnsType<Row> = definitionColumns.map(column => ({
    key: column.name, title: column.label, width: 180,
    render: (_: unknown, row: Row) => <TableCell column={column}
      value={Object.prototype.hasOwnProperty.call(row.cells, column.name) ? row.cells[column.name] : undefined} disabled={disabled}
      onChange={cell => onChange?.(value.map((item, index) => index === row.index ? { ...item, [column.name]: cell } : item))} />,
  }));
  columns.push({ key: 'row-actions', title: t('common.actions'), fixed: 'right', width: 100,
    render: (_: unknown, row: Row) => <Button danger disabled={disabled} onClick={() => onChange?.(value.filter((_, index) => index !== row.index))}>
      {t('approval.table.removeRow')}</Button> });
  return <Space orientation="vertical" style={{ width: '100%' }}>
    <ResponsiveTable<Row> rowKey="index" size="small" columns={columns} pagination={false}
      dataSource={value.map((cells, index) => ({ index, cells }))} scroll={{ x: definitionColumns.length * 180 + 100 }} />
    <Button icon={<OaIcon name="add" />} disabled={disabled || value.length >= APPROVAL_TABLE_MAX_ROWS}
      onClick={() => onChange?.([...value, {}])}>{t('approval.table.addRow')}</Button>
  </Space>;
}
