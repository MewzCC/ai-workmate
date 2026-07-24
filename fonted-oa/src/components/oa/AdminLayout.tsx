'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { ConfigProvider, FloatButton, Layout, App as AntApp, message, theme as antdTheme } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
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
import { getNavigation, type NavigationRoute } from '@/lib/navigationApi';

const { Content } = Layout;

const dashboardMenu: OaMenuItem = {
  id: 'dashboard',
  name: '企业驾驶舱',
  type: 'page',
  sort: 1,
  visible: true,
};

const themes: OaTheme[] = [
  {
    name: 'enterprise-blue',
    label: '企业蓝',
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
    label: '深青绿',
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
    label: '高级紫',
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
    label: '石墨灰',
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
    label: '暖棕橙',
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
    label: '首页风格',
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
    label: '黑夜风格',
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
  const currentPageId = useMemo(() => {
    const segments = pathname.split('/').filter(Boolean);
    return segments.length > 1 ? decodeURIComponent(segments[1]) : 'dashboard';
  }, [pathname]);
  const { user } = useAuth();
  const role = useMemo<OaRole>(() => {
    if (user?.role === 'SUPER_ADMIN') return 'super_admin';
    if (user?.role === 'ADMIN' || user?.role === 'SYSTEM_ADMIN') return 'system_admin';
    if (user?.role === 'PROCESS_ADMIN') return 'process_admin';
    if (user?.role === 'FINANCE_ADMIN') return 'finance_admin';
    return 'employee';
  }, [user?.role]);
  const [collapsed, setCollapsed] = useState(false);
  const [menus, setMenus] = useState<OaMenuItem[]>([]);
  const [navigationLoaded, setNavigationLoaded] = useState(false);
  const [selectedMenu, setSelectedMenu] = useState<OaMenuItem>(dashboardMenu);
  const [appearanceOpen, setAppearanceOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [aiPrompt, setAiPrompt] = useState('');
  const [themeName, setThemeName] = useState(() => readStorage('workmeta-oa-theme', 'enterprise-blue'));
  const [aiMiniEnabled, setAiMiniEnabled] = useState(() => readStorage('workmeta-oa-ai-mini-enabled', 'false') === 'true');
  const [wallpaper, setWallpaper] = useState<string | null>(() => {
    const saved = readStorage('workmeta-oa-wallpaper', '');
    return saved || null;
  });
  const [wallpaperOpacity, setWallpaperOpacity] = useState(() => Number(readStorage('workmeta-oa-wallpaper-opacity', '0.28')));
  const [wallpaperBlur, setWallpaperBlur] = useState(() => Number(readStorage('workmeta-oa-wallpaper-blur', '4')));
  const [auditItems, setAuditItems] = useState<Array<{ color: string; children: string }>>([]);

  const currentTheme = useMemo(() => themes.find((theme) => theme.name === themeName) || themes[0], [themeName]);

  useEffect(() => {
    let active = true;
    if (!user) return;
    setNavigationLoaded(false);
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
        message.error(error instanceof Error ? error.message : '导航菜单加载失败');
      });
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
    const fallback = findMenu('dashboard', menus) || firstPage(menus);
    if (fallback) {
      setSelectedMenu(fallback);
      if (currentPageId !== fallback.id) router.replace(fallback.path || `/oa/${fallback.id}`);
    }
  }, [currentPageId, menus, navigationLoaded, router]);

  useEffect(() => {
    document.title = `AI WorkMate OA - ${selectedMenu.name}`;
  }, [selectedMenu.name]);

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
    setAuditItems((prev) => [{ color: currentTheme.primary, children: `${new Date().toLocaleTimeString()} ${text}` }, ...prev].slice(0, 6));
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
      <AntApp>
        <div className={`oa-shell ${collapsed ? 'oa-shell-collapsed' : ''} ${wallpaper ? 'oa-has-wallpaper' : ''} ${selectedMenu.id === 'ai-workspace' ? 'oa-chat-page' : ''}`}>
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
                router.push(menu.path || `/oa/${menu.id}`);
                message.info(`已切换到：${menu.name}`);
              }}
            />
            <Layout>
              <Topbar
                role={role}
                pageTitle={selectedMenu.name}
                onOpenAppearance={() => setAppearanceOpen(true)}
                onOpenAi={openAi}
              />
              <Content className={`oa-content ${selectedMenu.id === 'ai-workspace' ? 'oa-chat-content' : ''}`}>
                <div key={selectedMenu.id} className="oa-page-transition">
                  {selectedMenu.componentKey === 'AI_WORKSPACE' ? (
                    <AiChatWorkspace role={role} />
                  ) : selectedMenu.componentKey === 'ACCESS_CONTROL' ? (
                    <AccessControlPage />
                  ) : (
                    <Dashboard
                      role={role}
                      pageId={selectedMenu.id}
                      pageTitle={selectedMenu.name}
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
            icon={<RobotOutlined />}
            description="AI"
            tooltip="打开 AI 操作面板"
            onClick={() => openAi()}
          />}

          {aiMiniEnabled && selectedMenu.id !== 'ai-workspace' && <AiMiniPanel onOpenAi={openAi} />}

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
            pageTitle={selectedMenu.name}
            initialPrompt={aiPrompt}
            onClose={() => setAiOpen(false)}
            onExecuted={addAudit}
          />}
        </div>
      </AntApp>
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
