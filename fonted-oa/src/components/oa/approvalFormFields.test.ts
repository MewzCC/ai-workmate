import { expect, it } from 'vitest';
import dayjs from 'dayjs';
import { approvalUiFields, normalizeApprovalValues, restoreApprovalValues, supportedApplicationField, type ApplicationField } from './approvalFormFields';

const field = (name: string, type: string): ApplicationField => ({ name, type, label: name, required: false, width: 'full', options: [] });
it('restores time and date using strict canonical values and drops removed fields', () => {
  const fields = [field('date', 'date'), field('time', 'time')];
  const values = restoreApprovalValues('{"date":"2026-11-03","time":"08:30","removed":"private"}', fields);
  expect(dayjs.isDayjs(values.time)).toBe(true);
  expect(normalizeApprovalValues(values, fields)).toEqual({ date: '2026-11-03', time: '08:30' });
  expect(() => restoreApprovalValues('{"date":"2026-02-30"}', fields)).toThrow();
  expect(() => restoreApprovalValues('{"time":"25:30"}', fields)).toThrow();
});
it('preserves raw numeric money tokens and quoted text without rounding or rewriting dates', () => {
  const fields = [field('amount', 'money'), field('note', 'text')];
  const note = '123 "x" \\ unchanged';
  const values = restoreApprovalValues('{"amount":9007199254740993.123456789,"note":' + JSON.stringify(note) + '}', fields);
  expect(values).toEqual({ amount: '9007199254740993.123456789', note });
  expect(normalizeApprovalValues(values, fields).amount).toBe('9007199254740993.123456789');
  expect(() => restoreApprovalValues('{"amount":01}', fields)).toThrow();
});
it('limits UI field rules to supported controls and real option values', () => {
  const fields = [field('money', 'money'), { ...field('kind', 'select'), options: ['TRAVEL', 'OTHER'] }, field('file', 'file')];
  expect(approvalUiFields(fields)).toEqual({ money: 'string', kind: ['TRAVEL', 'OTHER'] });
});

it('round trips an ordered range and selected choices without changing their JSON shape', () => {
  const fields = [field('period', 'dateRange'), { ...field('choices', 'checkbox'), options: ['A', 'B'] }];
  expect(fields.every(supportedApplicationField)).toBe(true);
  const payload = { period: ['2026-11-03', '2026-11-05'], choices: ['B', 'A'] };
  const restored = restoreApprovalValues(JSON.stringify(payload), fields);
  expect(restored.period).toEqual([dayjs('2026-11-03'), dayjs('2026-11-05')]);
  expect(normalizeApprovalValues(restored, fields)).toEqual(payload);
  expect(normalizeApprovalValues({ period: null, choices: [] }, fields)).toEqual({});
});

it.each([
  { period: ['2026-11-05', '2026-11-03'] },
  { period: ['2026-02-30', '2026-11-05'] },
  { period: '2026-11-05' },
  { choices: ['A', 'A'] },
  { choices: ['unknown'] },
  { choices: 'A' },
])('rejects a saved value with invalid range or choices %j', (payload) => {
  const fields = [field('period', 'dateRange'), { ...field('choices', 'checkbox'), options: ['A', 'B'] }];
  expect(() => restoreApprovalValues(JSON.stringify(payload), fields)).toThrow('PAGE_UI_FIELD_REJECTED');
});

it('round trips reference identifiers exactly and refuses labels or malformed identifiers', () => {
  const fields = [field('person', 'user'), field('unit', 'department')];
  expect(fields.every(supportedApplicationField)).toBe(true);
  const restored = restoreApprovalValues('{"person":9007199254740993,"unit":"9223372036854775807"}', fields);
  expect(normalizeApprovalValues(restored, fields)).toEqual({ person: '9007199254740993', unit: '9223372036854775807' });
  for (const person of ['Alice', '01', '0', '9223372036854775808', ['1']]) {
    expect(() => restoreApprovalValues(JSON.stringify({ person }), fields)).toThrow('PAGE_UI_FIELD_REJECTED');
  }
});
