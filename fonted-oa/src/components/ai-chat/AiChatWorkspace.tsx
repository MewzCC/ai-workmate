'use client';

import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Drawer, Dropdown, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { message } from '@/lib/antdMessage';
import { MenuUnfoldOutlined } from '@ant-design/icons';
import { useAiChatStore } from '@/store/aiChatStore';
import { knowledgeApi, type KnowledgeBase } from '@/lib/knowledgeApi';
import type { OaRole } from '@/types/oa';
import type { AiModelId } from '@/config/aiModels';
import ChatSidebar from './ChatSidebar';
import ChatWindow from './ChatWindow';
import { OaIcon } from '@/components/OaIcon';
import { useRouter } from '@/lib/nextCompat';

const SIDEBAR_COLLAPSED_KEY = 'workmeta-ai-chat-sidebar-collapsed';
/** 收起侧栏时最近会话快捷跳转的上限（LRU，最近使用优先） */
const RECENT_LIMIT = 10;
/** 下拉菜单中会话标题的最大展示长度 */
const RECENT_TITLE_MAX = 18;

interface AiChatWorkspaceProps {
  role: OaRole;
}

export default function AiChatWorkspace({ role }: AiChatWorkspaceProps) {
  const { t } = useTranslation();
  const router = useRouter();
  const store = useAiChatStore();
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [kbOptions, setKbOptions] = useState<KnowledgeBase[]>([]);
  const active = useMemo(
    () => store.conversations.find((item) => item.id === store.activeId),
    [store.activeId, store.conversations],
  );

  // 最近会话（LRU）：按 updatedAt 降序取前 RECENT_LIMIT 条。
  // 后端会话列表本身按 updated_at 降序返回，此处防御性重排；再次对话时后端会刷新 updatedAt。
  const recentConversations = useMemo(
    () => [...store.conversations]
      .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
      .slice(0, RECENT_LIMIT),
    [store.conversations],
  );

  const recentMenuItems: MenuProps['items'] = recentConversations.length
    ? recentConversations.map((item) => {
        const title = item.title?.trim() || t('chat.newConversation');
        return {
          key: String(item.id),
          label: title.length > RECENT_TITLE_MAX ? `${title.slice(0, RECENT_TITLE_MAX)}…` : title,
        };
      })
    : [{ key: 'empty', label: t('chat.recentsEmpty'), disabled: true }];

  useEffect(() => {
    setSidebarCollapsed(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true');
    store.hydrateSettings().catch((error) => {
      message.error(error instanceof Error ? error.message : t('errors.requestFailed'));
    });
    store.loadConversations().catch((error) => {
      message.error(error instanceof Error ? error.message : t('chat.conversationsLoadFailed'));
    });
    knowledgeApi.listBases()
      .then(setKbOptions)
      .catch(() => setKbOptions([]));
  // Store actions are stable in Zustand.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateSidebarCollapsed = (collapsed: boolean) => {
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(collapsed));
    setSidebarCollapsed(collapsed);
  };

  const sidebar = (
    <ChatSidebar
      conversations={store.conversations}
      activeId={store.activeId}
      loading={store.loading}
      messagesByConversation={store.messagesByConversation}
      previewByConversation={store.previewByConversation}
      generatingIds={store.generatingIds}
      onSearch={store.loadConversations}
      onNew={() => store.newConversation()}
      onSelect={(id) => {
        store.selectConversation(id);
        setMobileSessionsOpen(false);
      }}
      onRename={store.rename}
      onDelete={store.remove}
      onSettings={() => router.push('/oa/system-config')}
      onCollapse={() => updateSidebarCollapsed(true)}
    />
  );

  return (
    <div
      className={`ai-workspace ${sidebarCollapsed ? 'ai-workspace-sidebar-collapsed' : ''}`}
      data-role={role}
    >
      <div className="ai-desktop-sidebar">
        {sidebarCollapsed ? (
          <aside className="ai-chat-sidebar-rail" aria-label={t('chat.sidebarRailAriaLabel')}>
            <Tooltip title={t('chat.expandSidebar')} placement="right">
              <Button
                type="text"
                icon={<MenuUnfoldOutlined />}
                aria-label={t('chat.expandSidebar')}
                onClick={() => updateSidebarCollapsed(false)}
              />
            </Tooltip>
            <Tooltip title={t('chat.newChat')} placement="right">
              <Button type="text" icon={<OaIcon name="add" />} aria-label={t('chat.newChat')} onClick={() => void store.newConversation()} />
            </Tooltip>
            <Dropdown
              menu={{
                items: recentMenuItems,
                onClick: ({ key }) => {
                  if (key === 'empty') return;
                  void store.selectConversation(Number(key));
                },
              }}
              trigger={['click']}
              placement="right"
            >
              <Tooltip title={t('chat.recents')} placement="right">
                <Button type="text" icon={<OaIcon name="history" />} aria-label={t('chat.recents')} />
              </Tooltip>
            </Dropdown>
            <Tooltip title={t('chat.settings')} placement="right">
              <Button
                className="ai-sidebar-rail-settings"
                type="text"
                icon={<OaIcon name="settings" />}
                aria-label={t('chat.settings')}
                onClick={() => router.push('/oa/system-config')}
              />
            </Tooltip>
          </aside>
        ) : sidebar}
      </div>
      <ChatWindow
        title={active?.title || t('chat.newConversation')}
        model={store.settings.model}
        kbId={store.settings.kbId}
        kbOptions={kbOptions}
        messages={store.activeId ? store.messagesByConversation[store.activeId] || [] : []}
        pending={store.activeId ? store.pendingAttachments[store.activeId] || [] : []}
        uploading={store.activeId ? store.uploading[store.activeId] || [] : []}
        generating={store.activeId ? store.generatingIds.includes(store.activeId) : false}
        onOpenSessions={() => setMobileSessionsOpen(true)}
        onUpload={store.upload}
        onRemoveAttachment={store.removePendingAttachment}
        onSend={(content) => store.send(content)}
        onStop={() => store.activeId && store.stop(store.activeId)}
        onModelChange={(model: AiModelId) => void store.updateSettings({ ...store.settings, model })
          .catch((error) => message.error(error instanceof Error ? error.message : t('errors.requestFailed')))}
        onKbChange={(kbId: number | null) => void store.updateSettings({ ...store.settings, kbId })}
      />
      <Drawer
        title={t('chat.historySessions')}
        placement="left"
        size="default"
        styles={{ wrapper: { width: 320 } }}
        open={mobileSessionsOpen}
        onClose={() => setMobileSessionsOpen(false)}
      >
        <ChatSidebar
          conversations={store.conversations}
          activeId={store.activeId}
          loading={store.loading}
          messagesByConversation={store.messagesByConversation}
          previewByConversation={store.previewByConversation}
          generatingIds={store.generatingIds}
          onSearch={store.loadConversations}
          onNew={() => store.newConversation()}
          onSelect={(id) => {
            store.selectConversation(id);
            setMobileSessionsOpen(false);
          }}
          onRename={store.rename}
          onDelete={store.remove}
          onSettings={() => router.push('/oa/system-config')}
        />
      </Drawer>
    </div>
  );
}
