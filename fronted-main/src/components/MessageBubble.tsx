'use client';

import ReactMarkdown from 'react-markdown';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import type { Message } from '@/types';

interface Props {
  message: Message;
  isStreaming?: boolean;
}

export default function MessageBubble({ message, isStreaming }: Props) {
  const isUser = message.role === 'user';

  return (
    <div className={`wm-msg ${isUser ? 'user' : 'assistant'}`}>
      {/* 头像 */}
      <div className="wm-avatar">{isUser ? '我' : 'AI'}</div>

      <div className="wm-msg-body">
        {/* 消息内容 */}
        <div className="wm-bubble">
          {isUser ? (
            <p className="whitespace-pre-wrap">{message.content}</p>
          ) : (
            <div className="markdown-body">
              <ReactMarkdown
                components={{
                  code({ node, className, children, ...props }) {
                    const match = /language-(\w+)/.exec(className || '');
                    const codeStr = String(children).replace(/\n$/, '');

                    if (match) {
                      return (
                        <div
                          className="my-2 rounded-xl overflow-hidden border"
                          style={{ borderColor: 'var(--wm-line)' }}
                        >
                          <div
                            className="flex items-center justify-between px-3 py-1.5 text-xs"
                            style={{ background: 'rgba(127,127,140,0.14)', color: 'var(--wm-muted)' }}
                          >
                            <span>{match[1]}</span>
                            <CopyButton text={codeStr} />
                          </div>
                          <SyntaxHighlighter
                            style={oneDark}
                            language={match[1]}
                            PreTag="div"
                            customStyle={{ margin: 0, borderRadius: 0, fontSize: '0.8rem' }}
                          >
                            {codeStr}
                          </SyntaxHighlighter>
                        </div>
                      );
                    }

                    return (
                      <code className="wm-inline-code" {...props}>
                        {children}
                      </code>
                    );
                  },
                }}
              >
                {message.content}
              </ReactMarkdown>
              {/* 流式输出光标 */}
              {isStreaming && <span className="wm-stream-caret" />}
            </div>
          )}
        </div>

        {/* 时间戳 */}
        <div className="wm-msg-time">
          {new Date(message.timestamp).toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
          })}
        </div>
      </div>
    </div>
  );
}

/**
 * 复制代码按钮
 */
function CopyButton({ text }: { text: string }) {
  const copy = () => {
    navigator.clipboard.writeText(text);
  };

  return (
    <button
      type="button"
      onClick={copy}
      className="hover:opacity-80 transition-opacity"
      title="复制代码"
    >
      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
      </svg>
    </button>
  );
}
