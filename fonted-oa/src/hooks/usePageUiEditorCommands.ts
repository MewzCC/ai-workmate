import type { FormInstance } from 'antd';
import dayjs from 'dayjs';
import type { PageUiCommandHandlers, UiFormContext } from '@/lib/pageUiCommands';
import { requireUiFieldTypes } from '@/lib/preparedPageUiCommand';
import { useRegisterPageUiCommands } from '@/components/oa/PageUiCommandBridge';
import { useUiFormPreview } from '@/components/oa/UiFormPreview';
import { useAsyncActionGuard } from './useAsyncActionGuard';

export type UiEditorField = 'string' | 'number' | 'boolean' | readonly string[] | { date: 'date' | 'time' | 'dateTime' };

/** Shared unsaved-form operations. There is deliberately no submit or save callback. */
export function usePageUiEditorCommands<T extends object>({ form, open, canEdit, context, create, fields, labels, commands = {}, viewKey }: {
  form: FormInstance<T>; open: boolean; canEdit: boolean; context: UiFormContext | null;
  create?: () => void | Promise<void>; fields: Record<string, UiEditorField>; labels: Record<string, string>; commands?: PageUiCommandHandlers;
  viewKey?: string;
}) {
  const { capture } = useAsyncActionGuard(`${canEdit}:${open}:${context?.sessionId ?? ''}`);
  const presentPreview = useUiFormPreview(`${canEdit}:${open}:${context?.sessionId ?? ''}`);
  useRegisterPageUiCommands({ ...commands, ...(canEdit ? {
    ...(create ? { 'ui.openCreateForm': () => create() } : {}),
    'ui.fillForm': async ({ payload: { values } }: Extract<import('@/lib/pageUiCommands').PageUiCommand, { code: 'ui.fillForm' }>) => {
      const current = capture();
      if (!current() || !open || !context) throw new Error('PAGE_UI_FORM_CLOSED');
      requireUiFieldTypes(values, Object.fromEntries(Object.entries(fields).map(([name, rule]) =>
        [name, typeof rule === 'object' && 'date' in rule ? 'string' : rule])));
      const converted: Record<string, unknown> = { ...values };
      for (const [name, value] of Object.entries(values)) {
        const rule = fields[name];
        if (typeof rule !== 'object' || !('date' in rule)) continue;
        const text = String(value), kind = rule.date;
        const parsed = dayjs(kind === 'time' ? `2000-01-01T${text}:00` : text);
        const format = kind === 'time' ? 'HH:mm' : kind === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss';
        if (!parsed.isValid() || parsed.format(format) !== text) throw new Error('PAGE_UI_FIELD_REJECTED');
        converted[name] = parsed;
      }
      form.setFieldsValue(converted as Parameters<typeof form.setFieldsValue>[0]);
      await form.validateFields(Object.keys(values));
      if (!current()) throw new Error('PAGE_UI_FORM_CLOSED');
    },
    'ui.previewSubmission': async () => {
      const current = capture();
      if (!current() || !open || !context) throw new Error('PAGE_UI_FORM_CLOSED');
      const values = await form.validateFields();
      if (!current()) throw new Error('PAGE_UI_FORM_CLOSED');
      const preview = Object.fromEntries(Object.keys(labels).map((name) => {
        const value = (values as Record<string, unknown>)[name], rule = fields[name];
        const format = typeof rule === 'object' && 'date' in rule
          ? rule.date === 'time' ? 'HH:mm' : rule.date === 'dateTime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD' : 'YYYY-MM-DD';
        return [name, dayjs.isDayjs(value) ? value.format(format) : value];
      }));
      presentPreview(preview, labels);
    },
  } : {}) }, context, viewKey);
}

export function requireUiPagination(values: Record<string, unknown>): void {
  for (const [name, maximum] of [['page', 10000], ['size', 100]] as const) {
    if (values[name] !== undefined && (typeof values[name] !== 'number' || !Number.isInteger(values[name])
        || values[name] < 1 || values[name] > maximum)) throw new Error('PAGE_UI_FIELD_REJECTED');
  }
}

export function uiDateRange(values: Record<string, unknown>, current?: [string, string]): [string, string] | undefined {
  if (values.from === undefined && values.to === undefined) return current;
  const from = String(values.from ?? current?.[0] ?? ''), to = String(values.to ?? current?.[1] ?? '');
  if (!from && !to) return undefined;
  const valid = (text: string) => /^\d{4}-\d{2}-\d{2}(?:T(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d(?:\.\d{1,3})?)?(?:Z|[+-]\d{2}:\d{2})?)?$/.test(text)
    && dayjs(text).isValid() && dayjs(text.slice(0, 10)).format('YYYY-MM-DD') === text.slice(0, 10);
  if (!valid(from) || !valid(to) || dayjs(from).isAfter(dayjs(to))) throw new Error('PAGE_UI_FIELD_REJECTED');
  return [dayjs(from).format('YYYY-MM-DDTHH:mm:ss'), dayjs(to).format('YYYY-MM-DDTHH:mm:ss')];
}
