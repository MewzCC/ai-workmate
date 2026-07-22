'use client';

import { useState } from 'react';
import { Button, Space, Tag, Tooltip, Typography, message as antMessage } from 'antd';
import { CopyOutlined, DislikeFilled, DislikeOutlined, LikeFilled, LikeOutlined, ReloadOutlined, RobotOutlined, UserOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { updateMessageFeedback } from '@/lib/chatApi';
import type { ChatMessage } from '@/types/chat';
import AttachmentPreview from './AttachmentPreview';

interface MessageItemProps {
  item: ChatMessage;
  onRetry: () => void;
}

export default function MessageItem({ item, onRetry }: MessageItemProps) {
  const isAssistant = item.role === 'assistant';
  const [feedbackValue, setFeedbackValue] = useState(item.feedback);
  const setFeedback = async (feedback: 'like' | 'dislike' | 'none') => {
    if (typeof item.id !== 'number') return;
    try {
      await updateMessageFeedback(item.id, feedback);
      setFeedbackValue(feedback === 'none' ? null : feedback);
      antMessage.success('反馈已记录');
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : '反馈提交失败');
    }
  };

  return (
    <article className={`ai-message ai-message-${item.role}`}>
      <div className="ai-message-avatar" aria-hidden="true">
        {isAssistant ? <RobotOutlined /> : <UserOutlined />}
      </div>
      <div className="ai-message-body">
        <div className="ai-message-heading">
          <Typography.Text strong>{isAssistant ? 'WorkMate AI' : '你'}</Typography.Text>
          {item.status === 'sending' && <Tag color="processing">生成中</Tag>}
          {item.status === 'failed' && <Tag color="error">未完成</Tag>}
        </div>
        {item.attachments.length > 0 && (
          <div className="ai-message-attachments">
            {item.attachments.map((attachment) => <AttachmentPreview key={attachment.id} attachment={attachment} />)}
          </div>
        )}
        <div className="ai-message-content">
          {isAssistant ? (
            <ReactMarkdown components={{
              code({ className, children, ...props }) {
                const match = /language-(\w+)/.exec(className || '');
                return match ? (
                  <SyntaxHighlighter style={oneDark} language={match[1]} PreTag="div">
                    {String(children).replace(/\n$/, '')}
                  </SyntaxHighlighter>
                ) : <code className={className} {...props}>{children}</code>;
              },
            }}>{item.content || (item.status === 'sending' ? '正在思考…' : '')}</ReactMarkdown>
          ) : <Typography.Paragraph>{item.content}</Typography.Paragraph>}
        </div>
        {isAssistant && item.status !== 'sending' && (
          <Space size={2} className="ai-message-actions">
            <Tooltip title="复制回复"><Button type="text" size="small" icon={<CopyOutlined />} onClick={() => navigator.clipboard.writeText(item.content)} /></Tooltip>
            <Tooltip title="重新生成"><Button type="text" size="small" icon={<ReloadOutlined />} onClick={onRetry} /></Tooltip>
            <Tooltip title="有帮助"><Button type="text" size="small" icon={feedbackValue === 'like' ? <LikeFilled /> : <LikeOutlined />} onClick={() => setFeedback(feedbackValue === 'like' ? 'none' : 'like')} /></Tooltip>
            <Tooltip title="需改进"><Button type="text" size="small" icon={feedbackValue === 'dislike' ? <DislikeFilled /> : <DislikeOutlined />} onClick={() => setFeedback(feedbackValue === 'dislike' ? 'none' : 'dislike')} /></Tooltip>
          </Space>
        )}
      </div>
    </article>
  );
}
