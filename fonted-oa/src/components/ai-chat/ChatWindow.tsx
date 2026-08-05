'use client';

import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Select, Typography } from 'antd';
import { DatabaseOutlined, MenuOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { AI_MODEL_OPTIONS, type AiModelId } from '@/config/aiModels';
import type { KnowledgeBase } from '@/lib/knowledgeApi';
import type { ChatAttachment, ChatMessage } from '@/types/chat';
import ChatInput from './ChatInput';
import MessageList from './MessageList';
import type { UploadProgressItem } from '@/store/aiChatStore';

interface ChatWindowProps {
  title: string;
  model: AiModelId;
  kbId: number | null;
  kbOptions: KnowledgeBase[];
  messages: ChatMessage[];
  pending: ChatAttachment[];
  uploading: UploadProgressItem[];
  generating: boolean;
  onOpenSessions: () => void;
  onUpload: (files: File[]) => void;
  onRemoveAttachment: (id: number) => void;
  onSend: (content: string) => void;
  onStop: () => void;
  onModelChange: (model: AiModelId) => void;
  onKbChange: (kbId: number | null) => void;
}

export default function ChatWindow(props: ChatWindowProps) {
  const { t } = useTranslation();
  const scrollRef = useRef<HTMLDivElement>(null);
  const latestContent = props.messages[props.messages.length - 1]?.content;

  useEffect(() => {
    const container = scrollRef.current;
    if (container) container.scrollTop = container.scrollHeight;
  }, [latestContent, props.messages.length]);

  return (
    <section className="ai-chat-window">
      <header className="ai-chat-header">
        <Button className="ai-mobile-session-button" type="text" icon={<MenuOutlined />} onClick={props.onOpenSessions} />
        <div>
          <Typography.Title level={5} title={props.title}>{props.title}</Typography.Title>
          <Typography.Text type="secondary"><SafetyCertificateOutlined /> {t('chat.serverValidated')}</Typography.Text>
        </div>
        <Select<number | null>
          aria-label={t('chat.selectKnowledgeBase')}
          className="ai-kb-select"
          placeholder={t('chat.allKnowledgeBases')}
          value={props.kbId ?? undefined}
          allowClear
          showSearch
          optionFilterProp="label"
          suffixIcon={<DatabaseOutlined />}
          options={props.kbOptions.map((base) => ({ label: base.name, value: base.id }))}
          onChange={(value) => props.onKbChange(value ?? null)}
          disabled={props.generating}
        />
        <Select<AiModelId>
          aria-label={t('chat.switchModel')}
          className="ai-model-select"
          value={props.model}
          options={[...AI_MODEL_OPTIONS]}
          onChange={props.onModelChange}
          disabled={props.generating}
        />
      </header>
      <div className="ai-chat-scroll" ref={scrollRef}>
        <MessageList messages={props.messages} onStarter={props.onSend} onRetry={props.onSend} />
      </div>
      <div className="ai-composer-wrap">
        <ChatInput
          pending={props.pending}
          uploading={props.uploading}
          generating={props.generating}
          onUpload={props.onUpload}
          onRemoveAttachment={props.onRemoveAttachment}
          onSend={props.onSend}
          onStop={props.onStop}
        />
        <Typography.Text type="secondary" className="ai-disclaimer">{t('chat.disclaimer')}</Typography.Text>
      </div>
    </section>
  );
}
