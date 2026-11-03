import { expect, it } from 'vitest';
import dayjs from 'dayjs';
import type { LeaveApplication } from './oaApi';
import { calculateHalfDays, leaveFormPayload, restoreLeaveValues } from './leaveFormValues';

const draft = { leaveType: 'PERSONAL', approverUserId: 9, startDate: '2026-11-03', endDate: '2026-11-04', startPeriod: 'AM', endPeriod: 'PM', reason: ' Actual reason ' } as LeaveApplication;
it('restores canonical dates and preserves dates, periods, actor and version in the write payload', () => {
  const restored = restoreLeaveValues(draft);
  expect(leaveFormPayload(restored, 4)).toEqual({ ...draft, reason: 'Actual reason', version: 4 });
  expect(calculateHalfDays(restored)).toBe(4);
});
it('rejects malformed restored data and returns no duration for incomplete or invalid controls', () => {
  expect(() => restoreLeaveValues({ ...draft, startDate: '2026-02-30' })).toThrow();
  expect(calculateHalfDays({ startDate: dayjs('bad'), endDate: dayjs(), startPeriod: 'AM', endPeriod: 'PM' })).toBe(0);
  expect(calculateHalfDays({ startDate: dayjs(), endDate: dayjs() })).toBe(0);
});
