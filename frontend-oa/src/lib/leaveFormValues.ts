import dayjs, { type Dayjs } from 'dayjs';
import type { HalfDayPeriod, LeaveApplication, LeaveApplicationPayload, LeaveType } from './oaApi';

export interface LeaveFormValues {
  leaveType: LeaveType; approverUserId: number; startDate: Dayjs; startPeriod: HalfDayPeriod;
  endDate: Dayjs; endPeriod: HalfDayPeriod; reason: string;
}
export const LEAVE_TYPE_KEYS: LeaveType[] = ['ANNUAL', 'PERSONAL', 'SICK', 'MARRIAGE', 'MATERNITY', 'PATERNITY', 'BEREAVEMENT', 'COMPENSATORY', 'OTHER'];
export const PERIOD_KEYS: HalfDayPeriod[] = ['AM', 'PM'];
export function calculateHalfDays(values?: Partial<LeaveFormValues>): number {
  if (!dayjs.isDayjs(values?.startDate) || !dayjs.isDayjs(values?.endDate) || !values.startDate.isValid() || !values.endDate.isValid()
      || !PERIOD_KEYS.includes(values.startPeriod as HalfDayPeriod) || !PERIOD_KEYS.includes(values.endPeriod as HalfDayPeriod)) return 0;
  const days = values.endDate.startOf('day').diff(values.startDate.startOf('day'), 'day');
  return days * 2 + (values.endPeriod === 'PM' ? 1 : 0) - (values.startPeriod === 'PM' ? 1 : 0) + 1;
}
export function isDateBeforeToday(date: Dayjs, today: Dayjs = dayjs()) { return date.startOf('day').isBefore(today.startOf('day')); }
export function initialLeaveValues(approverUserId?: number): Partial<LeaveFormValues> {
  return { leaveType: 'ANNUAL', startDate: dayjs().startOf('day'), startPeriod: 'AM', endDate: dayjs().startOf('day'), endPeriod: 'PM', approverUserId };
}
export function restoreLeaveValues(item: LeaveApplication): LeaveFormValues {
  const start = dayjs(item.startDate), end = dayjs(item.endDate);
  if (!start.isValid() || start.format('YYYY-MM-DD') !== item.startDate || !end.isValid() || end.format('YYYY-MM-DD') !== item.endDate
      || !LEAVE_TYPE_KEYS.includes(item.leaveType) || !PERIOD_KEYS.includes(item.startPeriod) || !PERIOD_KEYS.includes(item.endPeriod))
    throw new Error('PAGE_UI_FIELD_REJECTED');
  return { leaveType: item.leaveType, approverUserId: item.approverUserId!, startDate: start, startPeriod: item.startPeriod,
    endDate: end, endPeriod: item.endPeriod, reason: item.reason };
}
export function leaveFormPayload(values: LeaveFormValues, version?: number): LeaveApplicationPayload {
  return { leaveType: values.leaveType, approverUserId: values.approverUserId, startDate: values.startDate.format('YYYY-MM-DD'), startPeriod: values.startPeriod,
    endDate: values.endDate.format('YYYY-MM-DD'), endPeriod: values.endPeriod, reason: values.reason.trim(), version };
}
