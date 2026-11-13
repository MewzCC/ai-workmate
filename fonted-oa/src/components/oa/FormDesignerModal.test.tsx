import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import { t } from 'i18next';
import type { ApprovalForm } from '@/lib/approvalEngineApi';
import FormDesignerModal from './FormDesignerModal';

vi.mock('@/lib/antdMessage', () => ({ message: { error: vi.fn(), warning: vi.fn() } }));
afterEach(cleanup);

function form(columns: unknown[]): ApprovalForm {
  return { id: 1, formKey: 'expense', formName: 'Expenses', version: 1, status: 'DISABLED',
    schemaJson: JSON.stringify({ fields: [{ name: 'items', label: 'Items', type: 'table', required: true, width: 'full', options: [], columns }] }),
    createdAt: '2026-11-12T00:00:00', updatedAt: '2026-11-12T00:00:00', canEdit: true, canDelete: false };
}

it('saves bounded columns without leaking builder identifiers or dropping decimal constraints', async () => {
  const submit = vi.fn().mockResolvedValue(undefined);
  const columns = [{ name: 'amount', label: 'Exact amount', type: 'money', required: true, options: [], min: 0 }];
  render(<FormDesignerModal open editing={form(columns)} loading={false} onClose={vi.fn()} onSubmit={submit} />);
  await screen.findByText('Items *');
  fireEvent.click(screen.getByRole('button', { name: t('common.save') }));
  await waitFor(() => expect(submit).toHaveBeenCalledTimes(1));
  const field = JSON.parse(submit.mock.calls[0][0].schemaJson).fields[0];
  expect(field.columns).toEqual(columns);
  expect(field.name).toBe('items');
  expect(field).not.toHaveProperty('id');
  expect(field).not.toHaveProperty('key');
});

it('shows a safe incomplete-column preview and blocks saving malformed columns', async () => {
  const submit = vi.fn();
  render(<FormDesignerModal open editing={form([{ name: '', label: '', type: 'text', required: false, options: [] }])}
    loading={false} onClose={vi.fn()} onSubmit={submit} />);
  await screen.findByText('Items *');
  fireEvent.click(screen.getByRole('button', { name: new RegExp(t('approval.builder.preview')) }));
  expect(await screen.findByText(t('approval.table.configureColumns'))).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: t('common.save') }));
  await waitFor(() => expect(submit).not.toHaveBeenCalled());
});
