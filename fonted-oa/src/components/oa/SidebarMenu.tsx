'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SearchOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import { Button, Input, Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import type { OaMenuItem } from '@/types/oa';
import { OaIcon, resolveOaMenuIcon } from '@/components/OaIcon';

const { Sider } = Layout;

interface SidebarMenuProps {
  menus: OaMenuItem[];
  selectedKey: string;
  initialSelectedKey: string;
  collapsed: boolean;
  onCollapse: (collapsed: boolean) => void;
  onSelect: (menu: OaMenuItem) => void;
}

// 用 routeKey 作为 i18n key 翻译菜单名，未配置时回退到后端返回的 name。
// 这样切换语言时菜单项即时翻译，不依赖后端数据库存储多语言。
function toMenuItems(menus: OaMenuItem[], t: TFunction): MenuProps['items'] {
  return menus.map((menu) => {
    const hasChildren = menu.type !== 'page' && Boolean(menu.children?.length);
    const iconName = resolveOaMenuIcon(menu.id, menu.icon);
    return {
      key: menu.id,
      icon: iconName ? <OaIcon name={iconName} size={18} /> : undefined,
      label: t(`oa.menu.${menu.id}`, { defaultValue: menu.name }),
      children: hasChildren ? toMenuItems(menu.children || [], t) : undefined,
    };
  });
}

function findMenu(menuId: string, menus: OaMenuItem[]): OaMenuItem | undefined {
  for (const menu of menus) {
    if (menu.id === menuId) return menu;
    const child = menu.children?.length ? findMenu(menuId, menu.children) : undefined;
    if (child) return child;
  }
  return undefined;
}

function findAncestorKeys(menuId: string, menus: OaMenuItem[], ancestors: string[] = []): string[] {
  for (const menu of menus) {
    if (menu.id === menuId) return ancestors;
    if (menu.children?.length) {
      const found = findAncestorKeys(menuId, menu.children, [...ancestors, menu.id]);
      if (found.length) return found;
    }
  }
  return [];
}

function isPageReload(): boolean {
  const navigation = window.performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined;
  return navigation?.type === 'reload';
}

// Flatten all page menus into a flat list for search
function flattenMenus(menus: OaMenuItem[]): OaMenuItem[] {
  return menus.flatMap((m) => [
    ...(m.type === 'page' ? [m] : []),
    ...flattenMenus(m.children || []),
  ]);
}

// Case-insensitive match against label and name
function menuMatches(menu: OaMenuItem, keyword: string, t: TFunction): boolean {
  const label = t(`oa.menu.${menu.id}`, { defaultValue: menu.name });
  const lower = keyword.toLowerCase();
  return label.toLowerCase().includes(lower) || menu.name.toLowerCase().includes(lower);
}

