import dayjs from 'dayjs';

export interface ApprovalTableColumn {
  name: string;
  label: string;
  type: 'text' | 'textarea' | 'number' | 'money' | 'date' | 'time' | 'radio' | 'select';
  required: boolean;
  options: string[];
  min?: number | null;
}

export const APPROVAL_TABLE_MAX_ROWS = 20;
export const APPROVAL_TABLE_MAX_COLUMNS = 10;
export function validateTableFieldName(name: string) {
  if (['constructor', 'prototype', '__proto__'].includes(name)) throw new Error('PAGE_UI_FIELD_REJECTED');
}
export const APPROVAL_TABLE_COLUMN_TYPES: ApprovalTableColumn['type'][] = ['text', 'textarea', 'number', 'money', 'date', 'time', 'radio', 'select'];
const types = new Set(APPROVAL_TABLE_COLUMN_TYPES);

export function validateTableColumns(columns: ApprovalTableColumn[] | undefined): ApprovalTableColumn[] {
  if (!columns?.length || columns.length > APPROVAL_TABLE_MAX_COLUMNS) throw new Error('PAGE_UI_FIELD_REJECTED');
  const names = new Set<string>();
  for (const column of columns) {
    if (!/^[a-z][A-Za-z0-9_-]{0,39}$/.test(column.name) || names.has(column.name)
      || typeof column.label !== 'string' || !column.label.trim() || column.label.length > 40 || !types.has(column.type)
      || typeof column.required !== 'boolean' || !Array.isArray(column.options) || column.options.length > 10
      || column.options.some(option => typeof option !== 'string' || !option.trim() || option.length > 80)
      || ['radio', 'select'].includes(column.type) && column.options.length === 0
      || new Set(column.options).size !== column.options.length
      || column.min != null && (!['number', 'money'].includes(column.type) || !Number.isFinite(column.min) || Math.abs(column.min) > 1e15))
      throw new Error('PAGE_UI_FIELD_REJECTED');
    names.add(column.name);
  }
  return columns;
}

function convertCell(value: unknown, column: ApprovalTableColumn, restoring: boolean): unknown {
  if (column.type === 'date' || column.type === 'time') {
    const format = column.type === 'date' ? 'YYYY-MM-DD' : 'HH:mm';
    const text = dayjs.isDayjs(value) ? value.format(format) : value;
    if (typeof text !== 'string') throw new Error('PAGE_UI_FIELD_REJECTED');
    const date = dayjs(column.type === 'time' ? `2000-01-01T${text}:00` : text);
    if (!date.isValid() || date.format(format) !== text) throw new Error('PAGE_UI_FIELD_REJECTED');
    return restoring ? date : text;
  }
  if (!['string', 'number', 'boolean'].includes(typeof value)) throw new Error('PAGE_UI_FIELD_REJECTED');
  const text = String(value);
  if (text.length > 500) throw new Error('PAGE_UI_FIELD_REJECTED');
  if (['text', 'textarea'].includes(column.type) && typeof value !== 'string') throw new Error('PAGE_UI_FIELD_REJECTED');
  if (column.type === 'number' || column.type === 'money') {
    if (!/^-?[0-9]+(?:\.[0-9]+)?$/.test(text) || !Number.isFinite(Number(text))
      || column.min != null && Number(text) < column.min) throw new Error('PAGE_UI_FIELD_REJECTED');
  }
  if ((column.type === 'select' || column.type === 'radio')
    && (typeof value !== 'string' || column.options.length > 0 && !column.options.includes(value)))
    throw new Error('PAGE_UI_FIELD_REJECTED');
  return value;
}

export function convertApprovalTable(value: unknown, definition: ApprovalTableColumn[] | undefined,
  { restoring = false, required = false }: { restoring?: boolean; required?: boolean } = {}) {
  const columns = validateTableColumns(definition);
  if (!Array.isArray(value) || value.length > APPROVAL_TABLE_MAX_ROWS) throw new Error('PAGE_UI_FIELD_REJECTED');
  const names = new Set(columns.map(column => column.name));
  return value.map((item: unknown) => {
    if (!item || typeof item !== 'object' || Array.isArray(item) || Object.keys(item).some(name => !names.has(name)))
      throw new Error('PAGE_UI_FIELD_REJECTED');
    const row = item as Record<string, unknown>;
    const converted: [string, unknown][] = [];
    for (const column of columns) {
      const cell = Object.prototype.hasOwnProperty.call(row, column.name) ? row[column.name] : undefined;
      if (cell == null || typeof cell === 'string' && !cell.trim()) {
        if (required && column.required) throw new Error('PAGE_UI_FIELD_REJECTED');
      } else converted.push([column.name, convertCell(cell, column, restoring)]);
    }
    return Object.fromEntries(converted);
  });
}
