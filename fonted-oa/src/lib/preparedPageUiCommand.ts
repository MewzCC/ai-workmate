import type { PageUiCommand } from './pageUiCommands';
import { isUiFormContext, parsePageUiCommand, PAGE_UI_COMMAND_CODES } from './pageUiCommands';

/** Converts a closed Gateway result into local UI state, never a URL, selector or executable callback. */
export function preparedPageUiCommand(input: unknown): PageUiCommand {
  if (!input || typeof input !== 'object' || Array.isArray(input)) throw new Error('PAGE_UI_COMMAND_REJECTED');
  const data = input as Record<string, unknown>;
  if (Object.keys(data).some((key) => !['pageId', 'code', 'targetPageId', 'recordId', 'fields', 'formContext'].includes(key))
      || typeof data.pageId !== 'string' || !Array.isArray(data.fields) || data.fields.length > 32)
    throw new Error('PAGE_UI_COMMAND_REJECTED');
  const formContext = data.formContext;
  if (formContext !== undefined && !isUiFormContext(formContext)) throw new Error('PAGE_UI_COMMAND_REJECTED');
  if (formContext !== undefined && data.code !== 'ui.fillForm' && data.code !== 'ui.previewSubmission')
    throw new Error('PAGE_UI_COMMAND_REJECTED');
  const values: Record<string, string | number | boolean> = {};
  for (const field of data.fields) {
    if (!field || typeof field !== 'object' || Array.isArray(field) || Object.keys(field).length !== 2
        || !Object.prototype.hasOwnProperty.call(field, 'name') || !Object.prototype.hasOwnProperty.call(field, 'value') || typeof field.name !== 'string'
        || !/^[a-z][A-Za-z0-9]{0,39}$/.test(field.name)
        || ['__proto__', 'prototype', 'constructor'].includes(field.name) || Object.prototype.hasOwnProperty.call(values, field.name)
        || !['string', 'number', 'boolean'].includes(typeof field.value)
        || typeof field.value === 'string' && field.value.length > 500
        || typeof field.value === 'number' && (!Number.isFinite(field.value) || Math.abs(field.value) > 1e12)) throw new Error('PAGE_UI_COMMAND_REJECTED');
    values[field.name] = field.value;
  }
  if (data.code !== 'ui.navigate' && data.targetPageId !== undefined || data.code !== 'ui.openDetail' && data.recordId !== undefined
      || !['ui.applyFilter', 'ui.fillForm'].includes(String(data.code)) && data.fields.length > 0)
    throw new Error('PAGE_UI_COMMAND_REJECTED');
  if (data.code === 'ui.openDetail' && !(typeof data.recordId === 'number' && Number.isSafeInteger(data.recordId) && data.recordId > 0
      || data.pageId === 'ai-tasks' && typeof data.recordId === 'string'
        && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.recordId)))
    throw new Error('PAGE_UI_COMMAND_REJECTED');
  const payload = data.code === 'ui.navigate' ? { targetPageId: data.targetPageId }
    : data.code === 'ui.openDetail' ? { recordId: data.recordId }
    : data.code === 'ui.applyFilter' ? { filters: values } : data.code === 'ui.fillForm' ? { values, ...(formContext ? { formContext } : {}) }
    : data.code === 'ui.previewSubmission' && formContext ? { formContext } : {};
  return parsePageUiCommand({ code: data.code, pageId: data.pageId, payload }, {
    currentPageId: data.pageId, allowedCommands: PAGE_UI_COMMAND_CODES,
    allowedTargetPageIds: typeof data.targetPageId === 'string' ? [data.targetPageId] : [],
  });
}

export function requireUiFields(values: Record<string, unknown>, allowed: readonly string[]): void {
  if (Object.keys(values).some((key) => !allowed.includes(key))) throw new Error('PAGE_UI_FIELD_REJECTED');
}

export function requireUiFieldTypes(values: Record<string, unknown>, fields: Record<string, 'string' | 'number' | 'boolean' | readonly string[]>): void {
  requireUiFields(values, Object.keys(fields));
  for (const [name, value] of Object.entries(values)) {
    const expected = fields[name];
    if (typeof expected === 'string' ? typeof value !== expected || typeof value === 'number' && !Number.isFinite(value)
      : typeof value !== 'string' || !expected.includes(value)) throw new Error('PAGE_UI_FIELD_REJECTED');
  }
}
