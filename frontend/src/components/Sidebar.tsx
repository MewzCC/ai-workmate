'use client';

import { useChatStore } from '@/store/chatStore';

interface Props {
  onLogout: () => void;
}

export default function Sidebar({ onLogout }: Props) {
  const clearMessages = useChatStore((s) => s.clearMessages);

  return (
    <aside className="w-64 bg-gray-50 dark:bg-dark-800 border-r border-gray-200 dark:border-gray-700 flex flex-col">
      {/* 头部 */}
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex items-center gap-2">
          <div className="w-8 h-8 bg-primary-500 rounded-lg flex items-center justify-center">
            <svg className="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <span className="font-bold text-gray-900 dark:text-white">AI WorkMate</span>
        </div>
      </div>

      {/* 新建对话 */}
      <div className="p-3">
        <button
          onClick={clearMessages}
          className="w-full flex items-center gap-2 px-3 py-2.5 bg-primary-600 hover:bg-primary-700 text-white rounded-lg transition-colors text-sm font-medium"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          新对话
        </button>
      </div>

      {/* 功能菜单 */}
      <nav className="flex-1 px-3 space-y-1 overflow-y-auto">
        <NavItem icon="💬" label="AI 对话" active />
        <NavItem icon="📚" label="知识库" disabled />
        <NavItem icon="🤖" label="Agent" disabled />
        <NavItem icon="📊" label="用量统计" disabled />
      </nav>

      {/* 底部 */}
      <div className="p-3 border-t border-gray-200 dark:border-gray-700">
        <button
          onClick={onLogout}
          className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-dark-700 rounded-lg transition-colors"
        >
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          退出登录
        </button>
      </div>
    </aside>
  );
}

function NavItem({ icon, label, active = false, disabled = false }: {
  icon: string;
  label: string;
  active?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      disabled={disabled}
      className={`
        w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors
        ${active
          ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-700 dark:text-primary-300 font-medium'
          : 'text-gray-600 dark:text-gray-400 hover:bg-gray-200 dark:hover:bg-dark-700'
        }
        ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}
      `}
    >
      <span className="text-lg">{icon}</span>
      <span>{label}</span>
      {disabled && (
        <span className="ml-auto text-xs bg-gray-200 dark:bg-dark-600 px-1.5 py-0.5 rounded">即将</span>
      )}
    </button>
  );
}
