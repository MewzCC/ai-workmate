'use client';

import { Button, Empty, Space, Typography } from 'antd';
import { CodeOutlined, FileSearchOutlined, PictureOutlined, WarningOutlined } from '@ant-design/icons';
import type { ChatMessage } from '@/types/chat';
import MessageItem from './MessageItem';

const starters = [
  { label: '帮我总结一份文档', icon: <FileSearchOutlined /> },
  { label: '分析这张图片', icon: <PictureOutlined /> },
  { label: '帮我写一段代码', icon: <CodeOutlined /> },
  { label: '解释这个报错', icon: <WarningOutlined /> },
];

interface MessageListProps {
  messages: ChatMessage[];
  onStarter: (prompt: string) => void;
  onRetry: (prompt: string) => void;
}

export default function MessageList({ messages, onStarter, onRetry }: MessageListProps) {
  if (!messages.length) {
    return (
      <div className="ai-chat-empty">
        <div className="ai-empty-mark">AI</div>
        <Typography.Title level={2}>今天想一起完成什么？</Typography.Title>
        <Typography.Paragraph type="secondary">可以直接提问，也可以上传图片、表格或文档作为上下文。</Typography.Paragraph>
        <Space wrap className="ai-starter-list">
          {starters.map((item) => <Button key={item.label} icon={item.icon} onClick={() => onStarter(item.label)}>{item.label}</Button>)}
        </Space>
      </div>
    );
  }
  return (
    <div className="ai-message-list" role="log" aria-live="polite">
      {messages.map((item, index) => (
        <MessageItem key={item.id} item={item} onRetry={() => onRetry(findPreviousUserPrompt(messages, index))} />
      ))}
    </div>
  );
}

function findPreviousUserPrompt(messages: ChatMessage[], currentIndex: number): string {
  for (let index = currentIndex - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'user') return messages[index].content;
  }
  return '请重新回答上一条问题。';
}
