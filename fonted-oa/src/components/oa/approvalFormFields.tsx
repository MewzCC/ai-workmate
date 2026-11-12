import { Checkbox, DatePicker, Input, InputNumber, Radio, Select, TimePicker } from 'antd';
import dayjs from 'dayjs';
import type { ApprovalApplicationTemplate } from '@/lib/approvalEngineApi';
import type { UiEditorField } from '@/hooks/usePageUiEditorCommands';

export type ApplicationField = ApprovalApplicationTemplate['fields'][number];
const SCALAR_FIELDS = new Set(['text', 'textarea', 'number', 'amount', 'money', 'date', 'time', 'radio', 'select']);
const SIMPLE = new Set([...SCALAR_FIELDS, 'dateRange', 'checkbox', 'user', 'department']);
export const isApplicationReferenceField = (field: ApplicationField) => field.type === 'user' || field.type === 'department';
export const supportedApplicationField = (field: ApplicationField) => SIMPLE.has(field.type || 'text');

export function approvalUiFields(fields: ApplicationField[]): Record<string, UiEditorField> {
  return Object.fromEntries(fields.filter(field => SCALAR_FIELDS.has(field.type || 'text')).map(field => [field.name,
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
    case 'dateRange': return <DatePicker.RangePicker style={{ width: '100%' }} />;
    case 'time': return <TimePicker {...shared} style={{ width: '100%' }} format="HH:mm" />;
    case 'radio': return <Radio.Group options={field.options.map(value => ({ value, label: value }))} />;
    case 'checkbox': return <Checkbox.Group options={field.options} />;
    case 'select': return <Select {...shared} allowClear options={field.options.map(value => ({ value, label: value }))} />;
    default: return <Input {...shared} disabled={supportedApplicationField(field) ? undefined : true} maxLength={500} />;
  }
}
function restoreDateRange(value: unknown) {
  if (!Array.isArray(value) || value.length !== 2) throw new Error('PAGE_UI_FIELD_REJECTED');
  const dates = value.map((item: unknown) => {
    if (typeof item !== 'string') throw new Error('PAGE_UI_FIELD_REJECTED');
    const date = dayjs(item);
    if (!date.isValid() || date.format('YYYY-MM-DD') !== item) throw new Error('PAGE_UI_FIELD_REJECTED');
    return date;
  });
  if (dates[0].isAfter(dates[1], 'day')) throw new Error('PAGE_UI_FIELD_REJECTED');
  return dates;
}

function validateChoices(value: unknown, field: ApplicationField) {
  if (!Array.isArray(value) || value.length > 20 || value.some(item => typeof item !== 'string' || !field.options.includes(item))
    || new Set(value).size !== value.length) throw new Error('PAGE_UI_FIELD_REJECTED');
  return value;
}

function validateReference(value: unknown) {
  if (typeof value !== 'string' || !/^[1-9][0-9]{0,18}$/.test(value)
    || (value.length === 19 && value > '9223372036854775807')) throw new Error('PAGE_UI_FIELD_REJECTED');
  return value;
}

/** Emit configured fields in their wire format, preserving exact money strings. */
export function normalizeApprovalValues(values: Record<string, unknown>, fields: ApplicationField[]) {
  const result: Record<string, unknown> = {};
  fields.forEach(field => {
    const value = values[field.name];
    if (value === undefined || value === null || value === '' || field.type === 'divider') return;
    if (field.type === 'dateRange') {
      const range = Array.isArray(value) ? value.map(item => dayjs.isDayjs(item) ? item.format('YYYY-MM-DD') : item) : value;
      result[field.name] = restoreDateRange(range).map(date => date.format('YYYY-MM-DD'));
      return;
    }
    if (field.type === 'checkbox') {
      const choices = validateChoices(value, field);
      if (choices.length) result[field.name] = choices;
      return;
    }
    if (isApplicationReferenceField(field)) {
      result[field.name] = validateReference(value);
      return;
    }
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
    if (isApplicationReferenceField(field)) {
      if (value !== null) restored[field.name] = validateReference(value);
    } else if (field.type === 'dateRange' || field.type === 'checkbox') {
      if (value === null) return;
      restored[field.name] = field.type === 'dateRange' ? restoreDateRange(value) : validateChoices(value, field);
    } else if (field.type === 'date' || field.type === 'time') {
      if (value === null || value === '') return;
      const format = field.type === 'time' ? 'HH:mm' : 'YYYY-MM-DD';
      const date = dayjs(field.type === 'time' ? `2000-01-01T${value}:00` : String(value));
      if (typeof value !== 'string' || !date.isValid() || date.format(format) !== value) throw new Error('PAGE_UI_FIELD_REJECTED');
      restored[field.name] = date;
    } else restored[field.name] = value;
  });
  return restored;
}
