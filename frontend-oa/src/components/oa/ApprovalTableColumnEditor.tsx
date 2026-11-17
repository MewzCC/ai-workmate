import { Button, Card, Form, Input, InputNumber, Select, Space, Switch } from 'antd';
import { useTranslation } from 'react-i18next';
import { APPROVAL_TABLE_COLUMN_TYPES, APPROVAL_TABLE_MAX_COLUMNS, type ApprovalTableColumn } from '@/lib/approvalTable';

export function ApprovalTableColumnEditor({ value, onChange, typeLabel }: {
  value: ApprovalTableColumn[]; onChange: (columns: ApprovalTableColumn[]) => void;
  typeLabel: (type: ApprovalTableColumn['type']) => string;
}) {
  const { t } = useTranslation();
  const patch = (index: number, change: Partial<ApprovalTableColumn>) =>
    onChange(value.map((column, at) => at === index ? { ...column, ...change } : column));
  const add = () => {
    let index = 1;
    while (value.some(column => column.name === `column${index}`)) index += 1;
    onChange([...value, { name: `column${index}`, label: t('approval.table.defaultColumn', { index }),
      type: 'text', required: false, options: [] }]);
  };
  return <Space orientation="vertical" style={{ width: '100%' }}>
    {value.map((column, index) => <Card key={index} size="small">
      <Form.Item label={t('approval.builder.fieldLabel')}><Input maxLength={40} value={column.label}
        onChange={event => patch(index, { label: event.target.value })} /></Form.Item>
      <Form.Item label={t('approval.builder.fieldKey')}><Input maxLength={40} value={column.name}
        onChange={event => patch(index, { name: event.target.value })} /></Form.Item>
      <Form.Item label={t('approval.table.columnType')}><Select value={column.type}
        options={APPROVAL_TABLE_COLUMN_TYPES.map(type => ({ value: type, label: typeLabel(type) }))}
        onChange={type => patch(index, { type, min: undefined })} /></Form.Item>
      <Form.Item label={t('approval.builder.fieldRequired')}><Switch checked={column.required}
        onChange={required => patch(index, { required })} /></Form.Item>
      {['radio', 'select'].includes(column.type) && <Form.Item label={t('approval.builder.options')}>
        <Select mode="tags" maxCount={10} value={column.options} onChange={options => patch(index, { options })} />
      </Form.Item>}
      {['number', 'money'].includes(column.type) && <Form.Item label={t('approval.table.minimum')}>
        <InputNumber value={column.min} min={-1e15} max={1e15} onChange={min => patch(index, { min })} />
      </Form.Item>}
      <Button danger onClick={() => onChange(value.filter((_, at) => at !== index))}>{t('approval.table.removeColumn')}</Button>
    </Card>)}
    <Button disabled={value.length >= APPROVAL_TABLE_MAX_COLUMNS} onClick={add}>{t('approval.table.addColumn')}</Button>
  </Space>;
}
