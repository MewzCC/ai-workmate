'use client';

import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { usePathname, useRouter } from '@/lib/nextCompat';
import { ConfigProvider, FloatButton, Layout, theme as antdTheme } from 'antd';
import { message } from '@/lib/antdMessage';
import type { OaMenuItem, OaRole, OaTheme } from '@/types/oa';
import { findMenu } from '@/mock/oaPermissions';
import Dashboard from './Dashboard';
import SidebarMenu from './SidebarMenu';
import Topbar from './Topbar';
import AppearanceDrawer from './AppearanceDrawer';
import AIOperationDrawer from './AIOperationDrawer';
import AiMiniPanel from './AiMiniPanel';
import AiChatWorkspace from '@/components/ai-chat/AiChatWorkspace';
import { useAuth } from '@/components/auth/AuthProvider';
import AccessControlPage from './AccessControlPage';
import NotificationPage from './NotificationPage';
import { getNavigation, type NavigationRoute } from '@/lib/navigationApi';
import { profileApi } from '@/lib/profileApi';
import { OaIcon } from '@/components/OaIcon';
import PageTabBar, { type OaPageTab } from './PageTabBar';
import TodoListPage from './TodoListPage';
import ApprovalListPage from './ApprovalListPage';
import FormEnginePage from './FormEnginePage';
import ProcessConfigPage from './ProcessConfigPage';
import ApprovalRulesPage from './ApprovalRulesPage';
import LeaveFormPage from './LeaveFormPage';
import MyApplicationsPage from './MyApplicationsPage';
import ApprovalDetailPage from './ApprovalDetailPage';
import ApprovalStartPage from './ApprovalStartPage';
import ApprovalFormPage from './ApprovalFormPage';
import AuditCenterPage from './AuditCenterPage';
import OrganizationTreePage from './OrganizationTreePage';
import EmployeeFilePage from './EmployeeFilePage';
import EmployeeChangePage from './EmployeeChangePage';
import KnowledgeBasePage from './KnowledgeBasePage';
import SystemSettingsPage from './SystemSettingsPage';
import AttendanceClockPage from './AttendanceClockPage';
import AttendanceExceptionPage from './AttendanceExceptionPage';
import AttendanceReissuePage from './AttendanceReissuePage';
import AttendanceStatisticsPage from './AttendanceStatisticsPage';
import AttendanceSettingsPage from './AttendanceSettingsPage';
import AssetLedgerPage from './AssetLedgerPage';
import MeetingRoomPage from './MeetingRoomPage';
import VisitorBookingPage from './VisitorBookingPage';
import SealUsagePage from './SealUsagePage';
import AiTaskCenterPage from './AiTaskCenterPage';

const { Content } = Layout;
const OPEN_TABS_STORAGE_KEY = 'workmeta-oa-open-tabs';
const MAX_OPEN_TABS = 20;
const MOBILE_BREAKPOINT = '(max-width: 720px)';

function isMobileViewport(): boolean {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia(MOBILE_BREAKPOINT).matches;
}

