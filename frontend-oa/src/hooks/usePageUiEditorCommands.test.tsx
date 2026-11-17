import { act, renderHook } from '@testing-library/react';
import type { FormInstance } from 'antd';
import dayjs from 'dayjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PageUiCommandHandlers } from '@/lib/pageUiCommands';
import { usePageUiEditorCommands, requireUiPagination, uiDateRange } from './usePageUiEditorCommands';

const registry = vi.hoisted(() => ({ handlers: {} as PageUiCommandHandlers, preview: vi.fn() }));
vi.mock('@/components/oa/PageUiCommandBridge', () => ({ useRegisterPageUiCommands: (handlers: PageUiCommandHandlers) => { registry.handlers = handlers; } }));
vi.mock('@/components/oa/UiFormPreview', () => ({ useUiFormPreview: () => registry.preview }));

describe('shared unsubmitted editor operations', () => {
  const setFieldsValue = vi.fn(), validateFields = vi.fn();
  const form = { setFieldsValue, validateFields } as unknown as FormInstance<Record<string, unknown>>;
  const fields = { reason: 'string', amount: 'number', status: ['DRAFT', 'READY'], date: { date: 'date' } } as const;
  const labels = { reason: 'Reason', date: 'Date' };
  const context = { sessionId: 'form_session_123456' };
  beforeEach(() => { vi.clearAllMocks(); validateFields.mockResolvedValue({ reason: 'synthetic', date: dayjs('2026-11-03'), secret: 'not-for-preview' }); });
  const fill = (values: Record<string, string | number | boolean>) => registry.handlers['ui.fillForm']!({ code: 'ui.fillForm', pageId: 'todo', payload: { values } });

  it('fills only allowed typed fields and parses exact dates before validation', async () => {
    renderHook(() => usePageUiEditorCommands({ form, open: true, canEdit: true, context, fields, labels }));
    await act(async () => { await fill({ reason: 'synthetic', date: '2026-11-03' }); });
    expect(setFieldsValue).toHaveBeenCalledWith({ reason: 'synthetic', date: expect.anything() });
    expect(dayjs(setFieldsValue.mock.calls[0][0].date).format('YYYY-MM-DD')).toBe('2026-11-03');
    expect(validateFields).toHaveBeenCalledWith(['reason', 'date']);
    expect(registry.preview).not.toHaveBeenCalled();
  });
  const invalidFields: Record<string, string>[] = [{ date: '2026-02-30' }, { amount: '3' }, { unknown: 'value' }, { status: 'ADMIN' }];
  it.each(invalidFields)('refuses invalid values before modifying form %#', async values => {
    renderHook(() => usePageUiEditorCommands({ form, open: true, canEdit: true, context, fields, labels }));
    await expect(fill(values)).rejects.toThrow();
    expect(setFieldsValue).not.toHaveBeenCalled();
  });
  it('previews only labelled validated values and never exposes submission callbacks', async () => {
    renderHook(() => usePageUiEditorCommands({ form, open: true, canEdit: true, context, fields, labels }));
    await act(async () => { await registry.handlers['ui.previewSubmission']!({ code: 'ui.previewSubmission', pageId: 'todo', payload: {} }); });
    expect(registry.preview).toHaveBeenCalledWith({ reason: 'synthetic', date: '2026-11-03' }, labels);
    expect(Object.keys(registry.handlers)).toEqual(['ui.fillForm', 'ui.previewSubmission']);
  });
  it('does not show late validation results after the form is replaced or permission is revoked', async () => {
    let resolve!: (values: Record<string, string>) => void;
    validateFields.mockReturnValue(new Promise<Record<string, string>>(done => { resolve = done; }));
    const hook = renderHook(({ editable }) => usePageUiEditorCommands({ form, open: true, canEdit: editable, context, fields, labels }), { initialProps: { editable: true } });
    const result = registry.handlers['ui.previewSubmission']!({ code: 'ui.previewSubmission', pageId: 'todo', payload: {} });
    const rejected = expect(result).rejects.toThrow('PAGE_UI_FORM_CLOSED');
    hook.rerender({ editable: false });
    await act(async () => { resolve({ reason: 'private prior value' }); await rejected; });
    expect(registry.preview).not.toHaveBeenCalled();
    expect(registry.handlers['ui.fillForm']).toBeUndefined();
  });
  it('validates list paging and complete date ranges without guessing missing endpoints', () => {
    expect(() => requireUiPagination({ page: 0 })).toThrow();
    expect(() => requireUiPagination({ size: 101 })).toThrow();
    expect(() => requireUiPagination({ page: 2, size: 20 })).not.toThrow();
    expect(() => uiDateRange({ from: '2026-02-30', to: '2026-03-01' })).toThrow();
    expect(() => uiDateRange({ from: '2026-11-04', to: '2026-11-03' })).toThrow();
    expect(() => uiDateRange({ from: '2026-11-03' })).toThrow();
    expect(uiDateRange({ from: '2026-11-03', to: '2026-11-04' })).toEqual(['2026-11-03T00:00:00', '2026-11-04T00:00:00']);
  });
});
