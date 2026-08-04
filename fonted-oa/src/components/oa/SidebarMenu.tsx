'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu } from 'antd';
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
