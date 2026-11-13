import { expect, it } from 'vitest';
import dayjs from 'dayjs';
import { convertApprovalTable, validateTableColumns, type ApprovalTableColumn } from './approvalTable';

const columns: ApprovalTableColumn[] = [
  { name: 'item', label: 'Item', type: 'text', required: true, options: [] },
  { name: 'cost', label: 'Cost', type: 'money', required: false, options: [], min: 0.01 },
  { name: 'date', label: 'Date', type: 'date', required: false, options: [] },
];

it('reads only own row cells and preserves an explicitly supplied constructor column', () => {
  const definition: ApprovalTableColumn[] = [{ ...columns[0], name: 'constructor' }];
  expect(convertApprovalTable([{}], definition)).toEqual([{}]);
  expect(() => convertApprovalTable([{}], definition, { required: true })).toThrow();
  const rows = JSON.parse('[{"constructor":"Explicit cell"}]');
  expect(convertApprovalTable(rows, definition, { required: true })).toEqual(rows);
});

it('requires configured choices before a select or radio column can be used', () => {
  for (const type of ['select', 'radio'] as const) {
    expect(() => validateTableColumns([{ ...columns[0], type, options: [] }])).toThrow();
  }
});

it('round trips exact money and calendar values through an editable table', () => {
  const rows = [{ item: 'Owned row', cost: '9007199254740993.123456789', date: '2026-11-12' }];
  const restored = convertApprovalTable(rows, columns, { restoring: true });
  expect(dayjs.isDayjs(restored[0].date)).toBe(true);
  expect(convertApprovalTable(restored, columns, { required: true })).toEqual(rows);
  expect(convertApprovalTable([{}], columns)).toEqual([{}]);
  expect(() => convertApprovalTable([{}], columns, { required: true })).toThrow();
});

it.each([
  [{ item: { command: 'forbidden' } }],
  [{ item: true }],
  [{ unknown: 'value' }],
  [{ date: '2026-02-30' }],
  [{ cost: 'NaN' }],
  [{ cost: '0.001' }],
  Array.from({ length: 21 }, () => ({ item: 'row' })),
].map(rows => ({ rows })))('rejects malformed or oversized row data', ({ rows }) => {
  expect(() => convertApprovalTable(rows, columns)).toThrow('PAGE_UI_FIELD_REJECTED');
});