export default function SidebarMenu({
  menus,
  selectedKey,
  initialSelectedKey,
  collapsed,
  onCollapse,
  onSelect,
}: SidebarMenuProps) {
  const { t } = useTranslation();
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const initialized = useRef(false);
  const lastSelectedKey = useRef(initialSelectedKey);
  const [searchExpanded, setSearchExpanded] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');
  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (initialized.current || !menus.length) return;
    setOpenKeys(isPageReload() ? findAncestorKeys(initialSelectedKey, menus) : []);
    lastSelectedKey.current = initialSelectedKey;
    initialized.current = true;
  }, [initialSelectedKey, menus]);

  useEffect(() => {
    if (!initialized.current || selectedKey === lastSelectedKey.current) return;
    setOpenKeys((current) => Array.from(new Set([
      ...current,
      ...findAncestorKeys(selectedKey, menus),
    ])));
    lastSelectedKey.current = selectedKey;
  }, [menus, selectedKey]);

  const changeOpenKeys: MenuProps['onOpenChange'] = (keys) => {
    setOpenKeys(keys.map(String));
  };

  const toggleSearch = useCallback(() => {
    if (collapsed) {
      onCollapse(false);
      setSearchExpanded(true);
      setTimeout(() => searchInputRef.current?.focus(), 140);
      return;
    }
    setSearchExpanded((prev) => {
      const next = !prev;
      if (next) {
        setTimeout(() => searchInputRef.current?.focus(), 80);
      } else {
        setSearchKeyword('');
      }
      return next;
    });
  }, [collapsed, onCollapse]);

  const matchedMenus = searchKeyword.trim()
    ? flattenMenus(menus).filter((m) => menuMatches(m, searchKeyword.trim(), t))
    : [];

  const handleSearchResultClick = useCallback((menu: OaMenuItem) => {
    onSelect(menu);
    setSearchExpanded(false);
    setSearchKeyword('');
  }, [onSelect]);

  return (
    <Sider
      className="oa-sider"
      width={268}
      collapsedWidth={80}
      collapsible
      collapsed={collapsed}
      trigger={null}
    >
      <div className={`oa-sider-brand ${collapsed ? 'is-collapsed' : 'is-expanded'}`}>
        <span className="oa-logo">
          <OaIcon name="brand" size={30} title={t('oa.sidebar.brand')} />
        </span>
        <div className="oa-sider-brand-text">
          <strong>{t('oa.sidebar.brand')}</strong>
          <small>{t('oa.sidebar.brandSub')}</small>
        </div>
      </div>

      <Button
        className="oa-collapse-btn"
        type="text"
        icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
        onClick={() => onCollapse(!collapsed)}
        block
      >
        <span className="oa-collapse-btn-text">{!collapsed && t('oa.sidebar.collapse')}</span>
      </Button>

      {searchExpanded && !collapsed ? (
        <div className="oa-sidebar-search-expanded">
          <Input
            ref={searchInputRef as any}
            className="oa-sidebar-search-input"
            prefix={<SearchOutlined className="oa-sidebar-search-icon" />}
            suffix={<CloseOutlined className="oa-sidebar-search-close" onClick={toggleSearch} />}
            placeholder={t('oa.sidebar.searchPlaceholder')}
            allowClear
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            onPressEnter={() => {
              if (matchedMenus.length) handleSearchResultClick(matchedMenus[0]);
            }}
          />
          {matchedMenus.length > 0 && (
            <div className="oa-sidebar-search-results">
              {matchedMenus.map((menu) => {
                const resultIcon = resolveOaMenuIcon(menu.id, menu.icon);
                return (
                  <div
                    key={menu.id}
                    className={`oa-sidebar-search-result-item ${menu.id === selectedKey ? 'is-active' : ''}`}
                    onClick={() => handleSearchResultClick(menu)}
                  >
                    {resultIcon && <span className="oa-sidebar-search-result-icon"><OaIcon name={resultIcon} size={14} /></span>}
                    <span>{t(`oa.menu.${menu.id}`, { defaultValue: menu.name })}</span>
                  </div>
                );
              })}
            </div>
          )}
          {searchKeyword.trim() && matchedMenus.length === 0 && (
            <div className="oa-sidebar-search-empty">
              {t('oa.sidebar.searchEmpty')}
            </div>
          )}
        </div>
      ) : (
        <Button
          className="oa-collapse-btn"
          type="text"
          icon={<SearchOutlined />}
          onClick={toggleSearch}
          block
        >
          <span className="oa-collapse-btn-text">{!collapsed && t('oa.sidebar.search')}</span>
        </Button>
      )}

      <Menu
        mode="inline"
        theme="dark"
        inlineCollapsed={collapsed}
        triggerSubMenuAction="click"
        selectedKeys={[selectedKey]}
        openKeys={collapsed ? undefined : openKeys}
        onOpenChange={collapsed ? undefined : changeOpenKeys}
        items={toMenuItems(menus, t)}
        onClick={({ key }) => {
          const menu = findMenu(String(key), menus);
          if (menu) onSelect(menu);
        }}
      />
    </Sider>
  );
}

