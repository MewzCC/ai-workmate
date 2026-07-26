'use client';

import {
  BellOutlined,
  FileTextOutlined,
  PlusOutlined,
  QuestionCircleOutlined,
  SkinOutlined,
  LogoutOutlined,
  UserOutlined,
  SettingOutlined,
  MoreOutlined,
} from '@ant-design/icons';
import { Avatar, Breadcrumb, Button, Dropdown, Layout, Space, message, notification } from 'antd';
import type { MenuProps } from 'antd';
import type { OaRole } from '@/types/oa';
import { useAuth } from '@/components/auth/AuthProvider';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import ProfileSettingsModal from '@/components/profile/ProfileSettingsModal';
import HelpDrawer from './HelpDrawer';

const { Header } = Layout;

interface TopbarProps {
  role: OaRole;
  pageTitle: string;
  breadcrumbs: Array<{ title: string }>;
  onOpenAppearance: () => void;
  onOpenAi: (prompt?: string) => void;
}

export default function Topbar({ role, pageTitle, breadcrumbs, onOpenAppearance, onOpenAi }: TopbarProps) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [profileOpen, setProfileOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);

  const handleLogout = async () => {
    await logout();
    router.replace('/auth');
  };

  const handleHelp = () => setHelpOpen(true);
  const handleNotify = () => notification.info({ message: '通知中心', description: '你有 3 条审批提醒、1 条接口告警待处理。' });
  const handleNewFlow = () => onOpenAi('帮我新建一个跨部门采购申请，并检查审批链是否完整');
  const handleExport = () => message.warning('真实导出能力尚未接入');

  const avatarMenuItems: MenuProps['items'] = [
    { key: 'profile', icon: <UserOutlined />, label: '个人资料' },
    { key: 'appearance', icon: <SkinOutlined />, label: '外观设置' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  const onAvatarMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'profile') setProfileOpen(true);
    else if (key === 'appearance') onOpenAppearance();
    else if (key === 'logout') void handleLogout();
  };

  // 移动端"更多"菜单：业务功能 + 个人/外观/退出
  const moreMenuItems: MenuProps['items'] = [
    { key: 'newFlow', icon: <PlusOutlined />, label: '新建流程' },
    { key: 'notify', icon: <BellOutlined />, label: '通知' },
    { key: 'export', icon: <FileTextOutlined />, label: '导出看板' },
    { key: 'help', icon: <QuestionCircleOutlined />, label: '帮助文档' },
    { type: 'divider' },
    { key: 'profile', icon: <UserOutlined />, label: '个人资料' },
    { key: 'appearance', icon: <SkinOutlined />, label: '外观设置' },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true },
  ];

  const onMoreMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'newFlow') handleNewFlow();
    else if (key === 'notify') handleNotify();
    else if (key === 'export') handleExport();
    else if (key === 'help') handleHelp();
    else if (key === 'profile') setProfileOpen(true);
    else if (key === 'appearance') onOpenAppearance();
    else if (key === 'logout') void handleLogout();
  };

  return (
    <Header className="oa-header">
      <div className="oa-header-title">
        <Breadcrumb items={breadcrumbs} />
        <h1>{pageTitle}</h1>
      </div>

      <div className="oa-header-right">
        <Space wrap className="oa-header-actions">
          <Button icon={<QuestionCircleOutlined />} onClick={handleHelp}>
            帮助文档
          </Button>
          <Button icon={<BellOutlined />} onClick={handleNotify}>
            通知
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleNewFlow}>
            新建流程
          </Button>
          <Button icon={<FileTextOutlined />} onClick={handleExport}>
            导出看板
          </Button>
        </Space>

        {/* 移动端"更多"按钮 */}
        <Dropdown
          menu={{ items: moreMenuItems, onClick: onMoreMenuClick }}
          trigger={['click']}
          placement="bottomRight"
          className="oa-header-more"
        >
          <Button type="text" className="oa-header-more-btn" aria-label="更多操作">
            <MoreOutlined />
          </Button>
        </Dropdown>

        {/* 桌面端头像 */}
        <Dropdown
          menu={{ items: avatarMenuItems, onClick: onAvatarMenuClick }}
          trigger={['click']}
          placement="bottomRight"
          className="oa-header-avatar-desktop"
        >
          <Button type="text" className="oa-profile-trigger">
            <Avatar size={28} src={user?.avatarUrl} icon={<UserOutlined />} />
            <span className="oa-profile-name">{user?.name || role}</span>
            <SettingOutlined className="oa-profile-caret" />
          </Button>
        </Dropdown>
      </div>
      <ProfileSettingsModal open={profileOpen} onClose={() => setProfileOpen(false)} />
      <HelpDrawer open={helpOpen} role={role} onClose={() => setHelpOpen(false)} onOpenAi={onOpenAi} />
    </Header>
  );
}
