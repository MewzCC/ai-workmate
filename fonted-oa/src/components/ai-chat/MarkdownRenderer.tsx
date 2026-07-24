'use client';

import { useState } from 'react';
import { Button, Tooltip, message as antMessage } from 'antd';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/cjs/styles/prism';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

interface CodeBlockProps {
  language: string;
  value: string;
}

export default function MarkdownRenderer({ content, className = '' }: MarkdownRendererProps) {
  return (
    <div className={`ai-markdown ${className}`.trim()}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a({ children, ...props }) {
            return <a {...props} target="_blank" rel="noreferrer">{children}</a>;
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
        {content}
      </ReactMarkdown>
    </div>
  );
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
