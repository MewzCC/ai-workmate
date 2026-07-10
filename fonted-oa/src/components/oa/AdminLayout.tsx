'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ConfigProvider, FloatButton, Layout, App as AntApp, message, theme as antdTheme } from 'antd';
import { RobotOutlined } from '@ant-design/icons';
import type { OaMenuItem, OaRole, OaTheme } from '@/types/oa';
import { filterMenusByRole, findMenu } from '@/mock/oaPermissions';
import Dashboard from './Dashboard';
import SidebarMenu from './SidebarMenu';
import Topbar from './Topbar';
import AppearanceDrawer from './AppearanceDrawer';
import AIOperationDrawer from './AIOperationDrawer';
import AiMiniPanel from './AiMiniPanel';

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

interface AdminLayoutProps {
  initialPageId?: string;
}

export default function AdminLayout({ initialPageId = 'dashboard' }: AdminLayoutProps) {
  const router = useRouter();
  const [role, setRole] = useState<OaRole>('super_admin');
  const [collapsed, setCollapsed] = useState(false);
  const [selectedMenu, setSelectedMenu] = useState<OaMenuItem>(() => findMenu(initialPageId) || dashboardMenu);
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
    const visibleMenu = findMenu(initialPageId, filterMenusByRole(role));
    setSelectedMenu(visibleMenu || dashboardMenu);
  }, [initialPageId, role]);

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
    if (wallpaper) {
      window.localStorage.setItem('workmeta-oa-wallpaper', wallpaper);
    } else {
      window.localStorage.removeItem('workmeta-oa-wallpaper');
    }
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
      }}
    >
      <AntApp>
        <div
          className={`oa-shell ${collapsed ? 'oa-shell-collapsed' : ''}`}
          style={wallpaper ? {
            backgroundImage: `linear-gradient(rgba(255,255,255,${1 - wallpaperOpacity}), rgba(255,255,255,${1 - wallpaperOpacity})), url(${wallpaper})`,
            backdropFilter: `blur(${wallpaperBlur}px)`,
          } : undefined}
        >
          <Layout className="oa-layout">
            <SidebarMenu
              role={role}
              selectedKey={selectedMenu.id}
              collapsed={collapsed}
              onCollapse={setCollapsed}
              onSelect={(menu) => {
                router.push(`/oa/${menu.id}`);
                message.info(`已切换到：${menu.name}`);
              }}
            />
            <Layout>
              <Topbar
                role={role}
                pageTitle={selectedMenu.name}
                onRoleChange={(nextRole) => {
                  setRole(nextRole);
                  router.push('/oa/dashboard');
                  message.success('角色已切换，菜单和权限已刷新');
                }}
                onOpenAppearance={() => setAppearanceOpen(true)}
                onOpenAi={openAi}
              />
              <Content className="oa-content">
                <Dashboard
                  role={role}
                  pageId={selectedMenu.id}
                  pageTitle={selectedMenu.name}
                  primaryColor={currentTheme.primary}
                  auditItems={auditItems}
                  onOpenAi={openAi}
                  onAddAudit={addAudit}
                />
              </Content>
            </Layout>
          </Layout>

          <FloatButton
            type="primary"
            icon={<RobotOutlined />}
            description="AI"
            tooltip="打开 AI 操作面板"
            onClick={() => openAi()}
          />

          {aiMiniEnabled && <AiMiniPanel onOpenAi={openAi} />}

          <AppearanceDrawer
            open={appearanceOpen}
            themes={themes}
            currentTheme={currentTheme.name}
            aiMiniEnabled={aiMiniEnabled}
            wallpaperOpacity={wallpaperOpacity}
            wallpaperBlur={wallpaperBlur}
            onClose={() => setAppearanceOpen(false)}
            onThemeChange={setThemeName}
            onAiMiniChange={setAiMiniEnabled}
            onWallpaperChange={setWallpaper}
            onWallpaperOpacityChange={setWallpaperOpacity}
            onWallpaperBlurChange={setWallpaperBlur}
          />

          <AIOperationDrawer
            open={aiOpen}
            role={role}
            pageId={selectedMenu.id}
            pageTitle={selectedMenu.name}
            initialPrompt={aiPrompt}
            onClose={() => setAiOpen(false)}
            onExecuted={addAudit}
          />
        </div>
      </AntApp>
    </ConfigProvider>
  );
}
