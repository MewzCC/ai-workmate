'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Avatar, Button, Space, Tag, Tooltip, Typography } from 'antd';
import { message as antMessage } from '@/lib/antdMessage';
import {
  DislikeFilled,
  DislikeOutlined,
  LikeFilled,
  LikeOutlined,
} from '@ant-design/icons';
import { useAuth } from '@/components/auth/AuthProvider';
import { updateMessageFeedback } from '@/lib/chatApi';
import type { ChatMessage } from '@/types/chat';
import AttachmentPreview from './AttachmentPreview';
import MarkdownRenderer from './MarkdownRenderer';
import CitationList, { extractCitedIndexes, type CitedItem } from './CitationList';
import { OaIcon } from '@/components/OaIcon';
import { usePermission } from '@/hooks/usePermission';
import { useRouter } from '@/lib/nextCompat';
import { agentToolTargetPage, resolveAgentTaskTargetPath } from '@/lib/agentToolTargetPage';
import { agentTaskApi } from '@/lib/oaApi';
import { getNavigation } from '@/lib/navigationApi';
import { toMenuItem } from '@/lib/navigationTree';

interface MessageItemProps {
  item: ChatMessage;
  onRetry: () => void;
}

export default function MessageItem({ item, onRetry }: MessageItemProps) {
  const { t } = useTranslation();
  const isAssistant = item.role === 'assistant';
  const { user } = useAuth();
  const targetPage = agentToolTargetPage(item.sourceToolCode);
  const { allowed: canOpenTarget, permissionVersion } = usePermission(`route:${targetPage}`);
  const router = useRouter();
  const [feedbackValue, setFeedbackValue] = useState(item.feedback);
  const [opening, setOpening] = useState(false);
  const openingRef = useRef(false);
  const requestRef = useRef(0);

  useEffect(() => {
    requestRef.current += 1;
    openingRef.current = false;
    setOpening(false);
    return () => { requestRef.current += 1; };
  }, [item.id, item.sourceTaskNo, item.status, item.role, canOpenTarget, permissionVersion, user?.id, user?.tenantId]);

  const openBusinessResult = async () => {
    if (!isAssistant || item.status !== 'success' || !item.sourceTaskNo || !canOpenTarget || openingRef.current) return;
    const request = ++requestRef.current;
    openingRef.current = true;
    setOpening(true);
    try {
      const [detail, navigation] = await Promise.all([agentTaskApi.detail(item.sourceTaskNo), getNavigation()]);
      if (request !== requestRef.current) return;
      const path = resolveAgentTaskTargetPath(item.sourceTaskNo, detail, navigation.map(toMenuItem));
      if (!path) throw new Error('UNAVAILABLE');
      router.push(path);
    } catch {
      if (request === requestRef.current) antMessage.error(t('chat.businessTargetUnavailable'));
    } finally {
      if (request === requestRef.current) { openingRef.current = false; setOpening(false); }
    }
  };

  // 末尾引用列表只展示正文中实际标注过的引用，保持与上标序号一一对应；
  // 正文完全未标注时保底展示全部引用，避免丢失知识来源信息
  const citedIndexes = useMemo(() => extractCitedIndexes(item.content), [item.content]);
  const visibleCitations = useMemo<CitedItem[]>(() => {
    const all = item.citations || [];
    const withIndex = all.map((citation, i) => ({ index: i + 1, citation }));
    if (!citedIndexes.size) return withIndex;
    return withIndex.filter((entry) => citedIndexes.has(entry.index));
  }, [item.citations, citedIndexes]);

  const setFeedback = async (feedback: 'like' | 'dislike' | 'none') => {
    if (typeof item.id !== 'number') return;
    try {
      await updateMessageFeedback(item.id, feedback);
      setFeedbackValue(feedback === 'none' ? null : feedback);
      antMessage.success(t('chat.feedbackRecorded'));
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : t('chat.feedbackFailed'));
    }
  };

  const copyReply = async () => {
    try {
      await navigator.clipboard.writeText(item.content);
      antMessage.success(t('chat.replyCopied'));
    } catch {
      antMessage.error(t('chat.replyCopyFailed'));
    }
  };

  return (
    <article className={`ai-message ai-message-${item.role}`}>
      <Avatar
        className="ai-message-avatar"
        src={isAssistant ? undefined : user?.avatarUrl}
        icon={isAssistant ? <OaIcon name="ai" /> : <OaIcon name="avatar" />}
      />
      <div className="ai-message-body">
        <div className="ai-message-heading">
          <Typography.Text strong>{isAssistant ? t('chat.assistantName') : t('chat.you')}</Typography.Text>
          {item.status === 'sending' && <Tag color="processing">{t('chat.statusGenerating')}</Tag>}
          {item.status === 'failed' && <Tag color="error">{t('chat.statusIncomplete')}</Tag>}
        </div>
        {item.attachments.length > 0 && (
          <div className="ai-message-attachments">
            {item.attachments.map((attachment) => (
              <AttachmentPreview key={attachment.id} attachment={attachment} />
            ))}
          </div>
        )}
        <div className={`ai-message-content ${isAssistant && item.status === 'sending' ? 'ai-message-content-streaming' : ''}`}>
          {isAssistant && !item.sourceTaskNo && !item.sourceToolCode ? (
            <MarkdownRenderer
              content={item.content || (item.status === 'sending' ? t('chat.thinking') : '')}
              citations={item.citations || []}
            />
          ) : (
            <div className="ai-message-user-text">{item.content}</div>
          )}
        </div>
        {isAssistant && item.status === 'success' && visibleCitations.length > 0 && (
          <CitationList citations={visibleCitations} />
        )}
        {isAssistant && item.status === 'success' && item.sourceTaskNo && canOpenTarget && (
          <Button type="link" loading={opening} icon={<OaIcon name="integration" />} onClick={() => void openBusinessResult()}>
            {t('chat.openBusinessPage')}
          </Button>
        )}
        {isAssistant && item.status !== 'sending' && (
          <Space size={2} className="ai-message-actions">
            <Tooltip title={t('chat.copyReply')}>
              <Button type="text" size="small" aria-label={t('chat.copyReply')} icon={<OaIcon name="copy" />} onClick={() => void copyReply()} />
            </Tooltip>
            <Tooltip title={t('chat.regenerate')}>
              <Button type="text" size="small" aria-label={t('chat.regenerate')} icon={<OaIcon name="reload" />} onClick={onRetry} />
            </Tooltip>
            <Tooltip title={t('chat.helpful')}>
              <Button
                type="text"
                size="small"
                aria-label={t('chat.helpful')}
                icon={feedbackValue === 'like' ? <LikeFilled /> : <LikeOutlined />}
                onClick={() => void setFeedback(feedbackValue === 'like' ? 'none' : 'like')}
              />
            </Tooltip>
            <Tooltip title={t('chat.needImprove')}>
              <Button
                type="text"
                size="small"
                aria-label={t('chat.needImprove')}
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
