'use client';

import { useMemo, useState } from 'react';
import { Button, Popover, Tooltip, Typography } from 'antd';
import { message as antMessage } from '@/lib/antdMessage';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import type { ChatMessageCitation } from '@/types/chat';

interface MarkdownRendererProps {
  content: string;
  className?: string;
  /** AI 回复的知识库引用；用于把正文中的 [知识来源N] 渲染为可悬停查看的上标引用 */
  citations?: ChatMessageCitation[];
}

/** 匹配 LLM 按提示词生成的 [知识来源N] / [知识来源 N] 标注 */
const CITATION_LINK_RE = /\[知识来源\s*(\d+)\]/g;

function decorateCitations(content: string): string {
  return content.replace(CITATION_LINK_RE, (_match, index: string) => `[${index}](#citation-${index})`);
}

export default function MarkdownRenderer({ content, className = '', citations = [] }: MarkdownRendererProps) {
  const decorated = useMemo(() => decorateCitations(content), [content]);
  return (
    <div className={`ai-markdown ${className}`.trim()}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a({ href, children, ...props }) {
            const citationMatch = /^#citation-(\d+)$/.exec(href || '');
            if (citationMatch) {
              const label = `[${citationMatch[1]}]`;
              const citation = citations[Number(citationMatch[1]) - 1];
              if (!citation) {
                return <sup className="ai-inline-citation ai-inline-citation-muted">{label}</sup>;
              }
              return (
                <Popover
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
                  <sup
                    className="ai-inline-citation"
                    role="button"
                    tabIndex={0}
                    aria-label={`查看引用内容：${citation.source}`}
                  >
                    {label}
                  </sup>
                </Popover>
              );
            }
            return <a {...props} href={href} target="_blank" rel="noreferrer">{children}</a>;
          },
          pre({ children }) {
            return <>{children}</>;
          },
          code({ className: codeClassName, children, node: _node, ...props }) {
            const rawValue = String(children);
            const language = /language-([\w-]+)/.exec(codeClassName || '')?.[1] || '';
            const isBlock = Boolean(language) || rawValue.includes('\n');
            if (isBlock) {
              return <CodeBlock language={language} value={rawValue.replace(/\n$/, '')} />;
            }
            return <code className={codeClassName} {...props}>{children}</code>;
          },
        }}
      >
        {decorated}
      </ReactMarkdown>
    </div>
  );
}

interface CodeBlockProps {
  language: string;
  value: string;
}

function CodeBlock({ language, value }: CodeBlockProps) {
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      antMessage.error('代码复制失败');
    }
  };

  return (
    <section className="ai-code-block">
      <header className="ai-code-block-header">
        <span>{language || 'text'}</span>
        <Tooltip title={copied ? '已复制' : '复制代码'}>
          <Button
            type="text"
            size="small"
            aria-label="复制代码"
            icon={copied ? <CheckOutlined /> : <CopyOutlined />}
            onClick={() => void copy()}
          />
        </Tooltip>
      </header>
      <SyntaxHighlighter
        style={oneDark}
        language={language || 'text'}
        PreTag="div"
        customStyle={{ margin: 0, borderRadius: 0, background: '#17191f' }}
        codeTagProps={{ style: { fontFamily: 'Consolas, "SFMono-Regular", Menlo, monospace' } }}
        wrapLongLines={false}
      >
        {value}
      </SyntaxHighlighter>
    </section>
  );
}
