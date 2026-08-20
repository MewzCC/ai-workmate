import { request, queryString, type PageResponse } from '@/lib/oaApi';

// ==================== 类型定义 ====================

export type AttendanceClockType = 'CLOCK_IN' | 'CLOCK_OUT';
export type AttendanceStatus =
  | 'NORMAL' | 'LATE' | 'EARLY_LEAVE' | 'LATE_AND_EARLY' | 'MISSING_CLOCK';
export type AttendanceReissueStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';

export interface AttendanceClockRequest {
  clockType: AttendanceClockType;
}

export interface AttendanceClockResponse {
  id: number;
  clockDate: string;
  clockInTime?: string | null;
  clockOutTime?: string | null;
  status: AttendanceStatus;
  lateMinutes: number;
  earlyLeaveMinutes: number;
}

export interface AttendanceTodayStatus {
  id?: number | null;
  clockDate: string;
  clockInTime?: string | null;
  clockOutTime?: string | null;
  status?: AttendanceStatus | null;
  lateMinutes: number;
  earlyLeaveMinutes: number;
  clockInIp?: string | null;
  clockOutIp?: string | null;
  canClockIn: boolean;
  canClockOut: boolean;
}

export interface AttendanceRecord {
  id: number;
  userId: number;
  userName: string;
  clockDate: string;
  clockInTime?: string | null;
  clockOutTime?: string | null;
  status: AttendanceStatus;
  lateMinutes: number;
  earlyLeaveMinutes: number;
}

export interface AttendanceReissuePayload {
  clockDate: string;
  clockType: AttendanceClockType;
  reason: string;
}

export interface AttendanceReissue {
  id: number;
  applicantUserId: number;
  applicantName: string;
  approverUserId?: number | null;
  approverName?: string | null;
  clockDate: string;
  clockType: AttendanceClockType;
  reason: string;
  status: AttendanceReissueStatus;
  approverComment?: string | null;
  submittedAt: string;
  decidedAt?: string | null;
  createdAt: string;
  updatedAt: string;
  canDecide: boolean;
  canWithdraw: boolean;
}

export interface AttendanceReissueDecision {
  decision: 'APPROVED' | 'REJECTED';
  comment?: string;
}

export interface AttendancePersonalStats {
  userId: number;
  userName: string;
  totalDays: number;
  normalDays: number;
  lateDays: number;
  earlyLeaveDays: number;
  missingDays: number;
  pendingReissueCount: number;
}

export interface AttendanceTeamMemberStats {
  userId: number;
  userName: string;
  departmentName?: string | null;
  totalDays: number;
  normalDays: number;
  lateDays: number;
  earlyLeaveDays: number;
  missingDays: number;
}

export interface AttendanceStatistics {
  startDate: string;
  endDate: string;
  personal: AttendancePersonalStats;
  team?: AttendanceTeamMemberStats[] | null;
}

export interface AttendanceSettings {
  tenantId: number;
  workStartTime: string;
  workEndTime: string;
  startFlexMinutes: number;
  endFlexMinutes: number;
  flexLinked: boolean;
  updatedAt?: string | null;
}

export interface AttendanceSettingsPayload {
  workStartTime: string;
  workEndTime: string;
  startFlexMinutes: number;
  endFlexMinutes: number;
  flexLinked: boolean;
}

// ==================== API 封装 ====================

export const attendanceApi = {
  clock: (payload: AttendanceClockRequest) =>
    request<AttendanceClockResponse>('/attendance/clock', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getTodayStatus: () =>
    request<AttendanceTodayStatus>('/attendance/today-status'),

  listRecords: (params: {
    from?: string;
    to?: string;
    userId?: number;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<AttendanceRecord>>(`/attendance/records${queryString(params)}`),

  listExceptions: (params: {
    from?: string;
    to?: string;
    userId?: number;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<AttendanceRecord>>(`/attendance/exceptions${queryString(params)}`),

  submitReissue: (payload: AttendanceReissuePayload) =>
    request<AttendanceReissue>('/attendance/reissue', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listMyReissues: (params: { status?: AttendanceReissueStatus; page?: number; size?: number } = {}) =>
    request<PageResponse<AttendanceReissue>>(`/attendance/reissue/mine${queryString(params)}`),

  listPendingReissues: (params: { page?: number; size?: number } = {}) =>
    request<PageResponse<AttendanceReissue>>(`/attendance/reissue/pending${queryString(params)}`),

  decideReissue: (id: number, payload: AttendanceReissueDecision) =>
    request<AttendanceReissue>(`/attendance/reissue/${id}/decide`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  getStatistics: (params: { year?: number; month?: number } = {}) =>
    request<AttendanceStatistics>(`/attendance/statistics${queryString(params)}`),

  getSettings: () =>
    request<AttendanceSettings>('/attendance/settings'),

  updateSettings: (payload: AttendanceSettingsPayload) =>
    request<AttendanceSettings>('/attendance/settings', {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
};