const themes: OaTheme[] = [
  {
    name: 'enterprise-blue',
    primary: '#1677ff',
    sidebar: '#0f1f3d',
    siderText: '#d7e7ff',
    surface: '#f4f7fb',
    card: '#ffffff',
    text: '#111827',
    muted: '#64748b',
    border: 'rgba(15, 23, 42, 0.08)',
    header: 'rgba(255,255,255,0.86)',
  },
  {
    name: 'deep-green',
    primary: '#0f766e',
    sidebar: '#0b2f2c',
    siderText: '#d7fbf4',
    surface: '#f2faf8',
    card: '#ffffff',
    text: '#111827',
    muted: '#64748b',
    border: 'rgba(15, 23, 42, 0.08)',
    header: 'rgba(255,255,255,0.86)',
  },
  {
    name: 'premium-purple',
    primary: '#7048e8',
    sidebar: '#251451',
    siderText: '#ece6ff',
    surface: '#f6f3ff',
    card: '#ffffff',
    text: '#111827',
    muted: '#64748b',
    border: 'rgba(15, 23, 42, 0.08)',
    header: 'rgba(255,255,255,0.86)',
  },
  {
    name: 'ink-gray',
    primary: '#343a40',
    sidebar: '#181a1f',
    siderText: '#f1f3f5',
    surface: '#f5f6f7',
    card: '#ffffff',
    text: '#111827',
    muted: '#64748b',
    border: 'rgba(15, 23, 42, 0.08)',
    header: 'rgba(255,255,255,0.86)',
  },
  {
    name: 'warm-orange',
    primary: '#d9480f',
    sidebar: '#3b1f10',
    siderText: '#fff0e6',
    surface: '#fff7ed',
    card: '#ffffff',
    text: '#111827',
    muted: '#64748b',
    border: 'rgba(15, 23, 42, 0.08)',
    header: 'rgba(255,255,255,0.86)',
  },
  {
    name: 'home-style',
    primary: '#111111',
    sidebar: '#17120d',
    siderText: '#fff4e6',
    surface: '#f3eee5',
    card: 'rgba(255,255,255,0.9)',
    text: '#17120d',
    muted: '#7a7168',
    border: 'rgba(93, 69, 45, 0.14)',
    header: 'rgba(246,241,232,0.88)',
  },
  {
    name: 'home-night',
    primary: '#8b5cf6',
    sidebar: '#080911',
    siderText: '#f4f0ff',
    surface: '#0b0c12',
    card: '#14151f',
    text: '#f8fafc',
    muted: '#a6adbb',
    border: 'rgba(148, 163, 184, 0.18)',
    header: 'rgba(12,13,20,0.86)',
    dark: true,
  },
];

function readStorage(key: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  return window.localStorage.getItem(key) || fallback;
}

