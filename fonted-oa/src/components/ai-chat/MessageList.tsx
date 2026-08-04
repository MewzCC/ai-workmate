'use client';

import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Button, Space, Typography } from 'antd';
import { PictureOutlined, WarningOutlined } from '@ant-design/icons';
import type { ChatMessage } from '@/types/chat';
import MessageItem from './MessageItem';
import { OaIcon } from '@/components/OaIcon';

interface MessageListProps {
  messages: ChatMessage[];
  onStarter: (prompt: string) => void;
  onRetry: (prompt: string) => void;
}

export default function MessageList({ messages, onStarter, onRetry }: MessageListProps) {
  const { t } = useTranslation();
  const starters = [
    { key: 'summarize', label: t('chat.starter.summarize'), icon: <OaIcon name="search" /> },
    { key: 'analyzeImage', label: t('chat.starter.analyzeImage'), icon: <PictureOutlined /> },
    { key: 'writeCode', label: t('chat.starter.writeCode'), icon: <OaIcon name="code" /> },
    { key: 'explainError', label: t('chat.starter.explainError'), icon: <WarningOutlined /> },
  ];
  if (!messages.length) {
    return (
      <div className="ai-chat-empty">
        <div className="ai-empty-mark"><OaIcon name="ai" size={30} title="WorkMate AI" /></div>
        <Typography.Title level={2}>{t('chat.emptyTitle')}</Typography.Title>
        <Typography.Paragraph type="secondary">{t('chat.emptyDesc')}</Typography.Paragraph>
        <Space wrap className="ai-starter-list">
          {starters.map((item) => <Button key={item.key} icon={item.icon} onClick={() => onStarter(item.label)}>{item.label}</Button>)}
        </Space>
      </div>
    );
  }
  return (
    <div className="ai-message-list" role="log" aria-live="polite">
      {messages.map((item, index) => (
        <MessageItem key={item.id} item={item} onRetry={() => onRetry(findPreviousUserPrompt(messages, index, t))} />
      ))}
    </div>
  );
}

function findPreviousUserPrompt(messages: ChatMessage[], currentIndex: number, t: TFunction): string {
  for (let index = currentIndex - 1; index >= 0; index -= 1) {
    if (messages[index].role === 'user') return messages[index].content;
  }
  return t('chat.retryPrevious');
}
