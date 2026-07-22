'use client';

import { useEffect, useMemo, useState } from 'react';
import { Drawer, message } from 'antd';
import { ChatApiError } from '@/lib/chatApi';
import { useAiChatStore } from '@/store/aiChatStore';
import type { OaRole } from '@/types/oa';
import ChatSidebar from './ChatSidebar';
import ChatWindow from './ChatWindow';
import SettingsDialog from './SettingsDialog';
import WorkspaceAuthGate from './WorkspaceAuthGate';

interface AiChatWorkspaceProps { role: OaRole; }

export default function AiChatWorkspace({ role }: AiChatWorkspaceProps) {
  const store = useAiChatStore();
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [mobileSessionsOpen, setMobileSessionsOpen] = useState(false);
  const [authenticated, setAuthenticated] = useState(() => typeof window !== 'undefined' && Boolean(localStorage.getItem('token')));
  const active = useMemo(() => store.conversations.find((item) => item.id === store.activeId), [store.activeId, store.conversations]);

  useEffect(() => {
    if (!authenticated) return;
    store.loadConversations().catch((error) => {
      if (error instanceof ChatApiError && error.status === 401) setAuthenticated(false);
      else message.error(error instanceof Error ? error.message : '会话加载失败');
    });
  // Store actions are stable in Zustand.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [authenticated]);

  if (!authenticated) return <WorkspaceAuthGate onAuthenticated={() => setAuthenticated(true)} />;

  const sidebar = (
    <ChatSidebar
      conversations={store.conversations}
      activeId={store.activeId}
      loading={store.loading}
      onSearch={store.loadConversations}
      onNew={() => store.newConversation()}
      onSelect={(id) => { store.selectConversation(id); setMobileSessionsOpen(false); }}
      onRename={store.rename}
      onDelete={store.remove}
      onSettings={() => setSettingsOpen(true)}
    />
  );

  return (
    <div className="ai-workspace" data-role={role}>
      <div className="ai-desktop-sidebar">{sidebar}</div>
      <ChatWindow
        title={active?.title || '新对话'}
        model={active?.model || store.settings.model}
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
      />
      <Drawer title="历史会话" placement="left" width={320} open={mobileSessionsOpen} onClose={() => setMobileSessionsOpen(false)}>
        {sidebar}
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
