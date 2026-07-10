'use client';

import { useCallback } from 'react';
import { Moon, Sun } from 'lucide-react';

type SiteTheme = 'day' | 'night';

interface Props {
  theme: SiteTheme;
  onChange: (theme: SiteTheme) => void;
}

/**
 * 日夜模式切换按钮。
 *
 * 设计参考：
 *  - shadcn ThemeToggleButton：单按钮承载 sun/moon，旋转 + 缩放淡入淡出
 *  - Vercel 极简对比度：图标 1.4rem，命中区域 40×40
 *  - Linear / Geist 配色：过渡 280ms cubic-bezier(0.16, 1, 0.3, 1)
 */
export default function ThemeToggle({ theme, onChange }: Props) {
  const isNight = theme === 'night';

  const handleToggle = useCallback(() => {
    onChange(isNight ? 'day' : 'night');
  }, [isNight, onChange]);

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isNight}
      aria-label={isNight ? '切换到日间模式' : '切换到夜间模式'}
      onClick={handleToggle}
      className="wm-theme-toggle"
    >
      <span className="wm-theme-toggle-track" aria-hidden="true">
        <span className={`wm-theme-toggle-thumb ${isNight ? 'is-night' : 'is-day'}`}>
          <Sun className="wm-theme-icon wm-theme-icon-sun" aria-hidden="true" />
          <Moon className="wm-theme-icon wm-theme-icon-moon" aria-hidden="true" />
        </span>
      </span>
    </button>
  );
}
