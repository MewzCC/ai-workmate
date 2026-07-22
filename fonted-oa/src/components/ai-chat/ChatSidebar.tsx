'use client';

import { useState } from 'react';
import { Button, Dropdown, Empty, Input, List, Modal, Space, Spin, Tooltip, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, MoreOutlined, PlusOutlined, SearchOutlined, SettingOutlined } from '@ant-design/icons';
import type { ChatConversation } from '@/types/chat';

interface ChatSidebarProps {
  conversations: ChatConversation[];
  activeId: number | null;
  loading: boolean;
  onSearch: (value: string) => void;
  onNew: () => void;
  onSelect: (id: number) => void;
  onRename: (id: number, title: string) => Promise<void>;
  onDelete: (id: number) => Promise<void>;
  onSettings: () => void;
}

export default function ChatSidebar(props: ChatSidebarProps) {
  const [search, setSearch] = useState('');

  const rename = (conversation: ChatConversation) => {
    let title = conversation.title;
    Modal.confirm({
      title: '重命名会话',
      icon: <EditOutlined />,
      content: <Input defaultValue={title} maxLength={100} onChange={(event) => { title = event.target.value; }} />,
      okText: '保存', cancelText: '取消',
      onOk: () => title.trim() ? props.onRename(conversation.id, title.trim()) : Promise.reject(new Error('标题不能为空')),
    });
  };

  const remove = (conversation: ChatConversation) => Modal.confirm({
    title: '删除该会话？',
    content: `“${conversation.title}”及其消息和附件将永久删除。`,
    okText: '删除', cancelText: '取消', okButtonProps: { danger: true },
    onOk: () => props.onDelete(conversation.id),
  });

  return (
    <aside className="ai-chat-sidebar">
      <Button type="primary" icon={<PlusOutlined />} block onClick={props.onNew}>新建聊天</Button>
      <Input
        allowClear prefix={<SearchOutlined />} value={search} placeholder="搜索会话与消息"
        onChange={(event) => setSearch(event.target.value)}
        onPressEnter={() => props.onSearch(search)}
        onClear={() => props.onSearch('')}
      />
      <div className="ai-session-list">
        <Spin spinning={props.loading}>
          {props.conversations.length ? (
            <List dataSource={props.conversations} renderItem={(item) => (
              <List.Item className={item.id === props.activeId ? 'ai-session-active' : ''} onClick={() => props.onSelect(item.id)}>
                <div className="ai-session-copy">
                  <Typography.Text ellipsis>{item.title}</Typography.Text>
                  <Typography.Text type="secondary">{formatDate(item.updatedAt)}</Typography.Text>
                </div>
                <Dropdown trigger={['click']} menu={{ items: [
                  { key: 'rename', label: '重命名', icon: <EditOutlined />, onClick: () => rename(item) },
                  { key: 'delete', label: '删除', danger: true, icon: <DeleteOutlined />, onClick: () => remove(item) },
                ] }}>
                  <Button type="text" size="small" icon={<MoreOutlined />} onClick={(event) => event.stopPropagation()} />
                </Dropdown>
              </List.Item>
            )} />
          ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话" />}
        </Spin>
      </div>
      <Tooltip title="模型、上下文与数据设置">
        <Button className="ai-sidebar-settings" type="text" icon={<SettingOutlined />} block onClick={props.onSettings}>设置</Button>
      </Tooltip>
    </aside>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  const today = new Date();
  return date.toDateString() === today.toDateString()
    ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
}
