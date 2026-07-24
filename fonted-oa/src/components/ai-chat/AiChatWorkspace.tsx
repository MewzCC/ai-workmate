'use client';

import { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Tooltip, message } from 'antd';
import { MenuUnfoldOutlined, PlusOutlined, SettingOutlined } from '@ant-design/icons';
import { useAiChatStore } from '@/store/aiChatStore';
import type { OaRole } from '@/types/oa';
import type { AiModelId } from '@/config/aiModels';
import ChatSidebar from './ChatSidebar';
import ChatWindow from './ChatWindow';
import SettingsDialog from './SettingsDialog';

const SIDEBAR_COLLAPSED_KEY = 'workmeta-ai-chat-sidebar-collapsed';

interface AiChatWorkspaceProps {
  role: OaRole;
}

export default function AiChatWorkspace({ role }: AiChatWorkspaceProps) {
  const store = useAiChatStore();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const active = useMemo(
    () => store.conversations.find((item) => item.id === store.activeId),
    [store.activeId, store.conversations],
  );

  useEffect(() => {
    setSidebarCollapsed(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true');
    store.loadConversations().catch((error) => {
      message.error(error instanceof Error ? error.message : '会话加载失败');
    });
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
      onSearch={store.loadConversations}
      onNew={() => store.newConversation()}
      onSelect={(id) => {
        store.selectConversation(id);
        setMobileSessionsOpen(false);
      }}
      onRename={store.rename}
      onDelete={store.remove}
      onSettings={() => setSettingsOpen(true)}
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
          <aside className="ai-chat-sidebar-rail" aria-label="会话栏快捷操作">
            <Tooltip title="展开会话栏" placement="right">
              <Button
                type="text"
                icon={<MenuUnfoldOutlined />}
                aria-label="展开会话栏"
                onClick={() => updateSidebarCollapsed(false)}
              />
            </Tooltip>
            <Tooltip title="新建聊天" placement="right">
              <Button type="text" icon={<PlusOutlined />} aria-label="新建聊天" onClick={() => void store.newConversation()} />
            </Tooltip>
            <Tooltip title="设置" placement="right">
              <Button
                className="ai-sidebar-rail-settings"
                type="text"
                icon={<SettingOutlined />}
                aria-label="设置"
                onClick={() => setSettingsOpen(true)}
              />
            </Tooltip>
          </aside>
        ) : sidebar}
      </div>
      <ChatWindow
        title={active?.title || '新对话'}
        model={store.settings.model}
        messages={store.activeId ? store.messagesByConversation[store.activeId] || [] : []}
        pending={store.activeId ? store.pendingAttachments[store.activeId] || [] : []}
        generating={store.activeId ? store.generatingIds.includes(store.activeId) : false}
        onOpenSessions={() => setMobileSessionsOpen(true)}
        onUpload={store.upload}
        onRemoveAttachment={store.removePendingAttachment}
        onSend={async (content) => {
          if (!store.activeId) {
            const id = await store.newConversation();
            if (!id) return;
          }
          store.send(content);
        }}
        onStop={() => store.activeId && store.stop(store.activeId)}
        onModelChange={(model: AiModelId) => store.updateSettings({ ...store.settings, model })}
      />
      <Drawer
        title="历史会话"
        placement="left"
        width={320}
        open={mobileSessionsOpen}
        onClose={() => setMobileSessionsOpen(false)}
      >
        <ChatSidebar
          conversations={store.conversations}
          activeId={store.activeId}
          loading={store.loading}
          onSearch={store.loadConversations}
          onNew={() => store.newConversation()}
          onSelect={(id) => {
            store.selectConversation(id);
            setMobileSessionsOpen(false);
          }}
          onRename={store.rename}
          onDelete={store.remove}
          onSettings={() => setSettingsOpen(true)}
        />
      </Drawer>
      <SettingsDialog
        open={settingsOpen}
        settings={store.settings}
        onClose={() => setSettingsOpen(false)}
        onSave={store.updateSettings}
        onClearAll={store.clearAll}
      />
    </div>
  );
}
