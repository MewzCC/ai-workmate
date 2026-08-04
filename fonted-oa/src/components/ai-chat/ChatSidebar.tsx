'use client';

import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Button, Dropdown, Empty, Input, Modal, Spin, Tooltip, Typography } from 'antd';
import {
  LoadingOutlined,
  MenuFoldOutlined,
  MessageOutlined,
} from '@ant-design/icons';
import type { ChatConversation, ChatMessage } from '@/types/chat';
import { OaIcon } from '@/components/OaIcon';

interface ChatSidebarProps {
  conversations: ChatConversation[];
  activeId: number | null;
  loading: boolean;
  messagesByConversation?: Record<number, ChatMessage[]>;
  previewByConversation?: Record<number, ChatMessage[]>;
  generatingIds?: number[];
  onSearch: (value: string) => void;
  onNew: () => void;
  onSelect: (id: number) => void;
  onRename: (id: number, title: string) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onSettings: () => void;
  onCollapse?: () => void;
}

interface SessionGroup {
  key: string;
  label: string;
  items: ChatConversation[];
}

function groupConversations(conversations: ChatConversation[], t: TFunction): SessionGroup[] {
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const sevenDaysAgo = todayStart - 7 * 24 * 60 * 60 * 1000;
  const thirtyDaysAgo = todayStart - 30 * 24 * 60 * 60 * 1000;

  const groups: Record<string, ChatConversation[]> = {
    today: [],
    seven: [],
    thirty: [],
    earlier: [],
  };

  for (const conv of conversations) {
    const ts = new Date(conv.updatedAt).getTime();
    if (ts >= todayStart) groups.today.push(conv);
    else if (ts >= sevenDaysAgo) groups.seven.push(conv);
    else if (ts >= thirtyDaysAgo) groups.thirty.push(conv);
    else groups.earlier.push(conv);
  }

  const result: SessionGroup[] = [
    { key: 'today', label: t('chat.groupToday'), items: groups.today },
    { key: 'seven', label: t('chat.groupLast7Days'), items: groups.seven },
    { key: 'thirty', label: t('chat.groupLast30Days'), items: groups.thirty },
    { key: 'earlier', label: t('chat.groupEarlier'), items: groups.earlier },
  ];
  return result.filter((g) => g.items.length > 0);
}

function pickPreview(messages: ChatMessage[] | undefined, t: TFunction): string {
  if (messages?.length) {
    const text = (messages[messages.length - 1].content || '').trim();
    if (text) return text.length > 40 ? `${text.slice(0, 40)}…` : text;
  }
  return t('chat.noMessages');
}

export default function ChatSidebar(props: ChatSidebarProps) {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');

  const groups = useMemo(() => groupConversations(props.conversations, t), [props.conversations, t]);

  const rename = (conversation: ChatConversation) => {
    let title = conversation.title;
    Modal.confirm({
      title: t('chat.renameConversation'),
      icon: <OaIcon name="edit" />,
      content: <Input defaultValue={title} maxLength={100} onChange={(event) => { title = event.target.value; }} />,
      okText: t('common.save'),
      cancelText: t('common.cancel'),
      onOk: () => title.trim()
        ? props.onRename(conversation.id, title.trim())
        : Promise.reject(new Error(t('chat.titleRequired'))),
    });
  };

  const remove = (conversation: ChatConversation) => Modal.confirm({
    title: t('chat.deleteConversationTitle'),
    content: t('chat.deleteConversationContent', { title: conversation.title }),
    okText: t('common.delete'),
    cancelText: t('common.cancel'),
    okButtonProps: { danger: true },
    onOk: () => props.onDelete(conversation.id),
  });

  const renderSessionItem = (item: ChatConversation) => {
    const isActive = item.id === props.activeId;
    const isGenerating = props.generatingIds?.includes(item.id);
    // 优先用完整消息列表（已点击加载过），否则用预览（preloadPreviews 预加载的最近一条）
    const messages = props.messagesByConversation?.[item.id];
    const previewMessages = props.previewByConversation?.[item.id];
    const preview = pickPreview(messages || previewMessages, t);
    // 消息数：只有完整列表才知道准确数量，预览模式不显示
    const messageCount = messages?.length || 0;

    return (
      <div
        key={item.id}
        className={`ai-session-item ${isActive ? 'ai-session-active' : ''}`}
        onClick={() => props.onSelect(item.id)}
      >
        {isActive && <span className="ai-session-indicator" aria-hidden="true" />}
        <div className="ai-session-copy">
          <div className="ai-session-title-row">
            <Typography.Text ellipsis className="ai-session-title">
              {item.title || t('chat.newConversation')}
            </Typography.Text>
            {isGenerating && (
              <Tooltip title={t('chat.generating')}>
                <LoadingOutlined className="ai-session-generating" />
              </Tooltip>
            )}
          </div>
          <Typography.Text type="secondary" ellipsis className="ai-session-preview">
            {preview}
          </Typography.Text>
          <div className="ai-session-meta">
            <span className="ai-session-time">{formatDate(item.updatedAt, t)}</span>
            {messageCount > 0 && (
              <span className="ai-session-count">
                <MessageOutlined /> {messageCount}
              </span>
            )}
          </div>
        </div>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: 'rename', label: t('chat.rename'), icon: <OaIcon name="edit" />, onClick: () => rename(item) },
              { key: 'delete', label: t('common.delete'), danger: true, icon: <OaIcon name="delete" />, onClick: () => remove(item) },
            ],
          }}
        >
          <Button
            type="text"
            size="small"
            className="ai-session-more"
            icon={<OaIcon name="more" />}
            aria-label={t('chat.manageConversation', { title: item.title })}
            onClick={(event) => event.stopPropagation()}
          />
        </Dropdown>
      </div>
    );
  };

  return (
    <aside className="ai-chat-sidebar">
      <div className="ai-sidebar-primary-actions">
        <Button type="primary" icon={<OaIcon name="add" />} block onClick={props.onNew}>{t('chat.newChat')}</Button>
        {props.onCollapse && (
          <Tooltip title={t('chat.collapseSidebar')}>
            <Button
              className="ai-sidebar-collapse-button"
              icon={<MenuFoldOutlined />}
              aria-label={t('chat.collapseSidebar')}
              onClick={props.onCollapse}
            />
          </Tooltip>
        )}
      </div>
      <Input
        allowClear
        prefix={<OaIcon name="search" />}
        value={search}
        placeholder={t('chat.searchSessions')}
        onChange={(event) => setSearch(event.target.value)}
        onPressEnter={() => props.onSearch(search)}
        onClear={() => props.onSearch('')}
      />
      <div className="ai-session-list">
        <Spin spinning={props.loading}>
          {props.conversations.length ? (
            <div className="ai-session-groups">
              {groups.map((group) => (
                <div key={group.key} className="ai-session-group">
                  <div className="ai-session-group-label">{group.label}</div>
                  {group.items.map(renderSessionItem)}
                </div>
              ))}
            </div>
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('chat.noSessions')} />
          )}
        </Spin>
      </div>
      <Tooltip title={t('chat.settingsTooltip')}>
        <Button className="ai-sidebar-settings" type="text" icon={<OaIcon name="settings" />} block onClick={props.onSettings}>
          {t('chat.settings')}
        </Button>
      </Tooltip>
    </aside>
  );
}

function formatDate(value: string, t: TFunction): string {
  const date = new Date(value);
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return t('chat.yesterday');
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}
