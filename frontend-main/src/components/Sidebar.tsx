import { MessageSquare, BookOpen, Bot, BarChart3, Plus, LogOut, Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useChatStore } from '@/store/chatStore';

interface Props {
  onLogout: () => void;
}

export default function Sidebar({ onLogout }: Props) {
  const { t } = useTranslation();
  const clearMessages = useChatStore((s) => s.clearMessages);

  return (
    <aside className="wm-sidebar">
      {/* 品牌 */}
      <div className="wm-sidebar-brand">
        <span className="wm-mark"><Sparkles className="h-5 w-5" /></span>
        <div>
          <strong>{t('brand.name')}</strong>
          <span>{t('brand.tagline')}</span>
        </div>
      </div>

      {/* 新建对话 */}
      <div className="wm-sidebar-new">
        <button type="button" onClick={clearMessages}>
          <Plus className="wm-si-icon" />
          {t('sidebar.newChat')}
        </button>
      </div>

      {/* 工作区菜单 */}
      <div className="wm-sidebar-section">{t('sidebar.workspace')}</div>
      <nav className="wm-sidebar-nav">
        <button type="button" className="wm-sidebar-item active">
          <MessageSquare className="wm-si-icon" />
          <span className="wm-si-label">{t('sidebar.items.chat')}</span>
        </button>
        <button type="button" className="wm-sidebar-item" disabled>
          <BookOpen className="wm-si-icon" />
          <span className="wm-si-label">{t('sidebar.items.knowledge')}</span>
          <span className="wm-si-badge">{t('sidebar.badgeSoon')}</span>
        </button>
        <button type="button" className="wm-sidebar-item" disabled>
          <Bot className="wm-si-icon" />
          <span className="wm-si-label">{t('sidebar.items.agent')}</span>
          <span className="wm-si-badge">{t('sidebar.badgeSoon')}</span>
        </button>
        <button type="button" className="wm-sidebar-item" disabled>
          <BarChart3 className="wm-si-icon" />
          <span className="wm-si-label">{t('sidebar.items.usage')}</span>
          <span className="wm-si-badge">{t('sidebar.badgeSoon')}</span>
        </button>
      </nav>

      {/* 底部退出 */}
      <div className="wm-sidebar-foot">
        <button type="button" className="wm-sidebar-logout" onClick={onLogout}>
          <LogOut className="wm-si-icon" />
          {t('sidebar.logout')}
        </button>
      </div>
    </aside>
  );
}
