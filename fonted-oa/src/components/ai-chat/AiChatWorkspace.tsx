'use client';

import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Drawer, Tooltip } from 'antd';
import { message } from '@/lib/antdMessage';
import { MenuUnfoldOutlined } from '@ant-design/icons';
import { useAiChatStore } from '@/store/aiChatStore';
import { knowledgeApi, type KnowledgeBase } from '@/lib/knowledgeApi';
import type { OaRole } from '@/types/oa';
import type { AiModelId } from '@/config/aiModels';
import ChatSidebar from './ChatSidebar';
import ChatWindow from './ChatWindow';
import SettingsDialog from './SettingsDialog';
import { OaIcon } from '@/components/OaIcon';

const SIDEBAR_COLLAPSED_KEY = 'workmeta-ai-chat-sidebar-collapsed';

interface AiChatWorkspaceProps {
  role: OaRole;
}

export default function AiChatWorkspace({ role }: AiChatWorkspaceProps) {
  const { t } = useTranslation();
  const store = useAiChatStore();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [kbOptions, setKbOptions] = useState<KnowledgeBase[]>([]);
  const active = useMemo(
    () => store.conversations.find((item) => item.id === store.activeId),
    [store.activeId, store.conversations],
  );

  useEffect(() => {
    setSidebarCollapsed(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true');
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
            <Tooltip title={t('chat.settings')} placement="right">
              <Button
                className="ai-sidebar-rail-settings"
                type="text"
                icon={<OaIcon name="settings" />}
                aria-label={t('chat.settings')}
                onClick={() => setSettingsOpen(true)}
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
        generating={store.activeId ? store.generatingIds.includes(store.activeId) : false}
        onOpenSessions={() => setMobileSessionsOpen(true)}
        onUpload={store.upload}
        onRemoveAttachment={store.removePendingAttachment}
        onSend={(content) => store.send(content)}
        onStop={() => store.activeId && store.stop(store.activeId)}
        onModelChange={(model: AiModelId) => store.updateSettings({ ...store.settings, model })}
        onKbChange={(kbId: number | null) => store.updateSettings({ ...store.settings, kbId })}
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
