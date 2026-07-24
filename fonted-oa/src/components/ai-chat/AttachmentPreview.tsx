'use client';

import { useEffect, useState } from 'react';
import { Alert, Button, Image, Modal, Spin, Typography } from 'antd';
import { CloseOutlined, FileTextOutlined } from '@ant-design/icons';
import { loadAttachmentContent, loadAttachmentText } from '@/lib/chatApi';
import type { ChatAttachment } from '@/types/chat';
import MarkdownRenderer from './MarkdownRenderer';

interface AttachmentPreviewProps {
  attachment: ChatAttachment;
  removable?: boolean;
  onRemove?: () => void;
}

export default function AttachmentPreview({ attachment, removable, onRemove }: AttachmentPreviewProps) {
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
    let objectUrl = '';
    loadAttachmentContent(attachment.id, controller.signal)
      .then((url) => {
        objectUrl = url;
        setSource(url);
      })
      .finally(() => setLoading(false));
    return () => {
      controller.abort();
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
      setMarkdownError(error instanceof Error ? error.message : 'Markdown 文档加载失败');
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
          </Spin>
        ) : markdown ? (
          <Button
            type="text"
            className="ai-attachment-open"
            icon={<span className="ai-attachment-file-icon"><FileTextOutlined /></span>}
            onClick={() => void openMarkdown()}
          >
            <AttachmentMeta attachment={attachment} action="点击预览" />
          </Button>
        ) : (
          <>
            <span className="ai-attachment-file-icon"><FileTextOutlined /></span>
            <AttachmentMeta attachment={attachment} />
          </>
        )}
        {removable && (
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            aria-label={`移除 ${attachment.name}`}
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
            <Alert type="error" showIcon message="文档预览失败" description={markdownError} />
          ) : (
            <MarkdownRenderer content={markdownContent} className="ai-markdown-document" />
          )}
        </Spin>
      </Modal>
    </>
  );
}

function AttachmentMeta({ attachment, action }: { attachment: ChatAttachment; action?: string }) {
  return (
    <span className="ai-attachment-meta">
      <Typography.Text ellipsis title={attachment.name}>{attachment.name}</Typography.Text>
      <Typography.Text type="secondary">
        {formatBytes(attachment.size)} · {attachment.parsed ? '已解析' : '待解析'}{action ? ` · ${action}` : ''}
      </Typography.Text>
    </span>
  );
}

function isMarkdownAttachment(attachment: ChatAttachment): boolean {
  const name = attachment.name.toLowerCase();
  return attachment.mimeType === 'text/markdown' || name.endsWith('.md') || name.endsWith('.markdown');
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
