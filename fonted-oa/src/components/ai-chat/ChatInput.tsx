'use client';

import { useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Button, Input, Progress, Space, Tooltip, Typography, Upload } from 'antd';
import { LoadingOutlined } from '@ant-design/icons';
import { message } from '@/lib/antdMessage';
import type { ChatAttachment } from '@/types/chat';
import type { UploadProgressItem } from '@/store/aiChatStore';
import AttachmentPreview from './AttachmentPreview';
import { OaIcon } from '@/components/OaIcon';

const SUPPORTED_EXTENSIONS = new Set([
  '.jpg', '.jpeg', '.png', '.webp',
  '.pdf', '.doc', '.docx', '.xls', '.xlsx',
  '.txt', '.md', '.markdown', '.csv',
]);
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ACCEPT = Array.from(SUPPORTED_EXTENSIONS).join(',');

interface ChatInputProps {
  pending: ChatAttachment[];
  uploading: UploadProgressItem[];
  generating: boolean;
  onUpload: (files: File[]) => void;
  onRemoveAttachment: (id: number) => void;
  onSend: (content: string) => void;
  onStop: () => void;
}

export default function ChatInput({ pending, uploading, generating, onUpload, onRemoveAttachment, onSend, onStop }: ChatInputProps) {
  const { t } = useTranslation();
  const [value, setValue] = useState('');
  const [dragging, setDragging] = useState(false);
  const dragDepth = useRef(0);

  const send = () => {
    // 去除末尾换行并 trim，避免发送的文本末尾残留回车符
    const content = value.replace(/\n+$/, '').trim();
    if ((!content && !pending.length) || generating) return;
    onSend(content);
    setValue('');
  };

  const acceptFiles = (files: File[]) => {
    const valid = files.filter((file) => validateFile(file, t));
    if (valid.length) onUpload(valid);
  };

  return (
    <div
      className={`ai-composer ${dragging ? 'ai-composer-dragging' : ''}`}
      onDragEnter={(event) => { event.preventDefault(); dragDepth.current += 1; setDragging(true); }}
      onDragLeave={() => { dragDepth.current -= 1; if (dragDepth.current <= 0) setDragging(false); }}
      onDragOver={(event) => event.preventDefault()}
      onDrop={(event) => { event.preventDefault(); dragDepth.current = 0; setDragging(false); acceptFiles(Array.from(event.dataTransfer.files)); }}
    >
      {(pending.length > 0 || uploading.length > 0) && (
        <div className="ai-composer-attachments">
          {uploading.map((item) => (
            <div key={item.key} className="ai-attachment ai-attachment-uploading" role="status">
              <div className="ai-attachment-file-icon"><LoadingOutlined /></div>
              <div className="ai-attachment-meta">
                <Tooltip title={t('chat.uploading')}>
                  <Typography.Text ellipsis>{item.name}</Typography.Text>
                </Tooltip>
                <Progress percent={item.percent} size="small" showInfo={false} status="active" />
              </div>
              <span className="ai-attachment-percent">{item.percent}%</span>
            </div>
          ))}
          {pending.map((item) => <AttachmentPreview key={item.id} attachment={item} removable onRemove={() => onRemoveAttachment(item.id)} />)}
        </div>
      )}
      <Input.TextArea
        value={value}
        autoSize={{ minRows: 1, maxRows: 7 }}
        placeholder={t('chat.inputPlaceholder')}
        onChange={(event) => setValue(event.target.value)}
        onPaste={(event) => {
          const files = Array.from(event.clipboardData.files);
          if (files.length) { event.preventDefault(); acceptFiles(files); }
        }}
        onKeyDown={(event) => {
          if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send(); }
        }}
      />
      <div className="ai-composer-toolbar">
        <Space>
          <Upload accept={ACCEPT} multiple showUploadList={false} beforeUpload={(file, list) => {
            if (file.uid === list[0]?.uid) acceptFiles(list as File[]);
            return false;
          }}>
            <Tooltip title={t('chat.uploadFile')}>
              <Button
                type="text"
                icon={<OaIcon name="attachment" />}
                aria-label={t('chat.uploadFile')}
              />
            </Tooltip>
          </Upload>
          <span className="ai-composer-hint">{t('chat.inputHint')}</span>
        </Space>
        {generating ? (
          <Button danger icon={<OaIcon name="pause" />} onClick={onStop}>{t('chat.stopGenerating')}</Button>
        ) : (
          <Button type="primary" shape="circle" icon={<OaIcon name="send" />} disabled={!value.trim() && !pending.length} onClick={send} aria-label={t('chat.sendMessage')} />
        )}
      </div>
      {dragging && <div className="ai-drop-mask">{t('chat.dropToUpload')}</div>}
    </div>
  );
}

function validateFile(file: File, t: TFunction): boolean {
  const dotIndex = file.name.lastIndexOf('.');
  const extension = dotIndex >= 0 ? file.name.slice(dotIndex).toLowerCase() : '';
  const image = IMAGE_TYPES.has(file.type);
  if (!SUPPORTED_EXTENSIONS.has(extension) && !image) {
    message.error(t('chat.unsupportedFileType', { name: file.name }));
    return false;
  }
  const max = image ? 10 * 1024 * 1024 : 20 * 1024 * 1024;
  if (file.size > max) {
    message.error(t('chat.fileSizeExceeded', { name: file.name, limit: image ? '10MB' : '20MB' }));
    return false;
  }
  return true;
}
