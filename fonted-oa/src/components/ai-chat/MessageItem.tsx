'use client';

import { useState } from 'react';
import { Avatar, Button, Space, Tag, Tooltip, Typography, message as antMessage } from 'antd';
import {
  CopyOutlined,
  DislikeFilled,
  DislikeOutlined,
  LikeFilled,
  LikeOutlined,
  ReloadOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/components/auth/AuthProvider';
import { updateMessageFeedback } from '@/lib/chatApi';
import type { ChatMessage } from '@/types/chat';
import AttachmentPreview from './AttachmentPreview';
import MarkdownRenderer from './MarkdownRenderer';

interface MessageItemProps {
  item: ChatMessage;
  onRetry: () => void;
}

export default function MessageItem({ item, onRetry }: MessageItemProps) {
  const isAssistant = item.role === 'assistant';
  const { user } = useAuth();
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

  const copyReply = async () => {
    try {
      await navigator.clipboard.writeText(item.content);
      antMessage.success('回复已复制');
    } catch {
      antMessage.error('回复复制失败');
    }
  };

  return (
    <article className={`ai-message ai-message-${item.role}`}>
      <Avatar
        className="ai-message-avatar"
        src={isAssistant ? undefined : user?.avatarUrl}
        icon={isAssistant ? <RobotOutlined /> : <UserOutlined />}
      />
      <div className="ai-message-body">
        <div className="ai-message-heading">
          <Typography.Text strong>{isAssistant ? 'WorkMate AI' : '你'}</Typography.Text>
          {item.status === 'sending' && <Tag color="processing">生成中</Tag>}
          {item.status === 'failed' && <Tag color="error">未完成</Tag>}
        </div>
        {item.attachments.length > 0 && (
          <div className="ai-message-attachments">
            {item.attachments.map((attachment) => (
              <AttachmentPreview key={attachment.id} attachment={attachment} />
            ))}
          </div>
        )}
        <div className={`ai-message-content ${isAssistant && item.status === 'sending' ? 'ai-message-content-streaming' : ''}`}>
          {isAssistant ? (
            <MarkdownRenderer content={item.content || (item.status === 'sending' ? '正在思考...' : '')} />
          ) : (
            <Typography.Paragraph>{item.content}</Typography.Paragraph>
          )}
        </div>
        {isAssistant && item.status !== 'sending' && (
          <Space size={2} className="ai-message-actions">
            <Tooltip title="复制回复">
              <Button type="text" size="small" aria-label="复制回复" icon={<CopyOutlined />} onClick={() => void copyReply()} />
            </Tooltip>
            <Tooltip title="重新生成">
              <Button type="text" size="small" aria-label="重新生成" icon={<ReloadOutlined />} onClick={onRetry} />
            </Tooltip>
            <Tooltip title="有帮助">
              <Button
                type="text"
                size="small"
                aria-label="有帮助"
                icon={feedbackValue === 'like' ? <LikeFilled /> : <LikeOutlined />}
                onClick={() => void setFeedback(feedbackValue === 'like' ? 'none' : 'like')}
              />
            </Tooltip>
            <Tooltip title="需改进">
              <Button
                type="text"
                size="small"
                aria-label="需改进"
                icon={feedbackValue === 'dislike' ? <DislikeFilled /> : <DislikeOutlined />}
                onClick={() => void setFeedback(feedbackValue === 'dislike' ? 'none' : 'dislike')}
              />
            </Tooltip>
          </Space>
        )}
      </div>
    </article>
  );
}
