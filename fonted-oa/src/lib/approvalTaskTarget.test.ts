import { describe, expect, it } from 'vitest';
import { approvalTaskTarget, todoBusinessLabelKey } from './approvalTaskTarget';

describe('fixed four-business task targets', () => {
  it.each(['UNKNOWN', 'constructor', '__proto__'])('gives unknown type %s a fixed translated fallback', type => {
    expect(todoBusinessLabelKey(type)).toBe('approval.genericTasks.unsupportedType');
  });
  it.each([
    ['LEAVE_APPLICATION', '/oa/approval-tasks/42?from=todo'],
    ['GENERIC_APPROVAL', '/oa/todo?kind=generic&taskId=42'],
    ['VISITOR_BOOKING', '/oa/visitor-booking?id=9'],
    ['SEAL_USAGE', '/oa/seal-usage?id=9'],
  ])('keeps the task ID and business ID distinct for %s', (businessType, path) => {
    expect(approvalTaskTarget({ taskId: 42, businessId: 9, businessType, detailAvailable: true }, 'todo')).toBe(path);
  });
  it.each(['UNKNOWN', '', 'http://untrusted.invalid'])('rejects an unsupported business type %s', businessType => {
    expect(approvalTaskTarget({ taskId: 42, businessId: 9, businessType, detailAvailable: true }, 'todo')).toBeNull();
  });
  it.each([0, -1, Number.MAX_SAFE_INTEGER + 1])('rejects an invalid visitor resource ID %s', businessId => {
    expect(approvalTaskTarget({ taskId: 42, businessId, businessType: 'VISITOR_BOOKING', detailAvailable: true }, 'todo')).toBeNull();
  });
});
