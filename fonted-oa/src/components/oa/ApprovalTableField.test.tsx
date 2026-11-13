import { useState } from 'react';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { t } from 'i18next';
import { Form } from 'antd';
import { ApprovalTableDetail, ApprovalTableField } from './ApprovalTableField';
import type { ApprovalTableColumn } from '@/lib/approvalTable';

afterEach(cleanup);
const columns: ApprovalTableColumn[] = [{ name: 'note', label: 'Row description', type: 'text', required: true, options: [] }];

function Editor({ initial = [], disabled = false }: { initial?: Record<string, unknown>[]; disabled?: boolean }) {
  const [rows, setRows] = useState(initial);
  return <><ApprovalTableField columns={columns} value={rows} onChange={setRows} disabled={disabled} />
    <output data-testid="rows">{JSON.stringify(rows)}</output></>;
}

it('adds edits and removes controlled rows without submitting a business operation', () => {
  render(<Editor />);
  fireEvent.click(screen.getByRole('button', { name: t('approval.table.addRow') }));
  fireEvent.change(screen.getByLabelText('Row description'), { target: { value: 'A real line' } });
  expect(screen.getByTestId('rows').textContent).toBe('[{"note":"A real line"}]');
  fireEvent.click(screen.getByRole('button', { name: t('approval.table.removeRow') }));
  expect(screen.getByTestId('rows').textContent).toBe('[]');
});

it('blocks a twenty-first row and disables all row actions while the form is locked', () => {
  const view = render(<Editor initial={Array.from({ length: 20 }, () => ({ note: 'Line' }))} />);
  expect(screen.getByRole('button', { name: t('approval.table.addRow') })).toBeDisabled();
  view.unmount();
  render(<Editor initial={[{ note: 'Line' }]} disabled />);
  expect(screen.getByLabelText('Row description')).toBeDisabled();
  expect(screen.getByRole('button', { name: t('approval.table.removeRow') })).toBeDisabled();
});

it('shows exact frozen money as text without rendering executable HTML', () => {
  render(<ApprovalTableDetail table={{ columns: [{ name: 'amount', label: 'Frozen amount', type: 'money' }],
    rows: [['9007199254740993.01'], ['<script>unsafe()</script>']] }} />);
  expect(screen.getByText('9007199254740993.01')).toBeInTheDocument();
  expect(screen.getByText('<script>unsafe()</script>')).toBeInTheDocument();
  expect(document.querySelector('script')).toBeNull();
});

it('treats an inherited object property as an empty cell', () => {
  render(<ApprovalTableField columns={[{ ...columns[0], name: 'constructor', label: 'Own value' }]} value={[{}]} />);
  expect(screen.getByLabelText('Own value')).toHaveValue('');
});

it('edits controlled rows inside an actual Ant Design form', async () => {
  render(<Form><Form.Item name="items"><ApprovalTableField columns={columns} /></Form.Item></Form>);
  fireEvent.click(screen.getByRole('button', { name: t('approval.table.addRow') }));
  fireEvent.change(await screen.findByLabelText('Row description'), { target: { value: 'Own row' } });
  expect(screen.getByLabelText('Row description')).toHaveValue('Own row');
  fireEvent.click(screen.getByRole('button', { name: t('approval.table.removeRow') }));
  expect(screen.queryByLabelText('Row description')).toBeNull();
});
