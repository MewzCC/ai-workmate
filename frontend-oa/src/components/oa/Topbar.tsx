'use client';

import { Avatar, Button, Dropdown, Layout, Space } from 'antd';
import { CheckOutlined, MenuOutlined } from '@ant-design/icons';
import { message } from '@/lib/antdMessage';
import type { MenuProps } from 'antd';
import { useTranslation } from 'react-i18next';
import type { OaRole } from '@/types/oa';
import { useAuth } from '@/components/auth/AuthProvider';
import { OaIcon } from '@/components/OaIcon';
import { useRouter } from '@/lib/nextCompat';
import { useCallback, useEffect, useRef, useState } from 'react';
import { usePermission } from '@/hooks/usePermission';
import { defaultDashboardExportRange, downloadDashboardExport, exportDashboard } from '@/lib/dashboardApi';
import { formatOaApiError } from '@/lib/oaApi';
import ProfileSettingsModal from '@/components/profile/ProfileSettingsModal';
import LanguageSwitcher from '@/components/LanguageSwitcher';
import { useLocale } from '@/i18n/useLocale';
import type { AppLocale } from '@/i18n';
import { SUPPORTED_LANGUAGES } from '@/i18n/languages';
import HelpDrawer from './HelpDrawer';
import { useAsyncActionGuard } from '@/hooks/useAsyncActionGuard';
import { useAsyncFormAction } from '@/hooks/useAsyncFormAction';
import { isAuthResponseIdentity } from '@/lib/authEvents';
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
  pageId: string;
  pageTitle: string;
  onOpenAppearance: () => void;
  onOpenAi: (prompt?: string) => void;
  onToggleMenu: () => void;
}

