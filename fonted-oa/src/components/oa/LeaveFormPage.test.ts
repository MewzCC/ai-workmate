import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import { calculateHalfDays } from './LeaveFormPage';

describe('calculateHalfDays', () => {
  it('counts inclusive calendar half-day slots', () => {
    const date = dayjs('2026-07-27');
    expect(calculateHalfDays({
      startDate: date,
      startPeriod: 'AM',
      endDate: date,
      endPeriod: 'AM',
    })).toBe(1);
    expect(calculateHalfDays({
      startDate: date,
      startPeriod: 'AM',
      endDate: date,
      endPeriod: 'PM',
    })).toBe(2);
  });

  it('includes weekend calendar days', () => {
    expect(calculateHalfDays({
      startDate: dayjs('2026-07-31'),
      startPeriod: 'AM',
      endDate: dayjs('2026-08-03'),
      endPeriod: 'PM',
    })).toBe(8);
  });

  it('returns a non-positive value for a reverse range so the form can block submission', () => {
    const date = dayjs('2026-07-27');
    expect(calculateHalfDays({
      startDate: date,
      startPeriod: 'PM',
      endDate: date,
      endPeriod: 'AM',
    })).toBe(0);
  });
});
