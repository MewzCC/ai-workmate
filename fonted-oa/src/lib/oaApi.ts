import type {
  AiTaskExecuteRequest,
  AiTaskExecuteResponse,
  AiTaskPlanRequest,
  AiTaskPlanResponse,
} from '@/types/oa';
import { buildApiHeaders } from '@/lib/apiHeaders';
import i18n from '@/i18n';

const BASE = '/api';

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
  requestId?: string;
  traceId?: string;
}

export class OaApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errorCode: string,
    readonly requestId?: string,
    readonly traceId?: string,
  ) {
    super(message);
    this.name = 'OaApiError';
  }

  get retryable(): boolean {
    return this.status === 429 || this.status >= 500;
  }
}

async function parseResult<T>(res: Response): Promise<T> {
  const json = await res.json().catch(() => null) as ApiResult<T> | null;
  if (!res.ok || !json || json.code !== 200 || json.data === null) {
    const status = res.status || 500;
    const error = new OaApiError(
      json?.message || statusMessage(status),
      status,
      json?.errorCode || statusErrorCode(status),
      json?.requestId,
      json?.traceId,
    );
    if (status === 401 && typeof window !== 'undefined') window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    throw error;
  }
  return json.data;
}

function statusErrorCode(status: number): string {
  if (status === 401) return 'AUTH_REQUIRED';
  if (status === 403) return 'PERMISSION_DENIED';
  if (status === 409) return 'VERSION_CONFLICT';
  if (status === 429) return 'RATE_LIMITED';
  return 'SYSTEM_ERROR';
}

function statusMessage(status: number): string {
  if (status === 401) return i18n.t('errors.oa.statusUnauthorized');
  if (status === 403) return i18n.t('errors.oa.statusForbidden');
  if (status === 409) return i18n.t('errors.oa.statusConflict');
  if (status === 429) return i18n.t('errors.oa.statusTooManyRequests');
  return i18n.t('errors.oa.statusServer');
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      credentials: 'include',
      cache: 'no-store',
      ...init,
      headers: buildApiHeaders(Boolean(init?.body), init?.headers),
    });
  } catch {
    throw new OaApiError(i18n.t('errors.oa.serviceUnavailable'), 0, 'SERVICE_UNAVAILABLE');
  }
  return parseResult<T>(res);
}

export function formatOaApiError(error: unknown): string {
  if (!(error instanceof OaApiError)) return i18n.t('errors.oa.requestFailedRetry');
  const trace = error.traceId ? i18n.t('errors.oa.traceIdSuffix', { traceId: error.traceId }) : '';
  return `${error.message}${trace}`;
}

export async function getSystemHealth(): Promise<{ status: string; service: string }> {
  const res = await fetch(`${BASE}/system/health`);
  return parseResult(res);
}

/**
 * 服务器时间接口：返回 epoch 毫秒与 ISO 字符串。
 * 前端可用 epochMillis 与 Date.now() 计算偏移，让按钮显示与后端落库一致的服务器时间。
 */
export async function getServerTime(): Promise<{ epochMillis: number; iso: string }> {
  const res = await fetch(`${BASE}/system/time`);
  return parseResult(res);
}

export async function planAiTask(request: AiTaskPlanRequest): Promise<AiTaskPlanResponse> {
  const res = await fetch(`${BASE}/ai/tasks/plan`, {
    method: 'POST',
    headers: buildApiHeaders(),
    body: JSON.stringify(request),
  });
  return parseResult(res);
}

export async function executeAiTask(request: AiTaskExecuteRequest): Promise<AiTaskExecuteResponse> {
  const res = await fetch(`${BASE}/ai/tasks/execute`, {
    method: 'POST',
    headers: buildApiHeaders(),
    body: JSON.stringify(request),
  });
  return parseResult(res);
}

export type LeaveType =
  | 'ANNUAL' | 'PERSONAL' | 'SICK' | 'MARRIAGE' | 'MATERNITY'
  | 'PATERNITY' | 'BEREAVEMENT' | 'COMPENSATORY' | 'OTHER';
export type LeaveStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
export type HalfDayPeriod = 'AM' | 'PM';

export interface PageResponse<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface LeaveApplicationPayload {
  leaveType: LeaveType;
  approverUserId: number;
  startDate: string;
  startPeriod: HalfDayPeriod;
  endDate: string;
  endPeriod: HalfDayPeriod;
  reason: string;
  version?: number;
}

export interface ApproverCandidate {
  id: number;
  name: string;
  departmentName?: string;
  positionName?: string;
  recommended: boolean;
  avatarUrl?: string | null;
}

