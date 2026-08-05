'use client';

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, Button, Image, Modal, Spin, Tag, Typography } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import { loadAttachmentContent, loadAttachmentText } from '@/lib/chatApi';
import type { ChatAttachment } from '@/types/chat';
import MarkdownRenderer from './MarkdownRenderer';
import { OaIcon, type OaIconName } from '@/components/OaIcon';

interface AttachmentPreviewProps {
  attachment: ChatAttachment;
  removable?: boolean;
  onRemove?: () => void;
}

export default function AttachmentPreview({ attachment, removable, onRemove }: AttachmentPreviewProps) {
  const { t } = useTranslation();
  const [source, setSource] = useState(attachment.previewUrl || '');
  const [loading, setLoading] = useState(attachment.type === 'image' && !attachment.previewUrl);
  const [markdownOpen, setMarkdownOpen] = useState(false);
  const [markdownLoading, setMarkdownLoading] = useState(false);
  const [markdownContent, setMarkdownContent] = useState('');
  const [markdownError, setMarkdownError] = useState('');
  const markdown = isMarkdownAttachment(attachment);

  useEffect(() => {
    if (attachment.type !== 'image' || attachment.previewUrl) return;
    const controller = new AbortController();
    let active = true;
    let objectUrl = '';
    loadAttachmentContent(attachment.id, controller.signal)
      .then((url) => {
        if (!active) {
          URL.revokeObjectURL(url);
          return;
        }
        objectUrl = url;
        setSource(url);
      })
      .catch((error: unknown) => {
        if (!isAbortError(error)) {
          setSource('');
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
      if (!controller.signal.aborted) controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [attachment.id, attachment.previewUrl, attachment.type]);

  const openMarkdown = async () => {
    setMarkdownOpen(true);
    if (markdownContent || markdownLoading) return;
    setMarkdownLoading(true);
    setMarkdownError('');
    try {
      setMarkdownContent(await loadAttachmentText(attachment.id));
    } catch (error) {
      setMarkdownError(error instanceof Error ? error.message : t('chat.markdownLoadFailed'));
    } finally {
      setMarkdownLoading(false);
    }
  };

  return (
    <>
      <div className={`ai-attachment ${attachment.type === 'image' ? 'ai-attachment-image' : ''}`}>
        {attachment.type === 'image' ? (
          <Spin spinning={loading} size="small">
            {source && <Image src={source} alt={attachment.name} preview width={88} height={64} />}
            {attachment.hasText ? (
              <Tag color="blue" className="ai-attachment-ocr-tag">{t('chat.ocrParsed')}</Tag>
            ) : (
              <Tag className="ai-attachment-ocr-tag">{t('chat.ocrPending')}</Tag>
            )}
          </Spin>
        ) : markdown ? (
          <Button
            type="text"
            className="ai-attachment-open"
            icon={<span className="ai-attachment-file-icon"><OaIcon name={attachmentIcon(attachment)} /></span>}
            onClick={() => void openMarkdown()}
          >
            <AttachmentMeta attachment={attachment} action={t('chat.clickToPreview')} />
          </Button>
        ) : (
          <>
            <span className="ai-attachment-file-icon"><OaIcon name={attachmentIcon(attachment)} /></span>
            <AttachmentMeta attachment={attachment} />
          </>
        )}
        {removable && (
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            aria-label={t('chat.removeAttachment', { name: attachment.name })}
            onClick={onRemove}
          />
        )}
      </div>
      <Modal
        open={markdownOpen}
        title={attachment.name}
        width={920}
        footer={null}
        destroyOnHidden
        className="ai-markdown-modal"
        onCancel={() => setMarkdownOpen(false)}
      >
        <Spin spinning={markdownLoading}>
          {markdownError ? (
            <Alert type="error" showIcon title={t('chat.previewFailed')} description={markdownError} />
          ) : (
            <MarkdownRenderer content={markdownContent} className="ai-markdown-document" />
          )}
        </Spin>
      </Modal>
    </>
  );
}

function AttachmentMeta({ attachment, action }: { attachment: ChatAttachment; action?: string }) {
  const { t } = useTranslation();
  return (
    <span className="ai-attachment-meta">
      <Typography.Text ellipsis title={attachment.name}>{attachment.name}</Typography.Text>
      <Typography.Text type="secondary">
        {formatBytes(attachment.size)} · {attachment.parsed ? t('chat.parsed') : t('chat.pendingParse')}{action ? ` · ${action}` : ''}
      </Typography.Text>
    </span>
  );
}

function isMarkdownAttachment(attachment: ChatAttachment): boolean {
  const name = attachment.name.toLowerCase();
  return attachment.mimeType === 'text/markdown' || name.endsWith('.md') || name.endsWith('.markdown');
}

function attachmentIcon(attachment: ChatAttachment): OaIconName {
  const extension = attachment.name.split('.').pop()?.toLowerCase();
  if (extension === 'csv') return 'csv';
  if (extension === 'pdf') return 'pdf';
  if (extension === 'png') return 'png';
  if (extension === 'jpg' || extension === 'jpeg') return 'jpg';
  if (extension === 'txt' || extension === 'md' || extension === 'markdown') return 'txt';
  return 'attachment';
}

function isAbortError(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === 'AbortError'
    : error instanceof Error && error.name === 'AbortError';
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
