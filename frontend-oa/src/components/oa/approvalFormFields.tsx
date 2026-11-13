import { DatePicker, Input, InputNumber, Radio, Select, TimePicker } from 'antd';
import dayjs from 'dayjs';
import type { ApprovalApplicationTemplate } from '@/lib/approvalEngineApi';
import type { UiEditorField } from '@/hooks/usePageUiEditorCommands';

export type ApplicationField = ApprovalApplicationTemplate['fields'][number];
const SIMPLE = new Set(['text', 'textarea', 'number', 'amount', 'money', 'date', 'time', 'radio', 'select']);
export const supportedApplicationField = (field: ApplicationField) => SIMPLE.has(field.type || 'text');

export function approvalUiFields(fields: ApplicationField[]): Record<string, UiEditorField> {
  return Object.fromEntries(fields.filter(supportedApplicationField).map(field => [field.name,
    field.type === 'date' || field.type === 'time' ? { date: field.type }
      : field.type === 'number' ? 'number'
      : ['radio', 'select'].includes(field.type) && field.options.length > 0 ? field.options : 'string']));
}
export function renderApplicationField(field: ApplicationField) {
  const shared = { placeholder: field.placeholder || undefined };
  switch (field.type) {
    case 'textarea': return <Input.TextArea {...shared} rows={4} maxLength={500} showCount />;
    case 'number': return <InputNumber {...shared} min={field.min ?? undefined} style={{ width: '100%' }} />;
    case 'amount': case 'money': return <InputNumber {...shared} stringMode min={field.min ?? undefined} style={{ width: '100%' }} />;
    case 'date': return <DatePicker {...shared} style={{ width: '100%' }} />;
    case 'time': return <TimePicker {...shared} style={{ width: '100%' }} format="HH:mm" />;
    case 'radio': return <Radio.Group options={field.options.map(value => ({ value, label: value }))} />;
    case 'select': return <Select {...shared} allowClear options={field.options.map(value => ({ value, label: value }))} />;
    default: return <Input {...shared} disabled={supportedApplicationField(field) ? undefined : true} maxLength={500} />;
  }
}
/** Emit only configured fields and JSON scalars, preserving exact money strings. */
export function normalizeApprovalValues(values: Record<string, unknown>, fields: ApplicationField[]) {
  const result: Record<string, unknown> = {};
  fields.forEach(field => {
    const value = values[field.name];
    if (value === undefined || value === null || value === '' || field.type === 'divider') return;
    result[field.name] = dayjs.isDayjs(value) ? value.format(field.type === 'time' ? 'HH:mm' : 'YYYY-MM-DD') : value;
  });
  return result;
}
export function restoreApprovalValues(dataJson: string, fields: ApplicationField[]) {
  // Validate JSON first, then preserve numeric tokens before JavaScript can round money values.
  JSON.parse(dataJson);
  const parsed: unknown = JSON.parse(dataJson.replace(/"(?:\\.|[^"\\])*"|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)/g,
    (token, numeric: string | undefined) => numeric === undefined ? token : JSON.stringify(numeric)));
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('PAGE_UI_FIELD_REJECTED');
  const restored: Record<string, unknown> = {};
  fields.forEach(field => {
    const value = (parsed as Record<string, unknown>)[field.name];
    if (value === undefined || field.type === 'divider') return;
    if (field.type === 'date' || field.type === 'time') {
      if (value === null || value === '') return;
      const format = field.type === 'time' ? 'HH:mm' : 'YYYY-MM-DD';
      const date = dayjs(field.type === 'time' ? `2000-01-01T${value}:00` : String(value));
      if (typeof value !== 'string' || !date.isValid() || date.format(format) !== value) throw new Error('PAGE_UI_FIELD_REJECTED');
      restored[field.name] = date;
    } else restored[field.name] = value;
  });
  return restored;
}
