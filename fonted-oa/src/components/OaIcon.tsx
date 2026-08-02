import type { ComponentType, CSSProperties } from 'react';
import type { AntdIconProps } from '@ant-design/icons/lib/components/AntdIcon';
import {
  AccountBookOutlined,
  ApartmentOutlined,
  ApiOutlined,
  AuditOutlined,
  BankOutlined,
  BellOutlined,
  BookOutlined,
  BugOutlined,
  CalendarOutlined,
  CheckSquareOutlined,
  ClusterOutlined,
  ControlOutlined,
  CopyOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeleteOutlined,
  EditOutlined,
  FileProtectOutlined,
  FileSearchOutlined,
  FormOutlined,
  FundOutlined,
  HistoryOutlined,
  IdcardOutlined,
  InboxOutlined,
  LinkOutlined,
  LockOutlined,
  LogoutOutlined,
  LeftOutlined,
  MoreOutlined,
  NodeIndexOutlined,
  PaperClipOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  SearchOutlined,
  SendOutlined,
  SettingOutlined,
  ShopOutlined,
  SkinOutlined,
  SwapOutlined,
  TeamOutlined,
  UploadOutlined,
  WalletOutlined,
} from '@ant-design/icons';
import { IconFont, type IconFontName } from '@/components/IconFont';

const iconFontMap = {
  brand: 'main_icon',
  ai: 'zhinengyouhua',
  csv: 'CSV',
  pdf: 'PDF',
  png: 'PNG',
  jpg: 'JPG',
  txt: 'TXT',
  user: 'yonghuguanli',
  role: 'jiaoseguanli',
  add: 'add_oa',
  pause: 'pause',
  option: 'xuanxiangka',
  export: 'daochu',
  code: 'daimayunhang',
  preview: 'jieguoyulan',
  avatar: 'siyouguize',
  phone: 'shouji',
  email: 'youxiang',
  dashboard: 'dashboard',
  business: 'business',
  platform: 'platform',
  settings: 'settings',
  approval: 'approval',
  hr: 'hr',
  assets: 'assets',
  finance: 'finance',
  integration: 'integration',
  todo: 'todo',
  'leave-application': 'form',
  'my-applications': 'history',
  messages: 'messages',
  form: 'form',
  process: 'process',
  rules: 'rules',
  organization: 'organization',
  'employee-files': 'employee-files',
  attendance: 'attendance',
  'employee-change': 'employee-change',
  'asset-ledger': 'asset-ledger',
  'meeting-room': 'meeting-room',
  visitor: 'visitor',
  seal: 'seal',
  expense: 'expense',
  budget: 'budget',
  contracts: 'contracts',
  suppliers: 'suppliers',
  'api-center': 'api-center',
  'page-actions': 'page-actions',
  'runtime-logs': 'runtime-logs',
  sandbox: 'sandbox',
  'access-control': 'access-control',
  'data-permission': 'data-permission',
  audit: 'audit',
  tenant: 'tenant',
  dictionary: 'dictionary',
  help: 'help',
  notification: 'notification',
  appearance: 'appearance',
  logout: 'logout',
  edit: 'edit',
  delete: 'delete',
  upload: 'upload',
  save: 'save',
  search: 'search',
  attachment: 'attachment',
  send: 'send',
  more: 'more',
  copy: 'copy',
  reload: 'reload',
  lock: 'lock',
  history: 'history',
} satisfies Record<string, IconFontName>;

const fallbackIconMap = {
  dashboard: DashboardOutlined,
  business: ApartmentOutlined,
  platform: ApiOutlined,
  settings: SettingOutlined,
  approval: AuditOutlined,
  hr: TeamOutlined,
  assets: InboxOutlined,
  finance: WalletOutlined,
  integration: LinkOutlined,
  todo: CheckSquareOutlined,
  messages: BellOutlined,
  form: FormOutlined,
  process: NodeIndexOutlined,
  rules: SafetyCertificateOutlined,
  organization: ClusterOutlined,
  'employee-files': IdcardOutlined,
  attendance: CalendarOutlined,
  'employee-change': SwapOutlined,
  'asset-ledger': DatabaseOutlined,
  'meeting-room': BankOutlined,
  visitor: TeamOutlined,
  seal: FileProtectOutlined,
  expense: AccountBookOutlined,
  budget: FundOutlined,
  contracts: FileProtectOutlined,
  suppliers: ShopOutlined,
  'api-center': ApiOutlined,
  'page-actions': ControlOutlined,
  'runtime-logs': FileSearchOutlined,
  sandbox: BugOutlined,
  'access-control': SafetyCertificateOutlined,
  'data-permission': DatabaseOutlined,
  audit: AuditOutlined,
  tenant: BankOutlined,
  dictionary: BookOutlined,
  help: QuestionCircleOutlined,
  notification: BellOutlined,
  appearance: SkinOutlined,
  logout: LogoutOutlined,
  edit: EditOutlined,
  delete: DeleteOutlined,
  upload: UploadOutlined,
  save: SaveOutlined,
  search: SearchOutlined,
  attachment: PaperClipOutlined,
  send: SendOutlined,
  more: MoreOutlined,
  copy: CopyOutlined,
  reload: ReloadOutlined,
  lock: LockOutlined,
  history: HistoryOutlined,
  'knowledge-base': DatabaseOutlined,
  previous: LeftOutlined,
  next: RightOutlined,
} satisfies Record<string, ComponentType<AntdIconProps>>;

