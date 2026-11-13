import { describe, expect, it } from 'vitest';
import { preparedPageUiCommand, requireUiFieldTypes } from './preparedPageUiCommand';

describe('closed Gateway page result conversion', () => {
  const fill = { pageId: 'todo', code: 'ui.fillForm', fields: [{ name: 'reason', value: 'synthetic' }],
    formContext: { sessionId: 'form_session_123456', recordId: 7, version: 2 } };
  it('preserves typed values and the form ownership snapshot', () => {
    expect(preparedPageUiCommand(fill)).toEqual({ pageId: 'todo', code: 'ui.fillForm',
      payload: { values: { reason: 'synthetic' }, formContext: fill.formContext } });
  });
  it.each([
    { ...fill, url: 'https://invalid.example' },
    { ...fill, code: 'ui.submit' },
    { ...fill, fields: [{ name: 'tenantId', value: 9 }] },
    { ...fill, fields: [{ name: 'tenantid', value: 9 }] },
    { ...fill, fields: [{ name: 'password', value: 'unsafe' }] },
    { ...fill, fields: [{ name: '__proto__', value: 'unsafe' }] },
    { ...fill, fields: [{ name: 'reason', value: 'a' }, { name: 'reason', value: 'b' }] },
    { ...fill, fields: [{ name: 'invalid_field', value: 'a' }] },
    { ...fill, fields: [{ name: 'amount', value: Infinity }] },
    { ...fill, fields: [{ name: 'amount', value: 1e12 + 1 }] },
    { ...fill, fields: [{ name: 'reason', value: 'x'.repeat(501) }] },
    { ...fill, formContext: { sessionId: 'form_session_123456', recordId: 7 } },
    { ...fill, code: 'ui.refreshPage', fields: [] },
  ])('rejects unsafe or out-of-contract results %#', value => {
    expect(() => preparedPageUiCommand(value)).toThrow('PAGE_UI_COMMAND_REJECTED');
  });
  it('does not coerce numbers or booleans into form enum values', () => {
    expect(() => requireUiFieldTypes({ status: 1 }, { status: ['1', '2'] })).toThrow();
    expect(() => requireUiFieldTypes({ status: true }, { status: ['true'] })).toThrow();
    expect(() => requireUiFieldTypes({ status: '1' }, { status: ['1', '2'] })).not.toThrow();
  });
  it('allows a task UUID only on task details and refuses arbitrary selectors', () => {
    const detail = { pageId: 'ai-tasks', code: 'ui.openDetail', recordId: '12345678-1234-1234-1234-123456789abc', fields: [] };
    expect(preparedPageUiCommand(detail).code).toBe('ui.openDetail');
    expect(() => preparedPageUiCommand({ ...detail, pageId: 'todo' })).toThrow();
    expect(() => preparedPageUiCommand({ ...detail, recordId: 'arbitrary-name' })).toThrow();
  });
});
