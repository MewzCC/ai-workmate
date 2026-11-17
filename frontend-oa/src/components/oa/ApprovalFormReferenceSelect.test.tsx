import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider, Form } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { ApprovalFormReferenceSelect } from './ApprovalFormReferenceSelect';

const api = vi.hoisted(() => ({ formReferenceOptions: vi.fn() }));
vi.mock('@/lib/approvalEngineApi', () => ({ approvalEngineApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: vi.fn() } }));

const selectedId = '9007199254740993';
function view(active = true, scopeKey = 'tenant1:user7:version1') {
  return <ConfigProvider virtual={false}><Form>
    <Form.Item label="Reference field">
      <ApprovalFormReferenceSelect value={selectedId} active={active} scopeKey={scopeKey} formKey="travel" fieldName="person" />
    </Form.Item>
  </Form></ConfigProvider>;
}

beforeEach(() => {
  vi.resetAllMocks();
  api.formReferenceOptions.mockImplementation(async ({ selectedId: requested }) => ({
    records: requested ? [{ id: requested, name: 'Selected employee' }] : [{ id: '12', name: 'Available employee' }], total: 1,
  }));
});
afterEach(cleanup);

it('resolves a selected identifier outside the candidate page without rounding it', async () => {
  render(view());
  expect(await screen.findByText('Selected employee')).toBeInTheDocument();
  expect(api.formReferenceOptions).toHaveBeenCalledWith({ formKey: 'travel', fieldName: 'person', selectedId, page: 1, size: 1 });
  fireEvent.mouseDown(screen.getByRole('combobox'));
  expect(await screen.findByText('Available employee')).toBeInTheDocument();
});

it('does not restore a selected name after the form loses its active scope', async () => {
  let resolve!: (value: { records: { id: string; name: string }[]; total: number }) => void;
  api.formReferenceOptions.mockImplementation(({ selectedId: requested }) => requested
    ? new Promise(done => { resolve = done; }) : Promise.resolve({ records: [], total: 0 }));
  const page = render(view());
  await waitFor(() => expect(resolve).toBeTypeOf('function'));
  page.rerender(view(false, 'tenant1:user7:version2'));
  await act(async () => resolve({ records: [{ id: selectedId, name: 'Old selected employee' }], total: 1 }));
  expect(screen.queryByText('Old selected employee')).toBeNull();
  expect(screen.getByRole('combobox')).toBeDisabled();
});

it('rejects a selected lookup response containing a different identifier', async () => {
  api.formReferenceOptions.mockResolvedValue({ records: [{ id: '99', name: 'Unrelated employee' }], total: 1 });
  render(view());
  await waitFor(() => expect(api.formReferenceOptions).toHaveBeenCalledWith(expect.objectContaining({ selectedId })));
  expect(screen.queryByText('Unrelated employee')).toBeNull();
});
