'use client';

import { useEffect, useRef, useState } from 'react';
import {
  ApartmentOutlined,
  ApiOutlined,
  DashboardOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import { Button, Layout, Menu } from 'antd';
import type { MenuProps } from 'antd';
import type { OaMenuItem } from '@/types/oa';

const { Sider } = Layout;

const iconMap = {
  DashboardOutlined: <DashboardOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  ApiOutlined: <ApiOutlined />,
  SettingOutlined: <SettingOutlined />,
  RobotOutlined: <RobotOutlined />,
};

interface SidebarMenuProps {
  menus: OaMenuItem[];
  selectedKey: string;
  initialSelectedKey: string;
  collapsed: boolean;
  onCollapse: (collapsed: boolean) => void;
  onSelect: (menu: OaMenuItem) => void;
}

function toMenuItems(menus: OaMenuItem[]): MenuProps['items'] {
  return menus.map((menu) => {
    const hasChildren = menu.type !== 'page' && Boolean(menu.children?.length);
    return {
      key: menu.id,
      icon: menu.icon ? iconMap[menu.icon as keyof typeof iconMap] : undefined,
      label: menu.name,
      children: hasChildren ? toMenuItems(menu.children || []) : undefined,
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
      <div className="oa-sider-brand">
        <span className="oa-logo">W</span>
        {!collapsed && (
          <div>
            <strong>WorkMate OA</strong>
            <small>Enterprise Console</small>
          </div>
        )}
      </div>

      <Button
        className="oa-collapse-btn"
        type="text"
        icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
        onClick={() => onCollapse(!collapsed)}
        block
      >
        {!collapsed && '收起菜单'}
      </Button>

      <Menu
        mode="inline"
        theme="dark"
        inlineCollapsed={collapsed}
        triggerSubMenuAction="click"
        selectedKeys={[selectedKey]}
        openKeys={collapsed ? undefined : openKeys}
        onOpenChange={collapsed ? undefined : changeOpenKeys}
        items={toMenuItems(menus)}
        onClick={({ key }) => {
          const menu = findMenu(String(key), menus);
          if (menu) onSelect(menu);
        }}
      />
    </Sider>
  );
}
