'use client';

import { useEffect, useRef } from 'react';
import { Button, Select, Typography } from 'antd';
import { MenuOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { AI_MODEL_OPTIONS, type AiModelId } from '@/config/aiModels';
import type { ChatAttachment, ChatMessage } from '@/types/chat';
import ChatInput from './ChatInput';
import MessageList from './MessageList';

interface ChatWindowProps {
  title: string;
  model: AiModelId;
  messages: ChatMessage[];
  pending: ChatAttachment[];
  generating: boolean;
  onOpenSessions: () => void;
  onUpload: (files: File[]) => void;
  onRemoveAttachment: (id: number) => void;
  onSend: (content: string) => void;
  onStop: () => void;
  onModelChange: (model: AiModelId) => void;
}

export default function ChatWindow(props: ChatWindowProps) {
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
          <Typography.Title level={5}>{props.title}</Typography.Title>
          <Typography.Text type="secondary"><SafetyCertificateOutlined /> 权限由服务端校验</Typography.Text>
        </div>
        <Select<AiModelId>
          aria-label="切换对话模型"
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
          generating={props.generating}
          onUpload={props.onUpload}
          onRemoveAttachment={props.onRemoveAttachment}
          onSend={props.onSend}
          onStop={props.onStop}
        />
        <Typography.Text type="secondary" className="ai-disclaimer">AI 可能出错；涉及审批、财务或权限变更时请核对执行计划。</Typography.Text>
      </div>
    </section>
  );
}
