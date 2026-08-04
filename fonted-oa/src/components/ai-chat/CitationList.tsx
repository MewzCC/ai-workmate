'use client';

import { useTranslation } from 'react-i18next';
import { Popover, Space, Typography } from 'antd';
import type { ChatMessageCitation } from '@/types/chat';

export interface CitedItem {
  /** 引用原始序号（1-based，与正文 [知识来源N] 对应） */
  index: number;
  citation: ChatMessageCitation;
}

interface CitationListProps {
  citations: CitedItem[];
}

/** 提取正文中实际标注过的引用序号集合 */
export function extractCitedIndexes(content: string): Set<number> {
  const indexes = new Set<number>();
  for (const match of content.matchAll(/\[知识来源\s*(\d+)\]/g)) {
    indexes.add(Number(match[1]));
  }
  return indexes;
}

/**
 * AI 回复末尾的知识库引用列表。
 * 每条引用以「[序号] 来源文件名」展示，鼠标悬停可查看该引用片段的具体内容。
 */
export default function CitationList({ citations }: CitationListProps) {
  const { t } = useTranslation();
  if (!citations.length) return null;
  return (
    <div className="ai-message-citations">
      <Typography.Text type="secondary" className="ai-message-citations-title">
        {t('chat.citationsTitle')}
      </Typography.Text>
      <Space size={[8, 8]} wrap>
        {citations.map(({ index, citation }) => (
          <Popover
            key={`${citation.docId}-${citation.chunkId}-${index}`}
            trigger="hover"
            placement="top"
            overlayClassName="ai-citation-popover"
            title={citation.source}
            content={
              <div className="ai-citation-content">
                <p className="ai-citation-text">{citation.text || t('chat.noContent')}</p>
                <Typography.Text type="secondary" className="ai-citation-meta">
                  {t('chat.similarity', { score: Math.min(100, Math.max(0, citation.score * 100)).toFixed(1) })}
                </Typography.Text>
              </div>
            }
          >
            <span className="ai-citation-item" tabIndex={0} role="button" aria-label={t('chat.viewCitation', { source: citation.source })}>
              [{index}] {citation.source}
            </span>
          </Popover>
        ))}
      </Space>
    </div>
  );
}
