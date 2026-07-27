'use client';

import { useEffect, useRef, useState } from 'react';
import { Button, Dropdown, Space, Tabs } from 'antd';
import type { MenuProps, TabsProps } from 'antd';
import { OaIcon, resolveOaMenuIcon } from '@/components/OaIcon';

export interface OaPageTab {
  id: string;
  name: string;
  path: string;
  icon?: string;
}

interface PageTabBarProps {
  tabs: OaPageTab[];
  activeKey: string;
  pinnedKey?: string;
  onNavigate: (tab: OaPageTab) => void;
  onClose: (tabId: string) => void;
  onCloseOthers: () => void;
  onCloseAll: () => void;
  onRefresh: () => void;
}

export default function PageTabBar({
  tabs,
  activeKey,
  pinnedKey,
  onNavigate,
  onClose,
  onCloseOthers,
  onCloseAll,
  onRefresh,
}: PageTabBarProps) {
  const navRef = useRef<HTMLElement>(null);
  const [hasOverflow, setHasOverflow] = useState(false);
  const activeTab = tabs.find((tab) => tab.id === activeKey);
  const activeIndex = tabs.findIndex((tab) => tab.id === activeKey);
  const closeableTabs = tabs.filter((tab) => tab.id !== pinnedKey);

  useEffect(() => {
    const root = navRef.current;
    const navWrap = root?.querySelector<HTMLElement>('.ant-tabs-nav-wrap');
    const navList = root?.querySelector<HTMLElement>('.ant-tabs-nav-list');
    if (!root || !navWrap || !navList) return;

    const measureOverflow = () => {
      setHasOverflow(navList.scrollWidth > navWrap.clientWidth + 1);
    };
    const observer = new ResizeObserver(measureOverflow);
    observer.observe(root);
    observer.observe(navWrap);
    observer.observe(navList);
    measureOverflow();

    return () => observer.disconnect();
  }, [tabs.length]);

  const items: TabsProps['items'] = tabs.map((tab) => {
    const iconName = resolveOaMenuIcon(tab.id, tab.icon);
    return {
      key: tab.id,
      closable: tab.id !== pinnedKey,
      label: (
        <span className="oa-page-tab-label" title={tab.name}>
          {iconName ? <OaIcon name={iconName} size={15} /> : null}
          <span>{tab.name}</span>
        </span>
      ),
    };
  });

  const menuItems: MenuProps['items'] = [
    {
      type: 'group',
      label: '切换已打开页面',
      children: tabs.map((tab) => {
        const iconName = resolveOaMenuIcon(tab.id, tab.icon);
        return {
          key: `navigate:${tab.id}`,
          icon: iconName ? <OaIcon name={iconName} /> : undefined,
          label: tab.name,
          disabled: tab.id === activeKey,
        };
      }),
    },
    { type: 'divider' },
    {
      key: 'refresh',
      icon: <OaIcon name="reload" />,
      label: '刷新当前页面',
    },
    { type: 'divider' },
    {
      key: 'close-current',
      icon: <OaIcon name="delete" />,
      label: '关闭当前页面',
      disabled: !activeTab || activeTab.id === pinnedKey,
    },
    {
      key: 'close-others',
      icon: <OaIcon name="copy" />,
      label: '关闭其他页面',
      disabled: tabs.length <= 1,
    },
    {
      key: 'close-all',
      icon: <OaIcon name="logout" />,
      label: '关闭全部页面',
      disabled: closeableTabs.length === 0,
    },
  ];

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key.startsWith('navigate:')) {
      const targetId = key.slice('navigate:'.length);
      const targetTab = tabs.find((tab) => tab.id === targetId);
      if (targetTab) onNavigate(targetTab);
    } else if (key === 'refresh') onRefresh();
    else if (key === 'close-current' && activeTab) onClose(activeTab.id);
    else if (key === 'close-others') onCloseOthers();
    else if (key === 'close-all') onCloseAll();
  };

  const navigateByOffset = (offset: -1 | 1) => {
    const targetTab = tabs[activeIndex + offset];
    if (targetTab) onNavigate(targetTab);
  };

  return (
    <nav ref={navRef} className="oa-page-tabs" aria-label="已打开页面">
      <Tabs
        type="editable-card"
        hideAdd
        activeKey={activeKey}
        items={items}
        removeIcon={<OaIcon name="delete" size={12} />}
        onChange={(key) => {
          const tab = tabs.find((item) => item.id === key);
          if (tab) onNavigate(tab);
        }}
        onEdit={(targetKey, action) => {
          if (action === 'remove') onClose(String(targetKey));
        }}
        tabBarExtraContent={{
          right: (
            <Space size={4} className="oa-page-tabs-controls">
              {hasOverflow ? (
                <>
                  <Button
                    type="text"
                    className="oa-page-tabs-scroll"
                    icon={<OaIcon name="previous" />}
                    disabled={activeIndex <= 0}
                    onClick={() => navigateByOffset(-1)}
                    aria-label="切换到上一个页面"
                    title="上一个页面"
                  />
                  <Button
                    type="text"
                    className="oa-page-tabs-scroll"
                    icon={<OaIcon name="next" />}
                    disabled={activeIndex < 0 || activeIndex >= tabs.length - 1}
                    onClick={() => navigateByOffset(1)}
                    aria-label="切换到下一个页面"
                    title="下一个页面"
                  />
                </>
              ) : null}
              <Dropdown
                menu={{ items: menuItems, onClick: handleMenuClick }}
                trigger={['click']}
                placement="bottomRight"
              >
                <Button
                  type="text"
                  className="oa-page-tabs-manage"
                  icon={<OaIcon name="more" />}
                  aria-label="切换或管理已打开页面"
                  title="切换或管理已打开页面"
                />
              </Dropdown>
            </Space>
          ),
        }}
      />
    </nav>
  );
}
