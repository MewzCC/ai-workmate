import { expect, it } from 'vitest';
import dayjs from 'dayjs';
import { approvalUiFields, normalizeApprovalValues, restoreApprovalValues, type ApplicationField } from './approvalFormFields';

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
