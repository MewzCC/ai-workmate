import { request, queryString, type PageResponse } from '@/lib/oaApi';

// ==================== 表单引擎 ====================

export type ApprovalConfigStatus = 'ENABLED' | 'DISABLED';

export interface ApprovalFormPayload {
  formKey: string;
  formName: string;
  description?: string;
  schemaJson: string;
  status: ApprovalConfigStatus;
  version?: number;
}

export interface ApprovalForm {
  id: number;
  formKey: string;
  formName: string;
  description?: string | null;
  schemaJson: string;
  status: ApprovalConfigStatus;
  version: number;
  creatorName?: string | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
}

// ==================== 流程配置 ====================

export type ApprovalNodeType =
  | 'DIRECT_MANAGER' | 'ROLE' | 'DEPARTMENT' | 'USER' | 'SELF' | 'MULTI_LEVEL';

export type ApprovalFlowNodeType = 'START' | 'APPROVAL' | 'CONDITION' | 'CC' | 'DELAY' | 'END';
export type ApprovalMode = 'COUNTERSIGN' | 'OR_SIGN' | 'SEQUENTIAL';
export type ApprovalTimeoutAction = 'REMIND' | 'TRANSFER' | 'AUTO_APPROVE';

/**
 * 流程节点。
 *
 * <p>与后端 `approval_process.node_json` 数组元素格式兼容：
 * 历史节点仅包含 nodeName / approveType / targetKey；
 * 设计器扩展字段（nodeType / mode / timeout*）由前端设计器写入，后端原样存储。
 */
export interface ApprovalProcessNode {
  nodeName: string;
  approveType?: ApprovalNodeType;
  targetKey?: string;
  nodeType?: ApprovalFlowNodeType;
  mode?: ApprovalMode;
  timeoutEnabled?: boolean;
  timeoutHours?: number;
  timeoutAction?: ApprovalTimeoutAction;
}

export interface ApprovalProcessPayload {
  processKey: string;
  processName: string;
  description?: string;
  formId?: number | null;
  nodeJson: string;
  status: ApprovalConfigStatus;
  version?: number;
}

export interface ApprovalProcess {
  id: number;
  processKey: string;
  processName: string;
  description?: string | null;
  formId?: number | null;
  formName?: string | null;
  nodeJson: string;
  status: ApprovalConfigStatus;
  version: number;
  creatorName?: string | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
}

// ==================== 审批规则 ====================

export type ApprovalRuleType =
  | 'AMOUNT_THRESHOLD' | 'LEAVE_TYPE' | 'EMPLOYEE_LEVEL' | 'LIMIT_OVERRIDE';

export interface ApprovalRulePayload {
  ruleKey: string;
  ruleName: string;
  ruleType: ApprovalRuleType;
  priority: number;
  conditionJson: string;
  actionJson: string;
  description?: string;
  status: ApprovalConfigStatus;
  version?: number;
}

export interface ApprovalRule {
  id: number;
  ruleKey: string;
  ruleName: string;
  ruleType: ApprovalRuleType;
  priority: number;
  conditionJson: string;
  actionJson: string;
  description?: string | null;
  status: ApprovalConfigStatus;
  version: number;
  creatorName?: string | null;
  createdAt: string;
  updatedAt: string;
  canEdit: boolean;
  canDelete: boolean;
}

// ==================== 通用审批提交 ====================

export interface ApprovalSubmitPayload {
  formKey: string;
  /** 可选：缺省时后端自动选择该表单绑定的第一个启用流程 */
  processKey?: string;
  formData: Record<string, unknown>;
}

export interface ApprovalDraftPayload extends ApprovalSubmitPayload {}

export interface ApprovalDraftUpdatePayload {
  processKey?: string;
  formData: Record<string, unknown>;
  version: number;
}

export interface ApprovalApplication {
  id: number;
  applicantUserId: number;
  applicantName: string;
  formKey: string;
  formName: string;
  title: string;
  dataJson: string;
  status: 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CANCELLED';
  version: number;
  taskId?: number | null;
  taskVersion?: number | null;
  taskStatus?: string | null;
  taskDueAt?: string | null;
  overdue: boolean;
  taskAssigneeUserId?: number | null;
  taskAssigneeName?: string | null;
  workflowStatus?: string | null;
  canWithdraw: boolean;
  canEditDraft: boolean;
  canCancel: boolean;
  submittedAt?: string | null;
  completedAt?: string | null;
  createdAt: string;
  updatedAt: string;
}

// ==================== API 封装 ====================

export const approvalEngineApi = {
  // ---------- 表单定义 ----------
  listForms: (params: {
    keyword?: string;
    status?: ApprovalConfigStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<ApprovalForm>>(`/approval-config/forms${queryString(params)}`),

  createForm: (payload: ApprovalFormPayload) =>
    request<ApprovalForm>('/approval-config/forms', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateForm: (id: number, payload: ApprovalFormPayload) =>
    request<ApprovalForm>(`/approval-config/forms/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteForm: (id: number) =>
    request<void>(`/approval-config/forms/${id}`, { method: 'DELETE' }),

  // ---------- 流程定义 ----------
  listProcesses: (params: {
    keyword?: string;
    status?: ApprovalConfigStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<ApprovalProcess>>(`/approval-config/processes${queryString(params)}`),

  createProcess: (payload: ApprovalProcessPayload) =>
    request<ApprovalProcess>('/approval-config/processes', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateProcess: (id: number, payload: ApprovalProcessPayload) =>
    request<ApprovalProcess>(`/approval-config/processes/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteProcess: (id: number) =>
    request<void>(`/approval-config/processes/${id}`, { method: 'DELETE' }),

  // ---------- 审批规则 ----------
  listRules: (params: {
    keyword?: string;
    status?: ApprovalConfigStatus;
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<ApprovalRule>>(`/approval-config/rules${queryString(params)}`),

  createRule: (payload: ApprovalRulePayload) =>
    request<ApprovalRule>('/approval-config/rules', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateRule: (id: number, payload: ApprovalRulePayload) =>
    request<ApprovalRule>(`/approval-config/rules/${id}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  deleteRule: (id: number) =>
    request<void>(`/approval-config/rules/${id}`, { method: 'DELETE' }),

  // ---------- 通用审批提交 ----------
  createDraft: (payload: ApprovalDraftPayload) =>
    request<ApprovalApplication>('/approval-applications/drafts', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  updateDraft: (id: number, payload: ApprovalDraftUpdatePayload) =>
    request<ApprovalApplication>(`/approval-applications/${id}/draft`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),

  submitDraft: (id: number, version: number) =>
    request<ApprovalApplication>(`/approval-applications/${id}/submit`, {
      method: 'POST',
      body: JSON.stringify({ version }),
    }),

  cancelDraft: (id: number, version: number) =>
    request<ApprovalApplication>(`/approval-applications/${id}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ version }),
    }),

  submitApplication: (payload: ApprovalSubmitPayload) =>
    request<ApprovalApplication>('/approval-applications', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),

  listMyApplications: (params: {
    status?: ApprovalApplication['status'];
    page?: number;
    size?: number;
  } = {}) =>
    request<PageResponse<ApprovalApplication>>(
      `/approval-applications/mine${queryString(params)}`,
    ),

  getApplication: (id: number) =>
    request<ApprovalApplication>(`/approval-applications/${id}`),
};
