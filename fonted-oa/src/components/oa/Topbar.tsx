'use client';

import { Avatar, Button, Dropdown, Layout, Space } from 'antd';
import { MenuOutlined } from '@ant-design/icons';
import { message } from '@/lib/antdMessage';
import type { MenuProps } from 'antd';
import type { OaRole } from '@/types/oa';
import { useAuth } from '@/components/auth/AuthProvider';
import { OaIcon } from '@/components/OaIcon';
import { useRouter } from '@/lib/nextCompat';
import { useCallback, useEffect, useState } from 'react';
import ProfileSettingsModal from '@/components/profile/ProfileSettingsModal';
import HelpDrawer from './HelpDrawer';
import {
  fetchUnreadCount,
  listNotifications,
  markNotificationRead,
  type NotificationItem,
} from '@/lib/notificationApi';

const { Header } = Layout;

const NOTIFY_POLL_INTERVAL_MS = 30_000;

interface TopbarProps {
  role: OaRole;
  pageTitle: string;
  onOpenAppearance: () => void;
  onOpenAi: (prompt?: string) => void;
  onToggleMenu: () => void;
}

export default function Topbar({ role, pageTitle, onOpenAppearance, onOpenAi, onToggleMenu }: TopbarProps) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const [profileOpen, setProfileOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifyItems, setNotifyItems] = useState<NotificationItem[]>([]);

  const loadNotifications = useCallback(async () => {
    try {
      const [pageResult, unread] = await Promise.all([
        listNotifications(1, 5),
        fetchUnreadCount(),
      ]);
      setNotifyItems(pageResult.records);
      setUnreadCount(unread);
    } catch {
      // 通知接口异常时保持现状，不打断顶栏
    }
  }, []);

  useEffect(() => {
    void loadNotifications();
    const timer = window.setInterval(() => void loadNotifications(), NOTIFY_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [loadNotifications]);

  const handleNotifyItemClick = async (item: NotificationItem) => {
    if (!item.read) {
      try {
        await markNotificationRead(item.id);
        setUnreadCount((current) => Math.max(0, current - 1));
        setNotifyItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, read: true } : entry));
      } catch {
        // 已读标记失败不阻断跳转
      }
    }
    router.push('/oa/messages');
  };

  const handleLogout = async () => {
    await logout();
    router.replace('/oa/auth');
  };

  const handleHelp = () => setHelpOpen(true);
  const handleNewFlow = () => onOpenAi('帮我新建一个跨部门采购申请，并检查审批链是否完整');
  const handleExport = () => message.warning('真实导出能力尚未接入');

  // 通知中心下拉内容（真实数据）
  const notifyContent = (
    <div className="oa-notify-panel">
      <div className="oa-notify-panel-head">
        <span className="oa-notify-panel-title">通知中心</span>
        <span className="oa-notify-panel-badge">{unreadCount} 条未读</span>
      </div>
      <ul className="oa-notify-panel-list">
        {notifyItems.length === 0 ? (
          <li className="oa-notify-empty">暂无通知</li>
        ) : notifyItems.map((item) => (
          <li
            key={item.id}
            className="oa-notify-item"
            role="button"
            tabIndex={0}
            onClick={() => void handleNotifyItemClick(item)}
            onKeyDown={(event) => {
              if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                void handleNotifyItemClick(item);
              }
            }}
          >
            <span className={`oa-notify-dot oa-notify-dot--${item.type}`} />
            <div className="oa-notify-item-body">
              <div className="oa-notify-item-title">
                {!item.read && <span className="oa-notify-unread-dot" />}
                {item.title}
              </div>
              <div className="oa-notify-item-desc">{item.content}</div>
            </div>
          </li>
        ))}
      </ul>
      <div className="oa-notify-panel-foot">
        <button type="button" className="oa-notify-link" onClick={() => router.push('/oa/messages')}>
          查看全部
        </button>
      </div>
    </div>
  );

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
    else if (key === 'notify') router.push('/oa/messages');
    else if (key === 'export') handleExport();
    else if (key === 'help') handleHelp();
    else if (key === 'profile') setProfileOpen(true);
    else if (key === 'appearance') onOpenAppearance();
    else if (key === 'logout') void handleLogout();
  };

  return (
    <Header className="oa-header">
      <Button
        type="text"
        className="oa-header-menu-btn"
        aria-label="展开或收起菜单"
        icon={<MenuOutlined />}
        onClick={onToggleMenu}
      />
      <div className="oa-header-title">
        <h1>{pageTitle}</h1>
      </div>

      <div className="oa-header-right">
        <Space wrap className="oa-header-actions">
          <Button icon={<OaIcon name="help" />} onClick={handleHelp}>
            帮助文档
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

        {/* 桌面端通知（hover 下拉） */}
        <Dropdown
          popupRender={() => notifyContent}
          trigger={['hover']}
          placement="bottomRight"
          className="oa-header-notify-desktop"
        >
          <Button type="text" className="oa-notify-trigger" aria-label="通知">
            <OaIcon name="notification" />
            {unreadCount > 0 && <span className="oa-notify-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
          </Button>
        </Dropdown>

        {/* 桌面端头像 */}
        <Dropdown
          menu={{ items: avatarMenuItems, onClick: onAvatarMenuClick }}
          trigger={['hover']}
          placement="bottomRight"
          className="oa-header-avatar-desktop"
        >
          <Button type="text" className="oa-profile-trigger">
            <Avatar size={28} src={user?.avatarUrl} icon={<OaIcon name="avatar" />} />
            <span className="oa-profile-name">{user?.name || role}</span>
          </Button>
        </Dropdown>
      </div>
      <ProfileSettingsModal open={profileOpen} onClose={() => setProfileOpen(false)} />
      <HelpDrawer open={helpOpen} role={role} onClose={() => setHelpOpen(false)} onOpenAi={onOpenAi} />
    </Header>
  );
}
