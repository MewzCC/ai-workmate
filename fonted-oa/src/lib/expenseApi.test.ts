import { beforeEach, describe, expect, it, vi } from 'vitest';
import { expenseApi, parseExpenseData } from '@/lib/expenseApi';

const requestMock = vi.fn();
vi.mock('@/lib/oaApi', async () => {
  const actual = await vi.importActual<typeof import('@/lib/oaApi')>('@/lib/oaApi');
  return { ...actual, request: (...args: unknown[]) => requestMock(...args) };
});

describe('expenseApi', () => {
  beforeEach(() => requestMock.mockReset());

  it('scopes the reimbursement list to the dedicated approval form', async () => {
    requestMock.mockResolvedValue({ records: [], total: 0, page: 1, size: 20 });
    await expenseApi.list('PENDING', 2, 10);
    expect(requestMock).toHaveBeenCalledWith(
      '/approval-applications/mine?formKey=expense-application&status=PENDING&page=2&size=10',
    );
  });

  it('treats malformed historical form data as an empty safe object', () => {
    expect(parseExpenseData({ dataJson: '<broken>' } as never)).toEqual({});
  });
});
