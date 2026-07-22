'use client';

import { useEffect, useState } from 'react';
import { Button, Image, Spin, Typography } from 'antd';
import { CloseOutlined, FileTextOutlined } from '@ant-design/icons';
import { loadAttachmentContent } from '@/lib/chatApi';
import type { ChatAttachment } from '@/types/chat';

interface AttachmentPreviewProps {
  attachment: ChatAttachment;
  removable?: boolean;
  onRemove?: () => void;
}

export default function AttachmentPreview({ attachment, removable, onRemove }: AttachmentPreviewProps) {
  const [source, setSource] = useState(attachment.previewUrl || '');
  const [loading, setLoading] = useState(attachment.type === 'image' && !attachment.previewUrl);

  useEffect(() => {
    if (attachment.type !== 'image' || attachment.previewUrl) return;
    const controller = new AbortController();
    let objectUrl = '';
    loadAttachmentContent(attachment.id, controller.signal)
      .then((url) => { objectUrl = url; setSource(url); })
      .finally(() => setLoading(false));
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [attachment.id, attachment.previewUrl, attachment.type]);

  return (
    <div className={`ai-attachment ${attachment.type === 'image' ? 'ai-attachment-image' : ''}`}>
      {attachment.type === 'image' ? (
        <Spin spinning={loading} size="small">
          {source && <Image src={source} alt={attachment.name} preview width={88} height={64} />}
        </Spin>
      ) : (
        <span className="ai-attachment-file-icon"><FileTextOutlined /></span>
      )}
      <div className="ai-attachment-meta">
        <Typography.Text ellipsis title={attachment.name}>{attachment.name}</Typography.Text>
        <Typography.Text type="secondary">{formatBytes(attachment.size)} · {attachment.parsed ? '已解析' : '待解析'}</Typography.Text>
      </div>
      {removable && (
        <Button type="text" size="small" icon={<CloseOutlined />} aria-label={`移除 ${attachment.name}`} onClick={onRemove} />
      )}
    </div>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}