export default function Topbar({ role, pageId, pageTitle, onOpenAppearance, onOpenAi, onToggleMenu }: TopbarProps) {
  const { user, logout } = useAuth();
  const router = useRouter();
  const { t } = useTranslation();
  const { locale, changeLanguage } = useLocale();
  const [profileOpen, setProfileOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const [unreadCount, setUnreadCount] = useState(0);
  const [notifyItems, setNotifyItems] = useState<NotificationItem[]>([]);
  const { allowed: hasExportPermission, hasPermission, permissionVersion } = usePermission('data:export');
  const canExportDashboard = pageId === 'dashboard' && hasExportPermission;
  const identity = `${user?.tenantId}:${user?.id}`;
  const scope = `${identity}:${permissionVersion}`;
  const canReadNotifications = hasPermission('notification:read:self');
  const canOpenMessages = canReadNotifications && hasPermission('route:messages');
  const { capture } = useAsyncActionGuard(scope);
  const exportAction = useAsyncFormAction(`${scope}:${canExportDashboard}`);
  const logoutAction = useAsyncFormAction(identity);
  const exporting = exportAction.busy;
  const notificationRequest = useRef(0);
  const [notificationScope, setNotificationScope] = useState(scope);
  const visibleItems = notificationScope === scope && canReadNotifications ? notifyItems : [];
  const visibleUnread = notificationScope === scope && canReadNotifications ? unreadCount : 0;

  const loadNotifications = useCallback(async () => {
    const current = capture();
    if (!current() || !canReadNotifications) return;
    const request = ++notificationRequest.current;
    try {
      const [pageResult, unread] = await Promise.all([
        listNotifications(1, 5),
        fetchUnreadCount(),
      ]);
      if (!current() || request !== notificationRequest.current) return;
      setNotifyItems(pageResult.records);
      setUnreadCount(unread);
      setNotificationScope(scope);
    } catch {
      if (current() && request === notificationRequest.current) { setNotifyItems([]); setUnreadCount(0); }
    }
  }, [capture, canReadNotifications, scope]);

  useEffect(() => {
    const ownedRequest = notificationRequest;
    setNotifyItems([]); setUnreadCount(0); setNotificationScope(scope);
    if (!canReadNotifications) return;
    void loadNotifications();
    const timer = window.setInterval(() => void loadNotifications(), NOTIFY_POLL_INTERVAL_MS);
    return () => { window.clearInterval(timer); ownedRequest.current++; };
  }, [loadNotifications, canReadNotifications, scope]);
  useEffect(() => { setProfileOpen(false); setHelpOpen(false); }, [identity]);

  const handleNotifyItemClick = async (item: NotificationItem) => {
    const current = capture();
    if (!current() || !canOpenMessages) return;
    if (!item.read) {
      try {
        await markNotificationRead(item.id);
        if (!current()) return;
        setUnreadCount((current) => Math.max(0, current - 1));
        setNotifyItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, read: true } : entry));
      } catch {
        if (!current()) return;
        // 已读标记失败不阻断跳转
      }
    }
    if (current()) router.push('/oa/messages');
  };

  const handleLogout = async () => {
    if (!user) return;
    const attempt = logoutAction.begin();
    if (!attempt) return;
    try {
      if (await logout() && isAuthResponseIdentity(null)) router.replace('/oa/auth');
    } catch (error) { if (attempt.current()) message.error(formatOaApiError(error)); }
    finally { attempt.finish(); }
  };

  const handleHelp = () => setHelpOpen(true);
  const handleNewFlow = () => onOpenAi(t('oa.topbar.newFlowPrompt'));
  const handleExport = async () => {
    if (!canExportDashboard || exporting) return;
    const attempt = exportAction.begin();
    if (!attempt) return;
    try {
      const response = await exportDashboard(defaultDashboardExportRange(7));
      if (!attempt.current()) return;
      downloadDashboardExport(response);
      message.success(t('dashboard.messages.exported', { count: response.rowCount }));
    } catch (cause) {
      if (attempt.current()) message.error(formatOaApiError(cause));
    } finally {
      attempt.finish();
    }
  };

  // 通知中心下拉内容（真实数据）
  const notifyContent = (
    <div className="oa-notify-panel">
      <div className="oa-notify-panel-head">
        <span className="oa-notify-panel-title">{t('oa.topbar.notifyCenter')}</span>
        <span className="oa-notify-panel-badge">{t('oa.topbar.unreadCount', { count: visibleUnread })}</span>
      </div>
      <ul className="oa-notify-panel-list">
        {visibleItems.length === 0 ? (
          <li className="oa-notify-empty">{t('oa.topbar.noNotifications')}</li>
        ) : visibleItems.map((item) => (
          <li
            key={item.id}
            className="oa-notify-item"
            role="button"
            tabIndex={canOpenMessages ? 0 : -1}
            aria-disabled={!canOpenMessages}
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
      {canOpenMessages && <div className="oa-notify-panel-foot">
        <Button type="link" className="oa-notify-link" onClick={() => router.push('/oa/messages')}>
          {t('oa.topbar.viewAll')}
        </Button>
      </div>}
    </div>
  );

  const avatarMenuItems: MenuProps['items'] = [
    { key: 'profile', icon: <OaIcon name="avatar" />, label: t('oa.topbar.profile') },
    { key: 'appearance', icon: <OaIcon name="appearance" />, label: t('oa.topbar.appearance') },
    { type: 'divider' },
    { key: 'logout', icon: <OaIcon name="logout" />, label: t('oa.topbar.logout'), danger: true, disabled: logoutAction.busy },
  ];

  const onAvatarMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'profile') setProfileOpen(true);
    else if (key === 'appearance') onOpenAppearance();
    else if (key === 'logout') void handleLogout();
  };

  // 移动端"更多"菜单：业务功能 + 语言选择（子菜单，与 PC 下拉一致）+ 个人/外观/退出
  const moreMenuItems: MenuProps['items'] = [
    { key: 'newFlow', icon: <OaIcon name="add" />, label: t('oa.topbar.newFlow') },
    ...(canOpenMessages ? [{ key: 'notify', icon: <OaIcon name="notification" />, label: t('oa.topbar.notify') }] : []),
    ...(canExportDashboard ? [{ key: 'export', icon: <OaIcon name="export" />, label: t('oa.topbar.exportBoard') }] : []),
    { key: 'help', icon: <OaIcon name="help" />, label: t('oa.topbar.help') },
    { type: 'divider' },
    {
      key: 'switchLanguage',
      icon: <OaIcon name="global" />,
      label: t('common.language'),
      children: SUPPORTED_LANGUAGES.map((option) => ({
        key: `lang-${option.code}`,
        label: t(option.labelKey),
        icon: option.code === locale ? <CheckOutlined /> : undefined,
      })),
    },
    { type: 'divider' },
    { key: 'profile', icon: <OaIcon name="avatar" />, label: t('oa.topbar.profile') },
    { key: 'appearance', icon: <OaIcon name="appearance" />, label: t('oa.topbar.appearance') },
    { type: 'divider' },
    { key: 'logout', icon: <OaIcon name="logout" />, label: t('oa.topbar.logout'), danger: true, disabled: logoutAction.busy },
  ];

  const onMoreMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'newFlow') handleNewFlow();
    else if (key === 'notify' && canOpenMessages) router.push('/oa/messages');
    else if (key === 'export') void handleExport();
    else if (key === 'help') handleHelp();
    else if (key.startsWith('lang-')) changeLanguage(key.slice('lang-'.length) as AppLocale);
    else if (key === 'profile') setProfileOpen(true);
    else if (key === 'appearance') onOpenAppearance();
    else if (key === 'logout') void handleLogout();
  };

  return (
    <Header className="oa-header">
      <Button
        type="text"
        className="oa-header-menu-btn"
        aria-label={t('oa.topbar.toggleMenu')}
        icon={<MenuOutlined />}
        onClick={onToggleMenu}
      />
      <div className="oa-header-title">
        <h1>{pageTitle}</h1>
      </div>

      <div className="oa-header-right">
        <Space wrap className="oa-header-actions">
          <LanguageSwitcher />
          <Button icon={<OaIcon name="help" />} onClick={handleHelp}>
            {t('oa.topbar.help')}
          </Button>
          <Button type="primary" icon={<OaIcon name="add" />} onClick={handleNewFlow}>
            {t('oa.topbar.newFlow')}
          </Button>
          {canExportDashboard && <Button loading={exporting} icon={<OaIcon name="export" />} onClick={() => void handleExport()}>
            {t('oa.topbar.exportBoard')}
          </Button>}
        </Space>

        {/* 移动端"更多"按钮 */}
        <Dropdown
          menu={{ items: moreMenuItems, onClick: onMoreMenuClick }}
          trigger={['click']}
          placement="bottomRight"
          className="oa-header-more"
        >
          <Button type="text" className="oa-header-more-btn" aria-label={t('oa.topbar.moreActions')}>
            <OaIcon name="more" />
          </Button>
        </Dropdown>

        {/* 桌面端通知（hover 下拉） */}
        {canReadNotifications && <Dropdown
          popupRender={() => notifyContent}
          trigger={['hover']}
          placement="bottomRight"
          className="oa-header-notify-desktop"
        >
          <Button type="text" className="oa-notify-trigger" aria-label={t('oa.topbar.notifications')}>
            <OaIcon name="notification" />
            {visibleUnread > 0 && <span className="oa-notify-badge">{visibleUnread > 99 ? '99+' : visibleUnread}</span>}
          </Button>
        </Dropdown>}

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
      <ProfileSettingsModal key={`${user?.tenantId}:${user?.id}`} open={profileOpen} onClose={() => setProfileOpen(false)} />
      <HelpDrawer open={helpOpen} role={role} onClose={() => setHelpOpen(false)} onOpenAi={onOpenAi} />
    </Header>
  );
}
