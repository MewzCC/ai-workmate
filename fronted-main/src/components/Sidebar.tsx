'use client';

import { MessageSquare, BookOpen, Bot, BarChart3, Plus, LogOut, Sparkles } from 'lucide-react';
import { useChatStore } from '@/store/chatStore';

interface Props {
  onLogout: () => void;
}

export default function Sidebar({ onLogout }: Props) {
  const clearMessages = useChatStore((s) => s.clearMessages);

  return (
    <aside className="wm-sidebar">
      {/* 品牌 */}
      <div className="wm-sidebar-brand">
        <span className="wm-mark"><Sparkles className="h-5 w-5" /></span>
        <div>
          <strong>AI WorkMate</strong>
          <span>企业 AI 工作入口</span>
        </div>
      </div>

      {/* 新建对话 */}
      <div className="wm-sidebar-new">
        <button type="button" onClick={clearMessages}>
          <Plus className="wm-si-icon" />
          新对话
        </button>
      </div>

      {/* 工作区菜单 */}
      <div className="wm-sidebar-section">工作区</div>
      <nav className="wm-sidebar-nav">
        <button type="button" className="wm-sidebar-item active">
          <MessageSquare className="wm-si-icon" />
          <span className="wm-si-label">AI 对话</span>
        </button>
        <button type="button" className="wm-sidebar-item" disabled>
          <BookOpen className="wm-si-icon" />
          <span className="wm-si-label">知识库</span>
          <span className="wm-si-badge">即将</span>
        </button>
        <button type="button" className="wm-sidebar-item" disabled>
          <Bot className="wm-si-icon" />
          <span className="wm-si-label">Agent</span>
          <span className="wm-si-badge">即将</span>
        </button>
        <button type="button" className="wm-sidebar-item" disabled>
          <BarChart3 className="wm-si-icon" />
          <span className="wm-si-label">用量统计</span>
          <span className="wm-si-badge">即将</span>
        </button>
      </nav>

      {/* 底部退出 */}
      <div className="wm-sidebar-foot">
        <button type="button" className="wm-sidebar-logout" onClick={onLogout}>
          <LogOut className="wm-si-icon" />
          退出登录
        </button>
      </div>
    </aside>
  );
}