export type OaIconName = keyof typeof iconFontMap | keyof typeof fallbackIconMap;

export const oaMenuIconOptions: Array<{ value: OaIconName; label: string }> = [
  { value: 'dashboard', label: '驾驶舱' },
  { value: 'ai', label: 'AI 智能' },
  { value: 'business', label: '业务系统' },
  { value: 'platform', label: '平台能力' },
  { value: 'settings', label: '系统设置' },
  { value: 'approval', label: '流程审批' },
  { value: 'hr', label: '组织人事' },
  { value: 'assets', label: '行政资产' },
  { value: 'finance', label: '财务合同' },
  { value: 'integration', label: '开放联调' },
  { value: 'todo', label: '待办事项' },
  { value: 'messages', label: '消息中心' },
  { value: 'form', label: '表单' },
  { value: 'process', label: '流程配置' },
  { value: 'rules', label: '规则' },
  { value: 'organization', label: '组织结构' },
  { value: 'user', label: '用户管理' },
  { value: 'role', label: '角色管理' },
  { value: 'access-control', label: '访问控制' },
  { value: 'data-permission', label: '数据权限' },
  { value: 'audit', label: '审计' },
  { value: 'tenant', label: '租户' },
  { value: 'dictionary', label: '数据字典' },
];

const configuredIconAliases: Record<string, OaIconName> = {
  DashboardOutlined: 'dashboard',
  ApartmentOutlined: 'business',
  ApiOutlined: 'platform',
  SettingOutlined: 'settings',
  RobotOutlined: 'ai',
};

const routeIconMap: Record<string, OaIconName> = {
  workspace: 'dashboard',
  dashboard: 'dashboard',
  'ai-workspace': 'ai',
  todo: 'todo',
  'leave-application': 'form',
  'my-applications': 'history',
  messages: 'messages',
  business: 'business',
  approval: 'approval',
  'approval-list': 'approval',
  'form-engine': 'form',
  'process-config': 'process',
  'approval-rules': 'rules',
  hr: 'hr',
  'org-tree': 'organization',
  'employee-files': 'employee-files',
  attendance: 'attendance',
  'employee-change': 'employee-change',
  assets: 'assets',
  'asset-ledger': 'asset-ledger',
  'meeting-room': 'meeting-room',
  'visitor-booking': 'visitor',
  'seal-usage': 'seal',
  finance: 'finance',
  expense: 'expense',
  budget: 'budget',
  contracts: 'contracts',
  suppliers: 'suppliers',
  platform: 'platform',
  integration: 'integration',
  'api-center': 'api-center',
  'page-actions': 'page-actions',
  'runtime-logs': 'runtime-logs',
  'sandbox-replay': 'sandbox',
  settings: 'settings',
  'access-control': 'role',
  'data-permission': 'data-permission',
  'ai-permission': 'ai',
  'audit-center': 'audit',
  'tenant-config': 'tenant',
  dictionary: 'dictionary',
  'knowledge-base': 'knowledge-base',
};

export interface OaIconProps {
  name: OaIconName;
  size?: CSSProperties['width'];
  className?: string;
  style?: CSSProperties;
  title?: string;
}

export function OaIcon({
  name,
  size = '1em',
  className,
  style,
  title,
}: OaIconProps) {
  const iconFontName = iconFontMap[name as keyof typeof iconFontMap];
  if (iconFontName) {
    return (
      <IconFont
        name={iconFontName}
        size={size}
        className={className}
        style={style}
        title={title}
      />
    );
  }

  const FallbackIcon = fallbackIconMap[name as keyof typeof fallbackIconMap];
  if (!FallbackIcon) return null;

  return (
    <FallbackIcon
      className={className}
      aria-hidden={title ? undefined : true}
      aria-label={title}
      role={title ? 'img' : undefined}
      style={{
        fontSize: size,
        ...style,
      }}
    />
  );
}

export function resolveOaMenuIcon(
  routeId: string,
  configuredIcon?: string,
): OaIconName | undefined {
  const configuredName = configuredIcon
    ? configuredIconAliases[configuredIcon]
      || (
        configuredIcon in iconFontMap || configuredIcon in fallbackIconMap
          ? configuredIcon as OaIconName
          : undefined
      )
    : undefined;
  return configuredName || routeIconMap[routeId];
}
