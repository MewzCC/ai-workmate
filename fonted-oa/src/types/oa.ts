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
  title: string;
  description: string;
}

export interface AiTaskPlanRequest {
  input: string;
  pageId: string;
}

export interface AiTaskPlanResponse {
  taskId: string;
  type: AiTaskType;
  riskLevel: RiskLevel;
  requireConfirm: boolean;
  summary: string;
  steps: AiPlanStep[];
}

export interface AiTaskExecuteRequest {
  taskId: string;
  confirm: boolean;
}

export interface AiTaskExecuteResponse {
  success: boolean;
  auditId: string;
  message: string;
  result: {
    successCount: number;
    pendingConfirmCount: number;
    rejectSuggestCount: number;
  };
}

export interface OaTheme {
  name: string;
  label: string;
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
