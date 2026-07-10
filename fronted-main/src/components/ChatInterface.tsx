'use client';

import { useState, useRef, useEffect } from 'react';
import { ArrowUp, Code2, Sparkles, Database, Terminal } from 'lucide-react';
import { useChatStore } from '@/store/chatStore';
import MessageBubble from './MessageBubble';

const SUGGESTIONS = [
  { icon: Code2, text: '帮我写一个 Spring Boot 接口' },
  { icon: Sparkles, text: '解释一下 RAG 是什么' },
  { icon: Database, text: '如何优化 SQL 查询性能？' },
  { icon: Terminal, text: '用 Python 写一个快速排序' },
];

export default function ChatInterface() {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const { messages, isLoading, streamingContent, error, sendMessage } = useChatStore();

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, streamingContent]);

  // 自动调整输入框高度
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
      inputRef.current.style.height = Math.min(inputRef.current.scrollHeight, 200) + 'px';
    }
  }, [input]);

  const handleSend = () => {
    if (!input.trim() || isLoading) return;
    sendMessage(input.trim());
    setInput('');
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="wm-chat-main">
      {/* 顶部标题栏 */}
      <header className="wm-chat-header">
        <div className="wm-chat-head-left">
          <span className="wm-chat-title">AI 对话</span>
          <span className="wm-chat-model">
            <i className="wm-chat-dot" />
            DeepSeek
          </span>
        </div>
        <div className="wm-chat-head-right">
          <span className="wm-chat-status">
            <i className={isLoading ? 'wm-is-busy' : ''} />
            {isLoading ? '回复中...' : '在线'}
          </span>
        </div>
      </header>

      {/* 消息列表 */}
      <div className="wm-chat-scroll">
        {messages.length === 0 && !isLoading && (
          <div className="wm-chat-empty">
            <div className="wm-chat-orb">
              <Sparkles className="h-8 w-8" />
            </div>
            <h2 className="wm-chat-hello">开始和 AI 对话吧</h2>
            <p>
              我是你的企业 AI 助手，可以回答问题、编写代码、分析文档。选择一个示例，或直接输入消息开始。
            </p>
            <div className="wm-chat-suggest">
              {SUGGESTIONS.map((s, i) => (
                <button
                  key={i}
                  type="button"
                  onClick={() => {
                    setInput(s.text);
                    inputRef.current?.focus();
                  }}
                >
                  <s.icon className="wm-si-icon" />
                  <span>{s.text}</span>
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="wm-chat-list">
          {messages.map((msg) => (
            <MessageBubble key={msg.id} message={msg} />
          ))}

          {/* 流式输出中的 AI 回复 */}
          {isLoading && streamingContent && (
            <MessageBubble
              message={{
                id: 'streaming',
                role: 'assistant',
                content: streamingContent,
                timestamp: Date.now(),
              }}
              isStreaming
            />
          )}

          {/* 加载中的打字指示器 */}
          {isLoading && !streamingContent && (
            <div className="wm-msg assistant">
              <div className="wm-avatar">AI</div>
              <div className="wm-msg-body">
                <div className="wm-typing">
                  <i />
                  <i />
                  <i />
                </div>
              </div>
            </div>
          )}

          {/* 错误提示 */}
          {error && <div className="wm-chat-error">{error}</div>}

          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* 输入框 */}
      <div className="wm-chat-input-wrap">
        <div className="wm-chat-input-bar">
          <textarea
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入消息，Shift+Enter 换行，Enter 发送"
            rows={1}
            disabled={isLoading}
            className="wm-chat-textarea"
          />
          <button
            type="button"
            onClick={handleSend}
            disabled={!input.trim() || isLoading}
            className="wm-chat-send"
            aria-label="发送"
          >
            {isLoading ? (
              <svg className="h-5 w-5 animate-spin" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
            ) : (
              <ArrowUp className="h-5 w-5" />
            )}
          </button>
        </div>
        <p className="wm-chat-foot">AI WorkMate 可能产生不准确信息，请核实重要内容</p>
      </div>
    </div>
  );
}
