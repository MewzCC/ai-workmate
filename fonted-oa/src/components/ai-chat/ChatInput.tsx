'use client';

import { useRef, useState } from 'react';
import { Button, Input, Space, Tooltip, Upload, message } from 'antd';
import { ArrowUpOutlined, PaperClipOutlined, StopOutlined } from '@ant-design/icons';
import type { ChatAttachment } from '@/types/chat';
import AttachmentPreview from './AttachmentPreview';

const SUPPORTED_EXTENSIONS = new Set([
  '.jpg', '.jpeg', '.png', '.webp',
  '.pdf', '.doc', '.docx', '.xls', '.xlsx',
  '.txt', '.md', '.markdown', '.csv',
]);
const IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ACCEPT = Array.from(SUPPORTED_EXTENSIONS).join(',');

interface ChatInputProps {
  pending: ChatAttachment[];
  generating: boolean;
  onUpload: (files: File[]) => void;
  onRemoveAttachment: (id: number) => void;
  onSend: (content: string) => void;
  onStop: () => void;
}

export default function ChatInput({ pending, generating, onUpload, onRemoveAttachment, onSend, onStop }: ChatInputProps) {
  const [value, setValue] = useState('');
  const [dragging, setDragging] = useState(false);
  const dragDepth = useRef(0);

  const send = () => {
    if ((!value.trim() && !pending.length) || generating) return;
    onSend(value);
    setValue('');
  };

  const acceptFiles = (files: File[]) => {
    const valid = files.filter(validateFile);
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
      {pending.length > 0 && (
        <div className="ai-composer-attachments">
          {pending.map((item) => <AttachmentPreview key={item.id} attachment={item} removable onRemove={() => onRemoveAttachment(item.id)} />)}
        </div>
      )}
      <Input.TextArea
        value={value}
        autoSize={{ minRows: 1, maxRows: 7 }}
        placeholder="输入消息，或拖入图片和文档…"
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
            <Tooltip title="上传图片或文件">
              <Button
                type="text"
                icon={<PaperClipOutlined />}
                aria-label="上传图片或文件"
              />
            </Tooltip>
          </Upload>
          <span className="ai-composer-hint">Enter 发送 · Shift + Enter 换行</span>
        </Space>
        {generating ? (
          <Button danger icon={<StopOutlined />} onClick={onStop}>停止生成</Button>
        ) : (
          <Button type="primary" shape="circle" icon={<ArrowUpOutlined />} disabled={!value.trim() && !pending.length} onClick={send} aria-label="发送消息" />
        )}
      </div>
      {dragging && <div className="ai-drop-mask">松开以上传文件</div>}
    </div>
  );
}

function validateFile(file: File): boolean {
  const dotIndex = file.name.lastIndexOf('.');
  const extension = dotIndex >= 0 ? file.name.slice(dotIndex).toLowerCase() : '';
  const image = IMAGE_TYPES.has(file.type);
  if (!SUPPORTED_EXTENSIONS.has(extension) && !image) {
    message.error(`${file.name}：暂不支持该文件类型`);
    return false;
  }
  const max = image ? 10 * 1024 * 1024 : 20 * 1024 * 1024;
  if (file.size > max) {
    message.error(`${file.name} 超过${image ? ' 10MB' : ' 20MB'}限制`);
    return false;
  }
  return true;
}
