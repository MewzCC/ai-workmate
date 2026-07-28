'use client';

import { Avatar, Button, Dropdown, Layout, Space, notification } from 'antd';
import { message } from '@/lib/antdMessage';
import type { MenuProps } from 'antd';
import type { OaRole } from '@/types/oa';
import { useAuth } from '@/components/auth/AuthProvider';
import { OaIcon } from '@/components/OaIcon';
import { useRouter } from '@/lib/nextCompat';
import { useState } from 'react';
import ProfileSettingsModal from '@/components/profile/ProfileSettingsModal';
import HelpDrawer from './HelpDrawer';

const { Header } = Layout;

interface TopbarProps {
  role: OaRole;
  pageTitle: string;
  onOpenAppearance: () => void;
  onOpenAi: (prompt?: string) => void;
}

export default function Topbar({ role, pageTitle, onOpenAppearance, onOpenAi }: TopbarProps) {
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
    { key: 'profile', icon: <OaIcon name="avatar" />, label: '个人资料' },
    { key: 'appearance', icon: <OaIcon name="appearance" />, label: '外观设置' },
    { type: 'divider' },
    { key: 'logout', icon: <OaIcon name="logout" />, label: '退出登录', danger: true },
  ];

  const onAvatarMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'profile') setProfileOpen(true);
    else if (key === 'appearance') onOpenAppearance();
    else if (key === 'logout') void handleLogout();
  };

  // 移动端"更多"菜单：业务功能 + 个人/外观/退出
  const moreMenuItems: MenuProps['items'] = [
    { key: 'newFlow', icon: <OaIcon name="add" />, label: '新建流程' },
    { key: 'notify', icon: <OaIcon name="notification" />, label: '通知' },
    { key: 'export', icon: <OaIcon name="export" />, label: '导出看板' },
    { key: 'help', icon: <OaIcon name="help" />, label: '帮助文档' },
    { type: 'divider' },
    { key: 'profile', icon: <OaIcon name="avatar" />, label: '个人资料' },
    { key: 'appearance', icon: <OaIcon name="appearance" />, label: '外观设置' },
    { type: 'divider' },
    { key: 'logout', icon: <OaIcon name="logout" />, label: '退出登录', danger: true },
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
        <h1>{pageTitle}</h1>
      </div>

      <div className="oa-header-right">
        <Space wrap className="oa-header-actions">
          <Button icon={<OaIcon name="help" />} onClick={handleHelp}>
            帮助文档
          </Button>
          <Button icon={<OaIcon name="notification" />} onClick={handleNotify}>
            通知
          </Button>
          <Button type="primary" icon={<OaIcon name="add" />} onClick={handleNewFlow}>
            新建流程
          </Button>
          <Button icon={<OaIcon name="export" />} onClick={handleExport}>
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
            <OaIcon name="more" />
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
            <Avatar size={28} src={user?.avatarUrl} icon={<OaIcon name="avatar" />} />
            <span className="oa-profile-name">{user?.name || role}</span>
            <OaIcon name="settings" className="oa-profile-caret" />
          </Button>
        </Dropdown>
      </div>
      <ProfileSettingsModal open={profileOpen} onClose={() => setProfileOpen(false)} />
      <HelpDrawer open={helpOpen} role={role} onClose={() => setHelpOpen(false)} onOpenAi={onOpenAi} />
    </Header>
  );
}
