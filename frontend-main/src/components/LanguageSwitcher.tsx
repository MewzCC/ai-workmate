import { useEffect, useRef, useState } from 'react';
import { Check, ChevronDown, Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocale } from '@/i18n/useLocale';
import type { AppLocale } from '@/i18n';
import { FlagIcon } from '@/components/CountryFlag';

interface Props {
  className?: string;
}

const OPTIONS: Array<{ locale: AppLocale; labelKey: 'language.zhCN' | 'language.enUS' }> = [
  { locale: 'zh-CN', labelKey: 'language.zhCN' },
  { locale: 'en-US', labelKey: 'language.enUS' },
];

/**
 * 营销官网语言切换下拉框：在 zh-CN / en-US 之间切换。
 * 按钮展示当前语言国旗，下拉菜单展示两种语言（国旗 + 名称），当前项高亮打勾。
 * 样式全部使用 --wm-* 主题变量，随日间/夜间主题自动适配。
 */
export default function LanguageSwitcher({ className }: Props) {
  const { t } = useTranslation();
  const { locale, changeLanguage } = useLocale();
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (e: PointerEvent) => {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const switchTo = (target: AppLocale) => {
    setOpen(false);
    if (target !== locale) changeLanguage(target);
  };

  return (
    <div className="wm-lang-wrap" ref={wrapRef}>
      <button
        type="button"
        className={`wm-lang-toggle ${open ? 'wm-lang-toggle-open' : ''} ${className ?? ''}`.trim()}
        onClick={() => setOpen(v => !v)}
        aria-label={t('language.label')}
        aria-haspopup="menu"
        aria-expanded={open}
        title={t('language.label')}
      >
        <Languages className="wm-lang-globe h-5 w-5" />
        <FlagIcon locale={locale} className="wm-lang-flag" />
        <span className="wm-lang-short">{locale === 'zh-CN' ? '中' : 'EN'}</span>
        <ChevronDown className={`wm-lang-chevron ${open ? 'wm-lang-chevron-open' : ''}`} />
      </button>

      {open && (
        <div className="wm-lang-menu" role="menu" aria-label={t('language.label')}>
          {OPTIONS.map(opt => {
            const active = opt.locale === locale;
            return (
              <button
                key={opt.locale}
                type="button"
                role="menuitem"
                className={`wm-lang-item ${active ? 'wm-lang-item-active' : ''}`}
                onClick={() => switchTo(opt.locale)}
              >
                <FlagIcon locale={opt.locale} className="wm-lang-flag" />
                <span>{t(opt.labelKey)}</span>
                {active && <Check className="wm-lang-check" aria-hidden="true" />}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
