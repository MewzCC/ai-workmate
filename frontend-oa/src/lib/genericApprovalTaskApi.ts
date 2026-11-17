import { request, queryString, type PageResponse, type WorkflowTimelineItem } from './oaApi';
import type { ApprovalApplicationAgentField } from './approvalEngineApi';
import type { BusinessUserOption } from './adminAssetsApi';

export interface GenericApprovalTaskItem {
  id: number; applicationId: number; applicantUserId: number; applicantName: string;
  formName: string; title: string; nodeName?: string; nodeIndex?: number; status: string; version: number;
  submittedAt?: string; dueAt?: string; overdue: boolean; hasSnapshot: boolean;
  parentTaskId?: number | null; addSignMode?: 'PRE' | 'POST' | null;
}
export interface GenericApprovalTaskDetail {
  task: GenericApprovalTaskItem; instanceStatus: string; applicationStatus: string;
  fields: ApprovalApplicationAgentField[]; totalFields: number; fieldPage: number;
  canApprove: boolean; canReject: boolean; blockedReason?: string; timeline: WorkflowTimelineItem[];
  canTransfer?: boolean; canCopy?: boolean;
  canAddSign?: boolean; addSignTasks?: GenericAddSignItem[];
}
export interface GenericAddSignItem { taskId: number; parentTaskId: number; mode: 'PRE' | 'POST'; assigneeUserId: number; assigneeName: string; status: string; version: number; dueAt?: string | null }
export interface GenericAddSignCommand { targetUserId: number; version: number; mode: 'PRE' | 'POST'; reason: string }
export interface GenericAddSignReceipt { applicationId: number; taskId: number; status: 'PENDING' | 'WAITING'; version: number; applicationStatus: 'PENDING';
  action: 'ADD_SIGN'; mode: 'PRE' | 'POST'; targetUserId: number; addedTaskId: number; addedTaskStatus: 'PENDING' | 'WAITING'; addedTaskVersion: number; activeTaskId: number }
export interface GenericParticipantCommand { targetUserId: number; version: number; reason: string }
export interface GenericParticipantReceipt { applicationId: number; taskId: number; status: 'PENDING'; version: number;
  applicationStatus: 'PENDING'; action: 'TRANSFER' | 'CC'; originalAssigneeUserId: number; targetUserId: number; assigneeUserId: number }
export const genericApprovalTaskApi = {
  participants: (id: number, purpose: 'TRANSFER' | 'CC' | 'ADD_SIGN', params: { keyword?: string; page: number; size: number }) =>
    request<PageResponse<BusinessUserOption & { canApprove: boolean }>>(`/generic-approval-tasks/${id}/participants${queryString({ purpose, ...params })}`),
  transfer: (id: number, command: GenericParticipantCommand) => request<GenericParticipantReceipt>(`/generic-approval-tasks/${id}/transfer`, { method: 'POST', body: JSON.stringify(command) }),
  copyTo: (id: number, command: GenericParticipantCommand) => request<GenericParticipantReceipt>(`/generic-approval-tasks/${id}/cc`, { method: 'POST', body: JSON.stringify(command) }),
  addSign: (id: number, command: GenericAddSignCommand) => request<GenericAddSignReceipt>(`/generic-approval-tasks/${id}/add-sign`, { method: 'POST', body: JSON.stringify(command) }),
  mine: (params: { status?: string; from?: string; to?: string; page: number; size: number }) =>
    request<PageResponse<GenericApprovalTaskItem>>(`/generic-approval-tasks${queryString(params)}`),
  detail: (id: number, fieldPage = 1) => request<GenericApprovalTaskDetail>(`/generic-approval-tasks/${id}${queryString({ fieldPage })}`),
  approve: (id: number, version: number, comment?: string) => request<GenericApprovalTaskDetail>(`/generic-approval-tasks/${id}/approve`, { method: 'POST', body: JSON.stringify({ version, comment }) }),
  reject: (id: number, version: number, comment: string) => request<GenericApprovalTaskDetail>(`/generic-approval-tasks/${id}/reject`, { method: 'POST', body: JSON.stringify({ version, comment }) }),
};
