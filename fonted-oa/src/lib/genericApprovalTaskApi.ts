import { request, queryString, type PageResponse, type WorkflowTimelineItem } from './oaApi';
import type { ApprovalApplicationAgentField } from './approvalEngineApi';

export interface GenericApprovalTaskItem {
  id: number; applicationId: number; applicantUserId: number; applicantName: string;
  formName: string; title: string; nodeName?: string; nodeIndex?: number; status: string; version: number;
  submittedAt?: string; dueAt?: string; overdue: boolean; hasSnapshot: boolean;
}
export interface GenericApprovalTaskDetail {
  task: GenericApprovalTaskItem; instanceStatus: string; applicationStatus: string;
  fields: ApprovalApplicationAgentField[]; totalFields: number; fieldPage: number;
  canApprove: boolean; canReject: boolean; blockedReason?: string; timeline: WorkflowTimelineItem[];
}
export const genericApprovalTaskApi = {
  mine: (params: { status?: string; from?: string; to?: string; page: number; size: number }) =>
    request<PageResponse<GenericApprovalTaskItem>>(`/generic-approval-tasks${queryString(params)}`),
  detail: (id: number, fieldPage = 1) => request<GenericApprovalTaskDetail>(`/generic-approval-tasks/${id}${queryString({ fieldPage })}`),
  approve: (id: number, version: number, comment?: string) => request<GenericApprovalTaskDetail>(`/generic-approval-tasks/${id}/approve`, { method: 'POST', body: JSON.stringify({ version, comment }) }),
  reject: (id: number, version: number, comment: string) => request<GenericApprovalTaskDetail>(`/generic-approval-tasks/${id}/reject`, { method: 'POST', body: JSON.stringify({ version, comment }) }),
};