export interface LeaveApplication {
  id: number;
  applicantUserId: number;
  applicantName: string;
  applicantAvatarUrl?: string | null;
  approverUserId?: number;
  approverName?: string;
  approverAvatarUrl?: string | null;
  leaveType: LeaveType;
  startDate: string;
  startPeriod: HalfDayPeriod;
  endDate: string;
  endPeriod: HalfDayPeriod;
  durationHalfDays: number;
  durationDays: number;
  reason: string;
  status: LeaveStatus;
  version: number;
  taskId?: number;
  taskVersion?: number;
  taskStatus?: string;
  taskDueAt?: string;
  overdue: boolean;
  workflowStatus?: string;
  currentStage: 'APPLICATION' | 'APPROVAL' | 'COMPLETED';
  workflowStages: WorkflowStage[];
  submittedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canSubmit: boolean;
  canWithdraw: boolean;
  canApprove: boolean;
}

export interface WorkflowStage {
  key: 'APPLICATION' | 'APPROVAL' | 'COMPLETED';
  title: string;
  status: 'WAIT' | 'PROCESS' | 'FINISH' | 'ERROR';
  actorName?: string;
  occurredAt?: string;
  description: string;
}

export interface LeaveApprovalContext {
  applicantName: string;
  departmentName?: string;
  positionName?: string;
  approverUserId?: number;
  approverName?: string;
  approverSource: 'DIRECT_OR_DEPARTMENT_DEFAULT' | 'UNCONFIGURED';
  approverConfigured: boolean;
  approvalDueHours: number;
  applicantAvatarUrl?: string | null;
}

export interface TodoItem {
  id: number;
  applicationId: number;
  applicantUserId: number;
  applicantName: string;
  applicantAvatarUrl?: string | null;
  leaveType: LeaveType;
  durationHalfDays: number;
  status: string;
  version: number;
  submittedAt: string;
  dueAt?: string;
  overdue: boolean;
}

export interface WorkflowTimelineItem {
  id: number;
  actorUserId: number;
  actorName: string;
  actorAvatarUrl?: string | null;
  action: string;
  fromStatus?: string;
  toStatus: string;
  comment?: string;
  createdAt: string;
}

export interface AuditRecord {
  id: number;
  actorUserId: number;
  actorName: string;
  resourceType: string;
  resourceId: string;
  action: string;
  result: 'SUCCESS' | 'DENIED' | 'CONFLICT' | 'FAILURE';
  summary?: string;
  traceId: string;
  createdAt: string;
}

export function queryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value));
  });
  const encoded = search.toString();
  return encoded ? `?${encoded}` : '';
}

export const leaveApi = {
  approvalContext: () => request<LeaveApprovalContext>('/leave-applications/approval-context'),
  approverCandidates: (keyword?: string) =>
    request<PageResponse<ApproverCandidate>>(
      `/leave-applications/approver-candidates${queryString({ keyword, page: 1, size: 100 })}`,
    ),
  create: (payload: LeaveApplicationPayload) =>
    request<LeaveApplication>('/leave-applications', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  update: (id: number, payload: LeaveApplicationPayload) =>
    request<LeaveApplication>(`/leave-applications/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  detail: (id: number) => request<LeaveApplication>(`/leave-applications/${id}`),
  mine: (params: { status?: LeaveStatus; page?: number; size?: number } = {}) =>
    request<PageResponse<LeaveApplication>>(`/leave-applications/mine${queryString(params)}`),
  submit: (id: number, version: number) =>
    request<LeaveApplication>(`/leave-applications/${id}/submit`, {
      method: 'POST',
      body: JSON.stringify({ version }),
    }),
  withdraw: (id: number, version: number) =>
    request<LeaveApplication>(`/leave-applications/${id}/withdraw`, {
      method: 'POST',
      body: JSON.stringify({ version }),
    }),
};

export const todoApi = {
  list: (params: { status?: string; from?: string; to?: string; page?: number; size?: number } = {}) =>
    request<PageResponse<TodoItem>>(`/todos${queryString(params)}`),
  detail: (id: number) => request<LeaveApplication>(`/todos/${id}`),
  approve: (id: number, version: number, comment?: string) =>
    request<LeaveApplication>(`/approval-tasks/${id}/approve`, {
      method: 'POST',
      body: JSON.stringify({ version, comment }),
    }),
  reject: (id: number, version: number, comment: string) =>
    request<LeaveApplication>(`/approval-tasks/${id}/reject`, {
      method: 'POST',
      body: JSON.stringify({ version, comment }),
    }),
  timeline: (id: number) =>
    request<WorkflowTimelineItem[]>(`/approval-tasks/${id}/timeline`),
};

export const auditApi = {
  list: (params: {
    actorUserId?: number;
    action?: string;
    resourceType?: string;
    result?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
  } = {}) => request<PageResponse<AuditRecord>>(`/audit-records${queryString(params)}`),
};
