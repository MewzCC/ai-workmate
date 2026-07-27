'use client';

import { useMemo, useState } from 'react';
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

function groupConversations(conversations: ChatConversation[]): SessionGroup[] {
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
    { key: 'today', label: '今天', items: groups.today },
    { key: 'seven', label: '过去 7 天', items: groups.seven },
    { key: 'thirty', label: '过去 30 天', items: groups.thirty },
    { key: 'earlier', label: '更早', items: groups.earlier },
  ];
  return result.filter((g) => g.items.length > 0);
}

function pickPreview(messages: ChatMessage[] | undefined): string {
  if (messages?.length) {
    const text = (messages[messages.length - 1].content || '').trim();
    if (text) return text.length > 40 ? `${text.slice(0, 40)}…` : text;
  }
  return '暂无消息';
}

export default function ChatSidebar(props: ChatSidebarProps) {
  const [search, setSearch] = useState('');

  const groups = useMemo(() => groupConversations(props.conversations), [props.conversations]);

  const rename = (conversation: ChatConversation) => {
    let title = conversation.title;
    Modal.confirm({
      title: '重命名会话',
      icon: <OaIcon name="edit" />,
      content: <Input defaultValue={title} maxLength={100} onChange={(event) => { title = event.target.value; }} />,
      okText: '保存',
      cancelText: '取消',
      onOk: () => title.trim()
        ? props.onRename(conversation.id, title.trim())
        : Promise.reject(new Error('标题不能为空')),
    });
  };

  const remove = (conversation: ChatConversation) => Modal.confirm({
    title: '删除该会话？',
    content: `“${conversation.title}”及其消息和附件将永久删除。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: () => props.onDelete(conversation.id),
  });

  const renderSessionItem = (item: ChatConversation) => {
    const isActive = item.id === props.activeId;
    const isGenerating = props.generatingIds?.includes(item.id);
    // 优先用完整消息列表（已点击加载过），否则用预览（preloadPreviews 预加载的最近一条）
    const messages = props.messagesByConversation?.[item.id];
    const previewMessages = props.previewByConversation?.[item.id];
    const preview = pickPreview(messages || previewMessages);
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
              {item.title || '新对话'}
            </Typography.Text>
            {isGenerating && (
              <Tooltip title="生成中">
                <LoadingOutlined className="ai-session-generating" />
              </Tooltip>
            )}
          </div>
          <Typography.Text type="secondary" ellipsis className="ai-session-preview">
            {preview}
          </Typography.Text>
          <div className="ai-session-meta">
            <span className="ai-session-time">{formatDate(item.updatedAt)}</span>
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
              { key: 'rename', label: '重命名', icon: <OaIcon name="edit" />, onClick: () => rename(item) },
              { key: 'delete', label: '删除', danger: true, icon: <OaIcon name="delete" />, onClick: () => remove(item) },
            ],
          }}
        >
          <Button
            type="text"
            size="small"
            className="ai-session-more"
            icon={<OaIcon name="more" />}
            aria-label={`管理会话 ${item.title}`}
            onClick={(event) => event.stopPropagation()}
          />
        </Dropdown>
      </div>
    );
  };

  return (
    <aside className="ai-chat-sidebar">
      <div className="ai-sidebar-primary-actions">
        <Button type="primary" icon={<OaIcon name="add" />} block onClick={props.onNew}>新建聊天</Button>
        {props.onCollapse && (
          <Tooltip title="收起会话栏">
            <Button
              className="ai-sidebar-collapse-button"
              icon={<MenuFoldOutlined />}
              aria-label="收起会话栏"
              onClick={props.onCollapse}
            />
          </Tooltip>
        )}
      </div>
      <Input
        allowClear
        prefix={<OaIcon name="search" />}
        value={search}
        placeholder="搜索会话与消息"
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
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话" />
          )}
        </Spin>
      </div>
      <Tooltip title="模型、上下文与数据设置">
        <Button className="ai-sidebar-settings" type="text" icon={<OaIcon name="settings" />} block onClick={props.onSettings}>
          设置
        </Button>
      </Tooltip>
    </aside>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  const today = new Date();
  if (date.toDateString() === today.toDateString()) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  }
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) {
    return '昨天';
  }
  return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}
