'use client';

import { Popover, Space, Typography } from 'antd';
import type { ChatMessageCitation } from '@/types/chat';

interface CitationListProps {
  citations: ChatMessageCitation[];
}

/**
 * AI 回复末尾的知识库引用列表。
 * 每条引用以「[序号] 来源文件名」展示，鼠标悬停可查看该引用片段的具体内容。
 */
export default function CitationList({ citations }: CitationListProps) {
  if (!citations.length) return null;
  return (
    <div className="ai-message-citations">
      <Typography.Text type="secondary" className="ai-message-citations-title">
        引用知识库
      </Typography.Text>
      <Space size={[8, 8]} wrap>
        {citations.map((citation, index) => (
          <Popover
            key={`${citation.docId}-${citation.chunkId}-${index}`}
            trigger="hover"
            placement="top"
            overlayClassName="ai-citation-popover"
            title={citation.source}
            content={
              <div className="ai-citation-content">
                <p className="ai-citation-text">{citation.text || '（无内容）'}</p>
                <Typography.Text type="secondary" className="ai-citation-meta">
                  相似度 {Math.min(100, Math.max(0, citation.score * 100)).toFixed(1)}%
                </Typography.Text>
              </div>
            }
          >
            <span className="ai-citation-item" tabIndex={0} role="button" aria-label={`查看引用内容：${citation.source}`}>
              [{index + 1}] {citation.source}
            </span>
          </Popover>
        ))}
      </Space>
    </div>
  );
}
