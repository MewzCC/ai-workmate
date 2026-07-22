'use client';

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
import type { OaMenuItem, OaRole } from '@/types/oa';
import { filterMenusByRole } from '@/mock/oaPermissions';

const { Sider } = Layout;

const iconMap = {
  DashboardOutlined: <DashboardOutlined />,
  ApartmentOutlined: <ApartmentOutlined />,
  ApiOutlined: <ApiOutlined />,
  SettingOutlined: <SettingOutlined />,
  RobotOutlined: <RobotOutlined />,
};

interface SidebarMenuProps {
  role: OaRole;
  selectedKey: string;
  collapsed: boolean;
  onCollapse: (collapsed: boolean) => void;
  onSelect: (menu: OaMenuItem) => void;
}

function toMenuItems(menus: OaMenuItem[]): MenuProps['items'] {
  return menus.map((menu) => ({
    key: menu.id,
    icon: menu.icon ? iconMap[menu.icon as keyof typeof iconMap] : undefined,
    label: menu.name,
    children: menu.children ? toMenuItems(menu.children) : undefined,
  }));
}

function findMenu(menuId: string, menus: OaMenuItem[]): OaMenuItem | undefined {
  for (const menu of menus) {
    if (menu.id === menuId) return menu;
    const child = menu.children ? findMenu(menuId, menu.children) : undefined;
    if (child) return child;
  }
  return undefined;
}

export default function SidebarMenu({ role, selectedKey, collapsed, onCollapse, onSelect }: SidebarMenuProps) {
  const menus = filterMenusByRole(role);

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
        selectedKeys={[selectedKey]}
        defaultOpenKeys={collapsed ? [] : ['workspace', 'business', 'approval']}
        items={toMenuItems(menus)}
        onClick={({ key }) => {
          const menu = findMenu(String(key), menus);
          if (menu) onSelect(menu);
        }}
      />
    </Sider>
  );
}
