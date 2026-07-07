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
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'} animate-fade-in`}>
      <div className={`flex gap-3 max-w-[85%] ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
        {/* 头像 */}
        <div className={`
          flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold
          ${isUser
            ? 'bg-primary-500 text-white'
            : 'bg-gradient-to-br from-purple-500 to-indigo-500 text-white'
          }
        `}>
          {isUser ? '我' : 'AI'}
        </div>

        {/* 消息内容 */}
        <div className={`
          relative px-4 py-3 rounded-2xl
          ${isUser
            ? 'bg-primary-600 text-white rounded-br-md'
            : 'bg-gray-100 dark:bg-dark-700 text-gray-900 dark:text-white rounded-bl-md'
          }
        `}>
          {isUser ? (
            <p className="text-sm leading-relaxed whitespace-pre-wrap">{message.content}</p>
          ) : (
            <div className="markdown-body text-sm">
              <ReactMarkdown
                components={{
                  code({ node, className, children, ...props }) {
                    const match = /language-(\w+)/.exec(className || '');
                    const codeStr = String(children).replace(/\n$/, '');

                    if (match) {
                      return (
                        <div className="my-2 rounded-lg overflow-hidden border border-gray-200 dark:border-gray-700">
                          <div className="flex items-center justify-between px-3 py-1.5 bg-gray-200 dark:bg-gray-800 text-xs text-gray-500">
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
                      <code className="bg-gray-200 dark:bg-dark-600 px-1 py-0.5 rounded text-sm" {...props}>
                        {children}
                      </code>
                    );
                  },
                }}
              >
                {message.content}
              </ReactMarkdown>
              {/* 流式输出光标 */}
              {isStreaming && (
                <span className="inline-block w-2 h-4 bg-gray-400 dark:bg-gray-300 ml-0.5 animate-pulse" />
              )}
            </div>
          )}

          {/* 时间戳 */}
          <div className={`text-xs mt-1 opacity-50 ${isUser ? 'text-right' : 'text-left'}`}>
            {new Date(message.timestamp).toLocaleTimeString('zh-CN', {
              hour: '2-digit',
              minute: '2-digit',
            })}
          </div>
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
      onClick={copy}
      className="hover:text-gray-300 transition-colors"
      title="复制代码"
    >
      <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
      </svg>
    </button>
  );
}
