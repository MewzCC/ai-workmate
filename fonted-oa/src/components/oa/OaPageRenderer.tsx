'use client';

import { lazy, type ReactNode } from 'react';
import { Alert, Card, Empty } from 'antd';
import { useTranslation } from 'react-i18next';
import type { ComponentKey, OaMenuItem, OaRole } from '@/types/oa';
import Dashboard from './Dashboard';
import NotificationPage from './NotificationPage';
import TodoListPage from './TodoListPage';
import ApprovalListPage from './ApprovalListPage';
import ApprovalStartPage from './ApprovalStartPage';
import ApprovalFormPage from './ApprovalFormPage';
import LeaveFormPage from './LeaveFormPage';
import MyApplicationsPage from './MyApplicationsPage';
import EmployeeFilePage from './EmployeeFilePage';
import EmployeeChangePage from './EmployeeChangePage';
import AttendanceClockPage from './AttendanceClockPage';
import AttendanceExceptionPage from './AttendanceExceptionPage';
import AttendanceReissuePage from './AttendanceReissuePage';
import AttendanceStatisticsPage from './AttendanceStatisticsPage';
import AttendanceSettingsPage from './AttendanceSettingsPage';
import AssetLedgerPage from './AssetLedgerPage';
import MeetingRoomPage from './MeetingRoomPage';
import VisitorBookingPage from './VisitorBookingPage';
import SealUsagePage from './SealUsagePage';

const AiChatWorkspace = lazy(() => import('@/components/ai-chat/AiChatWorkspace'));
const AccessControlPage = lazy(() => import('./AccessControlPage'));
const AiTaskCenterPage = lazy(() => import('./AiTaskCenterPage'));
const ApprovalRulesPage = lazy(() => import('./ApprovalRulesPage'));
const AuditCenterPage = lazy(() => import('./AuditCenterPage'));
const FormEnginePage = lazy(() => import('./FormEnginePage'));
const KnowledgeBasePage = lazy(() => import('./KnowledgeBasePage'));
const OrganizationTreePage = lazy(() => import('./OrganizationTreePage'));
const ProcessConfigPage = lazy(() => import('./ProcessConfigPage'));
const SystemSettingsPage = lazy(() => import('./SystemSettingsPage'));
const WorkbenchModulePage = lazy(() => import('./WorkbenchModulePage'));
const DictionaryPage = lazy(() => import('./DictionaryPage'));
const TenantConfigPage = lazy(() => import('./TenantConfigPage'));
const DataPermissionPage = lazy(() => import('./DataPermissionPage'));
const AiOperationPermissionPage = lazy(() => import('./AiOperationPermissionPage'));
const SupplierPage = lazy(() => import('./SupplierPage'));
const ContractPage = lazy(() => import('./ContractPage'));
const ExpensePage = lazy(() => import('./ExpensePage'));
const BudgetPage = lazy(() => import('./BudgetPage'));
const ApiCenterPage = lazy(() => import('./ApiCenterPage'));
const PageActionsPage = lazy(() => import('./PageActionsPage'));
const RuntimeLogsPage = lazy(() => import('./RuntimeLogsPage'));
const SandboxReplayPage = lazy(() => import('./SandboxReplayPage'));

export interface OaPageRendererProps {
  menu: OaMenuItem;
  role: OaRole;
  primaryColor: string;
  onOpenAi: (prompt?: string) => void;
}

type PageRenderer = (props: OaPageRendererProps) => ReactNode;

export const OA_PAGE_REGISTRY: Readonly<Record<ComponentKey, PageRenderer>> = {
  DASHBOARD: (props) => <Dashboard primaryColor={props.primaryColor} onOpenAi={props.onOpenAi} />,
  AI_WORKSPACE: ({ role }) => <AiChatWorkspace role={role} />,
  AI_TASK_CENTER: () => <AiTaskCenterPage />,
  MESSAGE_CENTER: () => <NotificationPage />,
  ACCESS_CONTROL: () => <AccessControlPage />,
  TODO_LIST: () => <TodoListPage />,
  APPROVAL_LIST: () => <ApprovalListPage />,
  APPROVAL_START: () => <ApprovalStartPage />,
  APPROVAL_FORM: () => <ApprovalFormPage />,
  FORM_ENGINE: () => <FormEnginePage />,
  PROCESS_CONFIG: () => <ProcessConfigPage />,
  APPROVAL_RULES: () => <ApprovalRulesPage />,
  LEAVE_FORM: () => <LeaveFormPage />,
  MY_APPLICATIONS: () => <MyApplicationsPage />,
  AUDIT_CENTER: () => <AuditCenterPage />,
  ORG_TREE: () => <OrganizationTreePage />,
  EMPLOYEE_FILES: () => <EmployeeFilePage />,
  EMPLOYEE_CHANGE: () => <EmployeeChangePage />,
  KNOWLEDGE_BASE: () => <KnowledgeBasePage />,
  SYSTEM_CONFIG: () => <SystemSettingsPage />,
  DICTIONARY: () => <DictionaryPage />,
  TENANT_CONFIG: () => <TenantConfigPage />,
  DATA_PERMISSION: () => <DataPermissionPage />,
  AI_PERMISSION: () => <AiOperationPermissionPage />,
  SUPPLIER: () => <SupplierPage />,
  CONTRACT: () => <ContractPage />,
  EXPENSE: () => <ExpensePage />,
  BUDGET: () => <BudgetPage />,
  API_CENTER: () => <ApiCenterPage />,
  PAGE_ACTIONS: () => <PageActionsPage />,
  RUNTIME_LOGS: () => <RuntimeLogsPage />,
  SANDBOX_REPLAY: () => <SandboxReplayPage />,
  WORKBENCH_MODULE: ({ menu }) => <WorkbenchModulePage moduleKey={menu.id} title={menu.name} />,
  ATTENDANCE_CLOCK: () => <AttendanceClockPage />,
  ATTENDANCE_EXCEPTION: () => <AttendanceExceptionPage />,
  ATTENDANCE_REISSUE: () => <AttendanceReissuePage />,
  ATTENDANCE_STATISTICS: () => <AttendanceStatisticsPage />,
  ATTENDANCE_SETTINGS: () => <AttendanceSettingsPage />,
  ASSET_LEDGER: () => <AssetLedgerPage />,
  MEETING_ROOM: () => <MeetingRoomPage />,
  VISITOR_BOOKING: () => <VisitorBookingPage />,
  SEAL_USAGE: () => <SealUsagePage />,
};

export function OaPageRenderer(props: OaPageRendererProps) {
  const { t } = useTranslation();
  const renderer = props.menu.componentKey ? OA_PAGE_REGISTRY[props.menu.componentKey] : undefined;
  if (renderer) return renderer(props);

  return (
    <Card className="oa-card oa-placeholder-card">
      <Alert type="error" showIcon title={t('oa.errors.unsupportedComponent')} />
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('oa.errors.unsupportedComponentDescription', { componentKey: props.menu.componentKey || '-' })} />
    </Card>
  );
}