export default function AdminLayout() {
  const router = useRouter();
  const pathname = usePathname();
  const { t } = useTranslation();
  const dashboardMenu = useMemo<OaMenuItem>(() => ({
    id: 'dashboard',
    name: t('oa.dashboard'),
    type: 'page',
    sort: 1,
    visible: true,
  }), [t]);
  const approvalTaskId = useMemo(() => {
    const match = pathname.match(/^\/oa\/approval-tasks\/(\d+)$/);
    return match ? Number(match[1]) : undefined;
  }, [pathname]);
  const kbId = useMemo(() => {
    const match = pathname.match(/^\/oa\/knowledge-bases\/(\d+)$/);
    return match ? Number(match[1]) : undefined;
  }, [pathname]);
  const currentPageId = useMemo(() => {
    if (approvalTaskId) return 'todo';
    if (kbId) return 'knowledge-base';
    const segments = pathname.split('/').filter(Boolean);
    return segments.length > 1 ? decodeURIComponent(segments[1]) : 'dashboard';
  }, [approvalTaskId, kbId, pathname]);
  const { user } = useAuth();
  const role = useMemo<OaRole>(() => {
    if (user?.role === 'SUPER_ADMIN') return 'super_admin';
    if (user?.role === 'ADMIN' || user?.role === 'SYSTEM_ADMIN') return 'system_admin';
    if (user?.role === 'PROCESS_ADMIN') return 'process_admin';
    if (user?.role === 'FINANCE_ADMIN') return 'finance_admin';
    return 'employee';
  }, [user?.role]);
  const [collapsed, setCollapsed] = useState(() => isMobileViewport());
  const [menus, setMenus] = useState<OaMenuItem[]>([]);
  const [navigationLoaded, setNavigationLoaded] = useState(false);
  const [selectedMenu, setSelectedMenu] = useState<OaMenuItem>(dashboardMenu);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [aiDrawerPresent, setAiDrawerPresent] = useState(false);
  const [aiPrompt, setAiPrompt] = useState('');
  const [themeName, setThemeName] = useState(() => readStorage('workmeta-oa-theme', 'enterprise-blue'));
  const [aiMiniEnabled, setAiMiniEnabled] = useState(() => readStorage('workmeta-oa-ai-mini-enabled', 'false') === 'true');
  const [wallpaper, setWallpaper] = useState<string | null>(null);
  const [wallpaperOpacity, setWallpaperOpacity] = useState(() => Number(readStorage('workmeta-oa-wallpaper-opacity', '0.28')));
  const [wallpaperBlur, setWallpaperBlur] = useState(() => Number(readStorage('workmeta-oa-wallpaper-blur', '4')));
  const [auditItems, setAuditItems] = useState<Array<{ color: string; content: string }>>([]);
  const [openTabs, setOpenTabs] = useState<OaPageTab[]>([]);
  const [openTabsReady, setOpenTabsReady] = useState(false);

  const currentTheme = useMemo(() => themes.find((theme) => theme.name === themeName) || themes[0], [themeName]);
  const pinnedMenu = useMemo(
    () => findMenu('dashboard', menus) || firstPage(menus),
    [menus],
  );

  useEffect(() => {
    let active = true;
    if (!user) return;
    setNavigationLoaded(false);
    setOpenTabsReady(false);
    getNavigation()
      .then((routes) => {
        if (!active) return;
        setMenus(routes.map(toMenuItem));
        setNavigationLoaded(true);
      })
      .catch((error) => {
        if (!active) return;
        setMenus([]);
        setNavigationLoaded(true);
        message.error(error instanceof Error ? error.message : t('oa.errors.navLoadFailed'));
      });
    return () => {
      active = false;
    };
  }, [user]);

  useEffect(() => {
    let active = true;
    if (!user) {
      setWallpaper(null);
      return;
    }
    const loadWallpaper = async () => {
      try {
        const stored = await profileApi.getWallpaper();
        if (!active) return;
        if (stored.wallpaperUrl) {
          setWallpaper(stored.wallpaperUrl);
          window.localStorage.removeItem('workmeta-oa-wallpaper');
          return;
        }

        const legacyWallpaper = window.localStorage.getItem('workmeta-oa-wallpaper');
        if (!legacyWallpaper?.startsWith('data:image/')) {
          setWallpaper(null);
          return;
        }
        const file = await fetch(legacyWallpaper).then((response) => response.blob());
        const migrated = await profileApi.uploadWallpaper(file);
        if (!active) return;
        setWallpaper(migrated.wallpaperUrl);
        window.localStorage.removeItem('workmeta-oa-wallpaper');
      } catch (error) {
        if (!active) return;
        setWallpaper(null);
        message.error(error instanceof Error ? error.message : t('oa.errors.wallpaperLoadFailed'));
      }
    };
    void loadWallpaper();
    return () => {
      active = false;
    };
  }, [user]);

  useEffect(() => {
    if (!navigationLoaded) return;
    const visibleMenu = findMenu(currentPageId, menus);
    if (visibleMenu?.type === 'page') {
      setSelectedMenu(visibleMenu);
      return;
    }
    // 仅当当前 pageId 在菜单中确实不存在时，才回退到首个可用页面
    // 延迟 300ms 执行，避免与用户主动点击触发的路由跳转打架
    const fallback = findMenu('dashboard', menus) || firstPage(menus);
    if (!fallback) return;
    const timer = setTimeout(() => {
      setSelectedMenu(fallback);
      if (fallback.id !== currentPageId) {
        router.replace(fallback.path || `/oa/${fallback.id}`);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [currentPageId, menus, navigationLoaded, router]);

  useEffect(() => {
    document.title = `${t('oa.pageTitle.brand')} - ${approvalTaskId ? t('oa.pageTitle.approvalDetail') : kbId ? t('oa.pageTitle.knowledgeDetail') : t(`oa.menu.${selectedMenu.id}`, { defaultValue: selectedMenu.name })}`;
  }, [approvalTaskId, kbId, selectedMenu.id, selectedMenu.name, t]);

  useEffect(() => {
    if (!navigationLoaded || openTabsReady) return;
    const pages = flattenPages(menus);
    const pageMap = new Map(pages.map((page) => [page.id, page]));
    const pinnedPage = pageMap.get('dashboard') || pages[0];
    const currentPage = pageMap.get(currentPageId);
    let storedIds: string[] = [];
    try {
      const raw = window.localStorage.getItem(OPEN_TABS_STORAGE_KEY);
      const parsed = raw ? JSON.parse(raw) : [];
      if (Array.isArray(parsed)) {
        storedIds = parsed.filter((value): value is string => typeof value === 'string');
      }
    } catch {
      storedIds = [];
    }

    const restored = storedIds
      .map((id) => pageMap.get(id))
      .filter((page): page is OaMenuItem => Boolean(page))
      .map(toPageTab);
    const initialTabs = limitTabs(uniqueTabs([
      ...(pinnedPage ? [toPageTab(pinnedPage)] : []),
      ...restored,
      ...(currentPage ? [toPageTab(currentPage)] : []),
    ]), pinnedPage?.id);
    setOpenTabs(initialTabs);
    setOpenTabsReady(true);
  }, [currentPageId, menus, navigationLoaded, openTabsReady]);

  useEffect(() => {
    if (!openTabsReady || selectedMenu.type !== 'page') return;
    setOpenTabs((current) => {
      const next = uniqueTabs([...current, toPageTab(selectedMenu)]);
      return limitTabs(next, pinnedMenu?.id);
    });
  }, [openTabsReady, pinnedMenu?.id, selectedMenu]);

  useEffect(() => {
    if (!openTabsReady) return;
    window.localStorage.setItem(
      OPEN_TABS_STORAGE_KEY,
      JSON.stringify(openTabs.map((tab) => tab.id)),
    );
  }, [openTabs, openTabsReady]);

  // 路由切换完成后关闭 loading 提示
  useEffect(() => {
    if (!navigationLoaded) return;
    message.destroy('oa-route-switch');
    if (selectedMenu.id === currentPageId) {
      message.info(t('oa.switchedTo', { name: t(`oa.menu.${selectedMenu.id}`, { defaultValue: selectedMenu.name }) }), 1.5);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname, navigationLoaded]);

  // 进入移动端视口时自动收起侧栏（用户手动展开/收起状态在桌面端保留）
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mql = window.matchMedia(MOBILE_BREAKPOINT);
    const onViewportChange = (event: MediaQueryListEvent) => {
      if (event.matches) setCollapsed(true);
    };
    mql.addEventListener('change', onViewportChange);
    return () => mql.removeEventListener('change', onViewportChange);
  }, []);

  useEffect(() => {
    document.documentElement.style.setProperty('--oa-primary', currentTheme.primary);
    document.documentElement.style.setProperty('--oa-sidebar', currentTheme.sidebar);
    document.documentElement.style.setProperty('--oa-sider-text', currentTheme.siderText);
    document.documentElement.style.setProperty('--oa-surface', currentTheme.surface);
    document.documentElement.style.setProperty('--oa-card', currentTheme.card);
    document.documentElement.style.setProperty('--oa-text', currentTheme.text);
    document.documentElement.style.setProperty('--oa-muted', currentTheme.muted);
    document.documentElement.style.setProperty('--oa-border', currentTheme.border);
    document.documentElement.style.setProperty('--oa-header', currentTheme.header);
    window.localStorage.setItem('workmeta-oa-theme', currentTheme.name);
  }, [currentTheme]);

  useEffect(() => {
    window.localStorage.setItem('workmeta-oa-ai-mini-enabled', String(aiMiniEnabled));
  }, [aiMiniEnabled]);

  useEffect(() => {
    document.documentElement.classList.toggle('oa-wallpaper-active', Boolean(wallpaper));
    return () => document.documentElement.classList.remove('oa-wallpaper-active');
  }, [wallpaper]);

  useEffect(() => {
    window.localStorage.setItem('workmeta-oa-wallpaper-opacity', String(wallpaperOpacity));
    window.localStorage.setItem('workmeta-oa-wallpaper-blur', String(wallpaperBlur));
  }, [wallpaperOpacity, wallpaperBlur]);

  const openAi = (prompt?: string) => {
    setAiPrompt(prompt || '');
    setAiOpen(true);
  };

  const addAudit = (text: string) => {
    setAuditItems((prev) => [{ color: currentTheme.primary, content: `${new Date().toLocaleTimeString()} ${text}` }, ...prev].slice(0, 6));
  };

  const navigateToPage = (tab: OaPageTab) => {
    if (tab.id === currentPageId) return;
    message.loading({
      content: t('oa.switchingTo', { name: t(`oa.menu.${tab.id}`, { defaultValue: tab.name }) }),
      key: 'oa-route-switch',
      duration: 0,
    });
    router.push(tab.path);
  };

  const closePageTab = (tabId: string) => {
    if (tabId === pinnedMenu?.id) return;
    const closingIndex = openTabs.findIndex((tab) => tab.id === tabId);
    if (closingIndex < 0) return;
    const nextTabs = openTabs.filter((tab) => tab.id !== tabId);
    setOpenTabs(nextTabs);
    if (tabId === currentPageId) {
      const nextActive =
        nextTabs[Math.min(closingIndex, nextTabs.length - 1)]
        || nextTabs[nextTabs.length - 1];
      if (nextActive) navigateToPage(nextActive);
    }
  };

  const closeOtherTabs = () => {
    setOpenTabs((tabs) =>
      tabs.filter((tab) => tab.id === pinnedMenu?.id || tab.id === currentPageId),
    );
  };

  const closeAllTabs = () => {
    if (!pinnedMenu) return;
    const pinnedTab = toPageTab(pinnedMenu);
    setOpenTabs([pinnedTab]);
    if (currentPageId !== pinnedTab.id) navigateToPage(pinnedTab);
  };

  const reorderTabs = (sourceId: string, targetId: string) => {
    setOpenTabs((current) => {
      const sourceIndex = current.findIndex((t) => t.id === sourceId);
      const targetIndex = current.findIndex((t) => t.id === targetId);
      if (sourceIndex === -1 || targetIndex === -1) return current;
      const next = [...current];
      const [moved] = next.splice(sourceIndex, 1);
      next.splice(targetIndex, 0, moved);
      return next;
    });
  };

  return (
    <ConfigProvider
      theme={{
        algorithm: currentTheme.dark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token: {
          colorPrimary: currentTheme.primary,
          borderRadius: 8,
          fontFamily: 'var(--font-geist-sans), system-ui, sans-serif',
          colorBgLayout: currentTheme.surface,
          colorBgContainer: currentTheme.card,
          colorText: currentTheme.text,
          colorTextSecondary: currentTheme.muted,
          colorBorder: currentTheme.border,
        },
        components: {
          Menu: {
            darkItemBg: currentTheme.sidebar,
            darkPopupBg: currentTheme.sidebar,
            darkSubMenuItemBg: currentTheme.sidebar,
            darkItemColor: currentTheme.siderText,
            darkItemHoverColor: '#ffffff',
            darkItemHoverBg: `color-mix(in srgb, ${currentTheme.primary} 24%, ${currentTheme.sidebar})`,
            darkItemSelectedColor: '#ffffff',
            darkItemSelectedBg: currentTheme.primary,
            darkGroupTitleColor: currentTheme.siderText,
          },
          Table: {
            headerBg: `color-mix(in srgb, ${currentTheme.card} 96%, ${currentTheme.text} 4%)`,
            headerColor: currentTheme.muted,
            rowHoverBg: `color-mix(in srgb, ${currentTheme.card} 94%, ${currentTheme.primary} 6%)`,
            borderColor: currentTheme.border,
            headerBorderRadius: 8,
          },
        },
      }}
    >
      <>
        <div className={`oa-shell ${collapsed ? 'oa-shell-collapsed' : ''} ${wallpaper ? 'oa-has-wallpaper' : ''} ${selectedMenu.id === 'ai-workspace' ? 'oa-chat-page' : ''}`}>
          <div
            className={`oa-sider-mask ${collapsed ? '' : 'is-visible'}`}
            onClick={() => setCollapsed(true)}
            aria-hidden="true"
          />
          {wallpaper && (
            <div
              className="oa-wallpaper-layer"
              aria-hidden="true"
              style={{
                backgroundImage: `url(${wallpaper})`,
                filter: `blur(${wallpaperBlur}px)`,
                opacity: wallpaperOpacity,
              }}
            />
          )}
          <Layout className="oa-layout">
            <SidebarMenu
              menus={menus}
              selectedKey={findMenu(currentPageId, menus)?.id || selectedMenu.id}
              initialSelectedKey={currentPageId}
              collapsed={collapsed}
              onCollapse={setCollapsed}
              onSelect={(menu) => {
                if (menu.id === currentPageId) {
                  // 移动端点击当前菜单也收起覆盖层
                  if (isMobileViewport()) setCollapsed(true);
                  return;
                }
                const target = menu.path || `/oa/${menu.id}`;
                message.loading({
                  content: t('oa.switchingTo', { name: t(`oa.menu.${menu.id}`, { defaultValue: menu.name }) }),
                  key: 'oa-route-switch',
                  duration: 0,
                });
                router.push(target);
                // 移动端选择菜单后收起覆盖层
                if (isMobileViewport()) setCollapsed(true);
              }}
            />
            <Layout>
              <div className="oa-top-stack">
                <Topbar
                  role={role}
                  pageTitle={t(`oa.menu.${selectedMenu.id}`, { defaultValue: selectedMenu.name })}
                  onOpenAppearance={() => setAppearanceOpen(true)}
                  onOpenAi={openAi}
                  onToggleMenu={() => setCollapsed((value) => !value)}
                />
                {openTabsReady ? (
                  <PageTabBar
                    tabs={openTabs}
                    activeKey={selectedMenu.id}
                    pinnedKey={pinnedMenu?.id}
                    onNavigate={navigateToPage}
                    onClose={closePageTab}
                    onCloseOthers={closeOtherTabs}
                    onCloseAll={closeAllTabs}
                    onRefresh={() => router.refresh()}
                    onReorder={reorderTabs}
                  />
                ) : null}
              </div>
              <Content className={`oa-content ${selectedMenu.id === 'ai-workspace' ? 'oa-chat-content' : ''}`}>
                <div key={selectedMenu.id} className="oa-page-transition">
                  {approvalTaskId ? (
                    <ApprovalDetailPage taskId={approvalTaskId} />
                  ) : kbId ? (
                    <KnowledgeBasePage kbId={kbId} />
                  ) : selectedMenu.componentKey === 'AI_WORKSPACE' ? (
                    <AiChatWorkspace role={role} />
                  ) : selectedMenu.componentKey === 'AI_TASK_CENTER' ? (
                    <AiTaskCenterPage />
                  ) : selectedMenu.componentKey === 'MESSAGE_CENTER' ? (
                    <NotificationPage />
                  ) : selectedMenu.componentKey === 'ACCESS_CONTROL' ? (
                    <AccessControlPage />
                  ) : selectedMenu.componentKey === 'TODO_LIST' ? (
                    <TodoListPage />
                  ) : selectedMenu.componentKey === 'APPROVAL_LIST' ? (
                    <ApprovalListPage />
                  ) : selectedMenu.componentKey === 'APPROVAL_START' ? (
                    <ApprovalStartPage />
                  ) : selectedMenu.componentKey === 'APPROVAL_FORM' ? (
                    <ApprovalFormPage />
                  ) : selectedMenu.componentKey === 'FORM_ENGINE' ? (
                    <FormEnginePage />
                  ) : selectedMenu.componentKey === 'PROCESS_CONFIG' ? (
                    <ProcessConfigPage />
                  ) : selectedMenu.componentKey === 'APPROVAL_RULES' ? (
                    <ApprovalRulesPage />
                  ) : selectedMenu.componentKey === 'LEAVE_FORM' ? (
                    <LeaveFormPage />
                  ) : selectedMenu.componentKey === 'MY_APPLICATIONS' ? (
                    <MyApplicationsPage />
                  ) : selectedMenu.componentKey === 'AUDIT_CENTER' ? (
                    <AuditCenterPage />
                  ) : selectedMenu.componentKey === 'ORG_TREE' ? (
                    <OrganizationTreePage />
                  ) : selectedMenu.componentKey === 'EMPLOYEE_FILES' ? (
                    <EmployeeFilePage />
                  ) : selectedMenu.componentKey === 'EMPLOYEE_CHANGE' ? (
                    <EmployeeChangePage />
                  ) : selectedMenu.componentKey === 'KNOWLEDGE_BASE' ? (
                    <KnowledgeBasePage />
                  ) : selectedMenu.componentKey === 'SYSTEM_CONFIG' ? (
                    <SystemSettingsPage />
                  ) : selectedMenu.componentKey === 'ATTENDANCE_CLOCK' ? (
                    <AttendanceClockPage />
                  ) : selectedMenu.componentKey === 'ATTENDANCE_EXCEPTION' ? (
                    <AttendanceExceptionPage />
                  ) : selectedMenu.componentKey === 'ATTENDANCE_REISSUE' ? (
                    <AttendanceReissuePage />
                  ) : selectedMenu.componentKey === 'ATTENDANCE_STATISTICS' ? (
                    <AttendanceStatisticsPage />
                  ) : selectedMenu.componentKey === 'ATTENDANCE_SETTINGS' ? (
                    <AttendanceSettingsPage />
                  ) : selectedMenu.componentKey === 'ASSET_LEDGER' ? (
                    <AssetLedgerPage />
                  ) : selectedMenu.componentKey === 'MEETING_ROOM' ? (
                    <MeetingRoomPage />
                  ) : selectedMenu.componentKey === 'VISITOR_BOOKING' ? (
                    <VisitorBookingPage />
                  ) : selectedMenu.componentKey === 'SEAL_USAGE' ? (
                    <SealUsagePage />
                  ) : (
                    <Dashboard
                      role={role}
                      pageId={selectedMenu.id}
                      pageTitle={t(`oa.menu.${selectedMenu.id}`, { defaultValue: selectedMenu.name })}
                      primaryColor={currentTheme.primary}
                      auditItems={auditItems}
                      onOpenAi={openAi}
                      onAddAudit={addAudit}
                    />
                  )}
                </div>
              </Content>
            </Layout>
          </Layout>

          {selectedMenu.id !== 'ai-workspace' && <FloatButton
            type="primary"
            icon={<OaIcon name="ai" size={20} />}
            tooltip={t('oa.ai.openPanel')}
            onClick={() => openAi()}
          />}

          {aiMiniEnabled
            && !aiOpen
            && !aiDrawerPresent
            && selectedMenu.id !== 'ai-workspace'
            && <AiMiniPanel onOpenAi={openAi} />}

          <AppearanceDrawer
            open={appearanceOpen}
            themes={themes}
            currentTheme={currentTheme.name}
            aiMiniEnabled={aiMiniEnabled}
            wallpaper={wallpaper}
            wallpaperOpacity={wallpaperOpacity}
            wallpaperBlur={wallpaperBlur}
            onClose={() => setAppearanceOpen(false)}
            onThemeChange={setThemeName}
            onAiMiniChange={setAiMiniEnabled}
            onWallpaperChange={setWallpaper}
            onWallpaperOpacityChange={setWallpaperOpacity}
            onWallpaperBlurChange={setWallpaperBlur}
          />

          {selectedMenu.id !== 'ai-workspace' && <AIOperationDrawer
            open={aiOpen}
            role={role}
            pageId={selectedMenu.id}
            pageTitle={t(`oa.menu.${selectedMenu.id}`, { defaultValue: selectedMenu.name })}
            initialPrompt={aiPrompt}
            onClose={() => setAiOpen(false)}
            onOpenChangeComplete={setAiDrawerPresent}
            onExecuted={addAudit}
          />}
        </div>
      </>
    </ConfigProvider>
  );
}

function toMenuItem(route: NavigationRoute): OaMenuItem {
  return {
    id: route.routeKey,
    parentId: route.parentKey,
    name: route.name,
    type: route.routeType.toLowerCase() as OaMenuItem['type'],
    icon: route.icon,
    path: route.path,
    componentKey: route.componentKey,
    permissionCode: route.permissionCode,
    sort: route.sortOrder,
    visible: true,
    children: route.children?.length ? route.children.map(toMenuItem) : undefined,
  };
}

function firstPage(menus: OaMenuItem[]): OaMenuItem | undefined {
  for (const menu of menus) {
    if (menu.type === 'page') return menu;
    const child = firstPage(menu.children || []);
    if (child) return child;
  }
  return undefined;
}

function flattenPages(menus: OaMenuItem[]): OaMenuItem[] {
  return menus.flatMap((menu) => [
    ...(menu.type === 'page' ? [menu] : []),
    ...flattenPages(menu.children || []),
  ]);
}

function toPageTab(menu: OaMenuItem): OaPageTab {
  return {
    id: menu.id,
    name: menu.name,
    path: menu.path || `/oa/${menu.id}`,
    icon: menu.icon,
  };
}

function uniqueTabs(tabs: OaPageTab[]): OaPageTab[] {
  const unique = new Map<string, OaPageTab>();
  tabs.forEach((tab) => unique.set(tab.id, tab));
  return [...unique.values()];
}

function limitTabs(tabs: OaPageTab[], pinnedId?: string): OaPageTab[] {
  if (tabs.length <= MAX_OPEN_TABS) return tabs;
  const pinned = pinnedId ? tabs.find((tab) => tab.id === pinnedId) : undefined;
  const recent = tabs
    .filter((tab) => tab.id !== pinnedId)
    .slice(-(MAX_OPEN_TABS - (pinned ? 1 : 0)));
  return pinned ? [pinned, ...recent] : recent;
}
