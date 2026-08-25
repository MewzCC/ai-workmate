export type OaRole =
  | 'super_admin'
  | 'system_admin'
  | 'process_admin'
  | 'finance_admin'
  | 'employee';

export type PermissionAction =
  | 'visible'
  | 'access'
  | 'create'
  | 'read'
  | 'update'
  | 'delete'
  | 'approve'
  | 'export'
  | 'import'
  | 'config'
  | 'assign'
  | 'audit'
  | 'ai_execute';

export type AiTaskType = 'read' | 'create' | 'update' | 'delete' | 'approve' | 'export' | 'debug' | 'general';

export type RiskLevel = 'low' | 'medium' | 'high' | 'critical';

export interface OaMenuItem {
  id: string;
  parentId?: string;
  name: string;
  type: 'group' | 'menu' | 'page';
  icon?: string;
  path?: string;
  componentKey?: 'DASHBOARD' | 'AI_WORKSPACE' | 'ACCESS_CONTROL'
    | 'AI_TASK_CENTER'
    | 'TODO_LIST' | 'LEAVE_FORM' | 'MY_APPLICATIONS' | 'AUDIT_CENTER'
    | 'APPROVAL_LIST' | 'APPROVAL_START' | 'APPROVAL_FORM' | 'FORM_ENGINE' | 'PROCESS_CONFIG' | 'APPROVAL_RULES'
    | 'ORG_TREE' | 'KNOWLEDGE_BASE' | 'MESSAGE_CENTER' | 'SYSTEM_CONFIG'
    | 'ATTENDANCE_CLOCK' | 'ATTENDANCE_EXCEPTION' | 'ATTENDANCE_REISSUE'
    | 'ATTENDANCE_STATISTICS' | 'ATTENDANCE_SETTINGS'
    | 'EMPLOYEE_FILES'
    | 'ASSET_LEDGER' | 'MEETING_ROOM' | 'VISITOR_BOOKING' | 'SEAL_USAGE';
  permissionCode?: string;
  sort: number;
  visible: boolean;
  roles?: OaRole[];
  actions?: PermissionAction[];
  children?: OaMenuItem[];
}

export interface AIAction {
  actionId: string;
  name: string;
  pageId: string;
  type: AiTaskType;
  riskLevel: RiskLevel;
  requiredPermission: PermissionAction;
  requireConfirm: boolean;
  executeApi: string;
}

export interface ApprovalRecord {
  id: string;
  name: string;
  applicant: string;
  department: string;
  node: string;
  status: 'warning' | 'processing' | 'success' | 'error' | 'default';
}

export interface AiPlanStep {
  sequence: number;
  toolCode: string;
  title: string;
  arguments: Record<string, unknown>;
}

export interface AiTaskPlanRequest {
  input: string;
  pageId: string;
  pageContext?: Record<string, unknown>;
}

export type AgentRiskLevel = 'L0' | 'L1' | 'L2';

export type AgentTaskStatus =
  | 'RECEIVED' | 'PLANNING' | 'PLAN_READY' | 'WAITING_CONFIRMATION'
  | 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIALLY_SUCCEEDED'
  | 'FAILED' | 'TIMED_OUT' | 'REJECTED' | 'EXPIRED' | 'CANCELLED';

export interface AiTaskPlanResponse {
  taskId: string;
  status: AgentTaskStatus;
  planVersion: number;
  planHash: string;
  riskLevel: AgentRiskLevel;
  confirmationRequired: boolean;
  expiresAt: string | null;
  summary: string;
  steps: AiPlanStep[];
}

export interface AiTaskExecuteRequest {
  planVersion: number;
  planHash: string;
  confirmationToken?: string;
}

export interface AiTaskExecuteResponse {
  taskId: string;
  status: AgentTaskStatus;
  statusUrl: string;
  eventsUrl: string;
}

export interface AiTaskConfirmationResponse {
  token: string;
  expiresAt: string;
}

export interface AiTaskEvent {
  id: string;
  type: string;
  data: Record<string, unknown>;
}

export interface AgentTaskSummary {
  taskId: string;
  pageId: string;
  status: AgentTaskStatus;
  riskLevel: AgentRiskLevel | null;
  planVersion: number | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  errorCode: string | null;
}

export interface AgentTaskDetailStep {
  sequence: number;
  toolCode: string;
  riskLevel: AgentRiskLevel;
  status: string;
  arguments: Record<string, unknown>;
  result: unknown;
  resultSummary: string | null;
  errorCode: string | null;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface AgentTaskDetail {
  taskId: string;
  pageId: string;
  input: string;
  pageContext: unknown;
  plan: unknown;
  planHash: string | null;
  planVersion: number | null;
  riskLevel: AgentRiskLevel | null;
  status: AgentTaskStatus;
  steps: AgentTaskDetailStep[];
  timeoutAt: string | null;
  createdAt: string;
  updatedAt: string;
  finishedAt: string | null;
  errorCode: string | null;
}

export interface OaTheme {
  name: string;
  primary: string;
  sidebar: string;
  siderText: string;
  surface: string;
  card: string;
  text: string;
  muted: string;
  border: string;
  header: string;
  dark?: boolean;
}
