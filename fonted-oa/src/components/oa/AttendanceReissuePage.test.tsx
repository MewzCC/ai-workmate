import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AttendanceReissuePage from './AttendanceReissuePage';

const permission = vi.hoisted(() => ({ allowed: false }));
const listMyReissues = vi.hoisted(() => vi.fn());

vi.mock('@/hooks/usePermission', () => ({
  usePermission: () => ({ allowed: permission.allowed }),
}));

vi.mock('@/lib/attendanceApi', () => ({
  attendanceApi: {
    listMyReissues,
    listPendingReissues: vi.fn(),
    submitReissue: vi.fn(),
    decideReissue: vi.fn(),
  },
}));

describe('AttendanceReissuePage realtime write permission', () => {
  beforeEach(() => {
    permission.allowed = false;
    listMyReissues.mockReset().mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });
  });

  afterEach(() => cleanup());

  it('does not expose the create entry without the dedicated business permission', async () => {
    render(<AttendanceReissuePage />);
    await waitFor(() => expect(listMyReissues).toHaveBeenCalled());
    expect(screen.queryByRole('button', { name: '新建补卡申请' })).toBeNull();
  });

  it('opens the Ant Design form when the permission snapshot allows it', async () => {
    permission.allowed = true;
    const view = render(<AttendanceReissuePage />);
    const create = await screen.findByRole('button', { name: '新建补卡申请' });
    fireEvent.click(create);
    expect(await screen.findByRole('dialog')).not.toBeNull();
    permission.allowed = false;
    view.rerender(<AttendanceReissuePage />);
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });
});
