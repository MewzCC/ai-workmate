'use client';

import { useEffect, useLayoutEffect, useRef, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
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
  onReorder?: (sourceId: string, targetId: string) => void;
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
  onReorder,
}: PageTabBarProps) {
  const { t } = useTranslation();
  const navRef = useRef<HTMLElement>(null);
  const [hasOverflow, setHasOverflow] = useState(false);
  // 被拖动的标签 id（仅用于触发 label 上的 class）
  const [draggingId, setDraggingId] = useState<string | null>(null);
  // 拖动起始鼠标 X
  const dragStartRef = useRef<number | null>(null);
  // 被拖动的 .ant-tabs-tab 原始元素引用
  const dragTabRef = useRef<HTMLElement | null>(null);
  // 拖动开始时所有 tab 元素的位置快照（含宽度），用于计算让路偏移
  const tabRectsRef = useRef<Array<{ left: number; width: number }>>([]);
  // 被拖动标签的原始索引和当前目标索引
  const dragOriginalIndexRef = useRef<number>(-1);
  const targetIndexRef = useRef<number>(-1);
  // 单位移动距离（拖动标签宽度 + 与相邻标签的间距）
  const unitDistanceRef = useRef<number>(0);
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

  /**
   * 纯 transform 拖动方案（浏览器标签页风格）：
   * - 拖动期间不改变 tabs 数组顺序，零 React 重渲染
   * - 原标签 transform translateX 跟随鼠标（仅水平），提升 z-index + 阴影
   * - 根据鼠标位置计算目标索引，给其他标签设置 transform 让路（左移或右移一个单位）
   * - 其他标签通过 CSS transition 平滑避让
   * - drop 时才调用一次 onReorder 真正更新顺序
   */
  const collectTabEls = useCallback((): HTMLElement[] => {
    const navList = navRef.current?.querySelector('.ant-tabs-nav-list');
    if (!navList) return [];
    return Array.from(navList.querySelectorAll<HTMLElement>('.ant-tabs-tab'));
  }, []);

  const handleDragStart = useCallback(
    (e: React.DragEvent, tabId: string, tabEl: HTMLElement) => {
      if (!onReorder) return;
      // 隐藏浏览器默认拖拽预览图
      const dragImg = document.createElement('div');
      dragImg.style.width = '1px';
      dragImg.style.height = '1px';
      dragImg.style.opacity = '0';
      document.body.appendChild(dragImg);
      e.dataTransfer.setDragImage(dragImg, 0, 0);
      setTimeout(() => document.body.removeChild(dragImg), 0);

      e.dataTransfer.effectAllowed = 'move';

      const tabEls = collectTabEls();
      const index = tabEls.indexOf(tabEl);
      const rect = tabEl.getBoundingClientRect();

      // 快照所有标签的初始位置（含宽度）
      tabRectsRef.current = tabEls.map((el) => {
        const r = el.getBoundingClientRect();
        return { left: r.left, width: r.width };
      });

      // 计算单位移动距离（拖动标签与相邻标签的间距，含宽度+gap）
      let unit = rect.width;
      if (index < tabEls.length - 1) {
        const nextRect = tabEls[index + 1].getBoundingClientRect();
        unit = nextRect.left - rect.left;
      } else if (index > 0) {
        const prevRect = tabEls[index - 1].getBoundingClientRect();
        unit = rect.left - prevRect.left;
      }
      unitDistanceRef.current = unit;

      dragOriginalIndexRef.current = index;
      targetIndexRef.current = index;
      dragStartRef.current = e.clientX;
      dragTabRef.current = tabEl;
      tabEl.classList.add('is-tab-dragging');
      setDraggingId(tabId);
    },
    [onReorder, collectTabEls],
  );

  // dragover 仅用于允许 drop，不触发重排
  const handleDragOver = useCallback((e: React.DragEvent) => {
    if (!draggingId) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }, [draggingId]);

  const handleDrag = useCallback(
    (e: React.DragEvent) => {
      if (!draggingId || dragStartRef.current === null || !dragTabRef.current) return;
      // 忽略拖动结束时的 0,0 坐标
      if (e.clientX === 0 && e.clientY === 0) return;

      const dx = e.clientX - dragStartRef.current;
      // 原标签跟随鼠标
      dragTabRef.current.style.transform = `translateX(${dx}px)`;

      // 计算拖动标签当前的视口中心 X
      const originRect = tabRectsRef.current[dragOriginalIndexRef.current];
      if (!originRect) return;
      const currentCenterX = originRect.left + dx + originRect.width / 2;

      // 根据中心 X 与其他标签中线的比较，确定目标插入索引
      let targetIndex = dragOriginalIndexRef.current;
      tabRectsRef.current.forEach((rect, i) => {
        if (i === dragOriginalIndexRef.current) return;
        const tabCenterX = rect.left + rect.width / 2;
        if (i < dragOriginalIndexRef.current && currentCenterX < tabCenterX) {
          targetIndex = Math.min(targetIndex, i);
        }
        if (i > dragOriginalIndexRef.current && currentCenterX > tabCenterX) {
          targetIndex = Math.max(targetIndex, i);
        }
      });

      // 仅在目标索引变化时才更新 DOM（减少写操作）
      if (targetIndex === targetIndexRef.current) return;
      targetIndexRef.current = targetIndex;

      const unit = unitDistanceRef.current;
      const original = dragOriginalIndexRef.current;
      const tabEls = collectTabEls();
      tabEls.forEach((el, i) => {
        if (i === original) return;
        let offset = 0;
        if (targetIndex > original) {
          // 向右拖：originalIndex < i <= targetIndex 的标签左移
          if (i > original && i <= targetIndex) offset = -unit;
        } else if (targetIndex < original) {
          // 向左拖：targetIndex <= i < originalIndex 的标签右移
          if (i >= targetIndex && i < original) offset = unit;
        }
        el.style.transform = offset !== 0 ? `translateX(${offset}px)` : '';
      });
    },
    [draggingId, collectTabEls],
  );

  const cleanupDrag = useCallback(() => {
    // 仅重置 ref 和 state，不清除 DOM transform；
    // DOM 清除在 useEffect 中于 React 重渲染后执行，避免 transform
    // 提前清除导致其他标签先跳回旧 DOM 位置再被重渲染到新位置。
    dragTabRef.current = null;
    dragStartRef.current = null;
    dragOriginalIndexRef.current = -1;
    targetIndexRef.current = -1;
    tabRectsRef.current = [];
    setDraggingId(null);
  }, []);

  const handleDragEnd = useCallback(() => {
    // dragend 总是触发：若目标索引变化则执行一次 reorder
    if (
      onReorder &&
      draggingId &&
      targetIndexRef.current !== dragOriginalIndexRef.current &&
      targetIndexRef.current >= 0 &&
      targetIndexRef.current < tabs.length
    ) {
      const targetTab = tabs[targetIndexRef.current];
      if (targetTab) onReorder(draggingId, targetTab.id);
    }
    cleanupDrag();
  }, [onReorder, draggingId, tabs, cleanupDrag]);

  const handleDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
    },
    [],
  );

  // 记录上一轮的 draggingId，用于检测拖动结束
  const prevDraggingIdRef = useRef<string | null>(null);
  // 关键：拖动结束时视觉状态已经是正确的（其他标签已让路，被拖动标签在目标位置）。
  // React 重排 DOM 后，transform 还在会导致视觉错乱（旧 transform + 新 DOM 位置）。
  // 必须在 paint 前清除 transform，且禁用过渡避免从错误位置动画。
  // useLayoutEffect 在 DOM commit 后、paint 前同步执行，可避免用户看到中间错误状态。
  useLayoutEffect(() => {
    const prev = prevDraggingIdRef.current;
    if (prev !== null && draggingId === null) {
      const tabEls = collectTabEls();
      // 1. 禁用过渡
      tabEls.forEach((el) => {
        el.style.transition = 'none';
      });
      // 2. 清除 transform 和拖动 class（瞬间生效，不动画）
      tabEls.forEach((el) => {
        el.style.transform = '';
        el.classList.remove('is-tab-dragging');
      });
      // 3. 强制 reflow，让上面的清除生效
      navRef.current?.offsetHeight;
      // 4. 恢复过渡，供后续交互使用
      tabEls.forEach((el) => {
        el.style.transition = '';
      });
    }
    prevDraggingIdRef.current = draggingId;
  }, [draggingId, tabs, collectTabEls]);

  const items: TabsProps['items'] = tabs.map((tab) => {
    const iconName = resolveOaMenuIcon(tab.id, tab.icon);
    const isPinned = tab.id === pinnedKey;
    const canDrag = !!onReorder && !isPinned;
    const isDragging = draggingId === tab.id;
    const tabLabel = t(`oa.menu.${tab.id}`, { defaultValue: tab.name });
    return {
      key: tab.id,
      closable: tab.id !== pinnedKey,
      label: (
        <span
          className={`oa-page-tab-label${isDragging ? ' is-dragging' : ''}`}
          title={tabLabel}
          data-tab-key={tab.id}
          draggable={canDrag}
          onDragStart={
            canDrag
              ? (e) => {
                  const tabEl = (e.currentTarget as HTMLElement).closest('.ant-tabs-tab') as HTMLElement | null;
                  if (tabEl) handleDragStart(e, tab.id, tabEl);
                }
              : undefined
          }
          onDragOver={canDrag ? handleDragOver : undefined}
          onDrag={handleDrag}
          onDrop={handleDrop}
          onDragEnd={handleDragEnd}
        >
          {iconName ? <OaIcon name={iconName} size={15} /> : null}
          <span>{tabLabel}</span>
        </span>
      ),
    };
  });

  const menuItems: MenuProps['items'] = [
    {
      type: 'group',
      label: t('oa.tabs.switchPages'),
      children: tabs.map((tab) => {
        const iconName = resolveOaMenuIcon(tab.id, tab.icon);
        return {
          key: `navigate:${tab.id}`,
          icon: iconName ? <OaIcon name={iconName} /> : undefined,
          label: t(`oa.menu.${tab.id}`, { defaultValue: tab.name }),
          disabled: tab.id === activeKey,
        };
      }),
    },
    { type: 'divider' },
    {
      key: 'refresh',
      icon: <OaIcon name="reload" />,
      label: t('oa.tabs.refresh'),
    },
    { type: 'divider' },
    {
      key: 'close-current',
      icon: <OaIcon name="delete" />,
      label: t('oa.tabs.closeCurrent'),
      disabled: !activeTab || activeTab.id === pinnedKey,
    },
    {
      key: 'close-others',
      icon: <OaIcon name="copy" />,
      label: t('oa.tabs.closeOthers'),
      disabled: tabs.length <= 1,
    },
    {
      key: 'close-all',
      icon: <OaIcon name="logout" />,
      label: t('oa.tabs.closeAll'),
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
    <nav ref={navRef} className="oa-page-tabs" aria-label={t('oa.tabs.openPages')}>
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
                    aria-label={t('oa.tabs.prevPage')}
                    title={t('oa.tabs.prevPage')}
                  />
                  <Button
                    type="text"
                    className="oa-page-tabs-scroll"
                    icon={<OaIcon name="next" />}
                    disabled={activeIndex < 0 || activeIndex >= tabs.length - 1}
                    onClick={() => navigateByOffset(1)}
                    aria-label={t('oa.tabs.nextPage')}
                    title={t('oa.tabs.nextPage')}
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
                  aria-label={t('oa.tabs.manage')}
                  title={t('oa.tabs.manage')}
                />
              </Dropdown>
            </Space>
          ),
        }}
      />
    </nav>
  );
}
