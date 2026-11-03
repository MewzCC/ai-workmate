import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider, Form } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { BusinessUserOption } from '@/lib/adminAssetsApi';
import { BusinessUserSelect } from './BusinessUserSelect';

const messages = vi.hoisted(() => ({ error: vi.fn() }));
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
const query = vi.fn();
const person = (id: number): BusinessUserOption => ({ id, name: `Candidate ${id}` });
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function view(active = true, scopeKey = 'tenant1:user7:version1', disabled = false) {
  return <ConfigProvider virtual={false}><Form disabled={disabled}><Form.Item label="Synthetic host" name="host">
    <BusinessUserSelect active={active} scopeKey={scopeKey} query={query} />
  </Form.Item></Form></ConfigProvider>;
}
beforeEach(() => { vi.resetAllMocks(); query.mockResolvedValue({ records: [person(9)], total: 1 }); });
afterEach(cleanup);

it('forwards the AntD form label and preserves inherited form disabling', async () => {
  render(view(true, undefined, true));
  expect(screen.getByLabelText('Synthetic host')).toBeDisabled();
  await waitFor(() => expect(query).toHaveBeenCalledTimes(1));
});

it('does not query while closed and loads domain candidates on opening', async () => {
  const page = render(view(false));
  expect(screen.getByLabelText('Synthetic host')).toBeDisabled();
  await act(async () => { await new Promise(resolve => setTimeout(resolve, 20)); });
  expect(query).not.toHaveBeenCalled();
  page.rerender(view());
  await waitFor(() => expect(query).toHaveBeenCalledWith({ keyword: undefined, page: 1, size: 20 }));
  fireEvent.mouseDown(screen.getByLabelText('Synthetic host'));
  expect(await screen.findByText('Candidate 9')).toBeTruthy();
});

it('debounces domain searches and never uses local filtering as a substitute for the API', async () => {
  render(view());
  await waitFor(() => expect(query).toHaveBeenCalledTimes(1));
  const input = screen.getByLabelText('Synthetic host');
  fireEvent.change(input, { target: { value: 'Cand' } });
  fireEvent.change(input, { target: { value: ' Candidate 9 ' } });
  await waitFor(() => expect(query).toHaveBeenCalledTimes(2));
  expect(query).toHaveBeenLastCalledWith({ keyword: 'Candidate 9', page: 1, size: 20 });
});

it('loads another page on scroll once and deduplicates overlapping candidates', async () => {
  const late = deferred<{ records: BusinessUserOption[]; total: number }>();
  query.mockResolvedValueOnce({ records: Array.from({ length: 20 }, (_, i) => person(i + 1)), total: 21 })
    .mockReturnValueOnce(late.promise);
  render(view());
  await waitFor(() => expect(query).toHaveBeenCalledTimes(1));
  fireEvent.mouseDown(screen.getByLabelText('Synthetic host'));
  await screen.findByText('Candidate 1');
  const popup = document.querySelector('.rc-virtual-list-holder');
  expect(popup).toBeTruthy();
  act(() => { fireEvent.scroll(popup!); fireEvent.scroll(popup!); });
  await waitFor(() => expect(query).toHaveBeenCalledTimes(2));
  expect(query).toHaveBeenLastCalledWith({ keyword: undefined, page: 2, size: 20 });
  await act(async () => { late.resolve({ records: [person(20), person(21)], total: 21 }); });
  // rc-select's accessible options live separately from its virtualized visible rows.
  expect(document.querySelectorAll('.ant-select-item[title="Candidate 20"]')).toHaveLength(1);
  fireEvent.scroll(popup!);
  expect(query).toHaveBeenCalledTimes(2);
});

it('discards a late old identity response and clears options immediately on authority changes', async () => {
  const late = deferred<{ records: BusinessUserOption[]; total: number }>();
  query.mockReturnValueOnce(late.promise);
  const page = render(view());
  await waitFor(() => expect(query).toHaveBeenCalledTimes(1));
  page.rerender(view(true, 'tenant1:user8:version2'));
  await waitFor(() => expect(query).toHaveBeenCalledTimes(2));
  await act(async () => { late.resolve({ records: [person(99)], total: 1 }); });
  fireEvent.mouseDown(screen.getByLabelText('Synthetic host'));
  await screen.findByText('Candidate 9');
  expect(screen.queryByText('Candidate 99')).toBeNull();
  page.rerender(view(false, 'tenant1:user8:version3'));
  expect(screen.queryByText('Candidate 9')).toBeNull();
});

it('retries a failed first load on opening instead of inventing a successful selection', async () => {
  query.mockRejectedValueOnce(new Error('Synthetic candidate failure'));
  render(view());
  await waitFor(() => expect(messages.error).toHaveBeenCalledTimes(1));
  fireEvent.mouseDown(screen.getByLabelText('Synthetic host'));
  await screen.findByText('Candidate 9');
  expect(query).toHaveBeenCalledTimes(2);
});
